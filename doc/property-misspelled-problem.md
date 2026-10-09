# Misspelled properties

Exploratory notes, not a design. Branch `explore/property-misspelled-problem`.

## The problem

A misspelled property is usually silently ignored:

```properties
logging.apenders=console
logging.encoder.console.keyvalues=logfmt
```

The first should be `appenders`, the second `keyValues` (keys are case sensitive). Neither
produces an error, an alert, or anything in debug mode. The application just logs with
the defaults and the user assumes the property does not work.

Why it is hard in Rainbow Gum:

1. **Properties are lookup only.** `LogProperties` answers `valueOrNull(key)`; it cannot
   say which keys are set. You cannot find a misspelled key without the set of keys that
   were given.
2. **The namespace is open.** Keys are parameterized (`logging.appender.{name}.output`),
   level keys take any logger name (`logging.level.com.myco`), and third party components
   add their own keys under the encoder, output, and publisher prefixes.
3. **Reads are lazy.** Some keys are read only when a component is created, and level
   keys are read per logger name when a logger is first used. A key that has not been
   read yet is not necessarily unused.
4. **There are many sources.** System properties, environment variables (`RAINBOWGUM_`
   names lose dots and case meaning), simple props files, URI queries, Spring's
   `Environment`, Avaje config.

## Key naming

Rainbow Gum keys follow Java naming, not the kebab case Micronaut and Spring Boot use:

- **Namespaces are all lowercase**, like package and module names:
  `logging.encoder.`, `logging.simpleprops.`, `logging.keyvalues.contributor.`. This is
  the prefix of a `@LogConfigurable` builder, so the processor can enforce it; every
  prefix and literal key today already complies. `{name}` segments are user data
  (`logging.appender.myAppender.output`) and are not covered.
- **Leaves are camel case**, like Java fields and methods: `keyValues`,
  `environmentVariables`.

Kebab case is lossy. `keyValues` converts to `key-values` exactly, but `key-values` could
mean `keyValues` or `keyvalues`, and kebab conventions allow dashes almost anywhere with
their own casing rules. So the Java form is the canonical one, and a kebab form can always
be derived from it, for example as an accepted alias or in IDE metadata, while the reverse
cannot be done reliably.

For misspellings this is useful: given a canonical camel case catalog, a kebab or all
lowercase spelling of a known key is a recognizable near miss (idea 4), not an unknown
key.

## Only leaves have case

The annotation processor now requires every `@LogConfigurable` prefix to start with
`logging.` and to be lowercase (Java package rules). So the only part of a generated key
whose case a user can get wrong is the leaf: `keyValues`, `keyValuesWhenEmpty`,
`maxFileSize`, `cleanHistoryOnStart`. That makes the leaf the likeliest source of a case
typo (`logging.encoder.console.keyvalues`), and it is worse for people coming from
Spring Boot or environment variables, where keys are effectively case insensitive.

The processor also now rejects two properties on one builder that differ only by case
(`Say` and `SAY`). So within a builder, a leaf lowercased is still unique, which is what
makes either option below unambiguous.

Two things are excluded:

- **Level keys.** `logging.level.com.MyCo` names a logger, and logger names are case
  sensitive data, not a leaf to normalize.
- **`{name}` segments.** `logging.appender.myAppender.output` has a user chosen name;
  only `output` is ours.

### Option A: property aliases as an annotation

Let a parameter declare extra accepted leaves, for example
`@LogConfigurable.Alias("keyvalues")`, generated as fallback lookups the same way kebab
case aliases were planned (the apt `.or()` fallback keys). It is explicit and documented
in the builder's property table, but someone has to remember to add it to every camel
case leaf, and each alias is another lookup.

### Option B: normalize every leaf

Accept any casing of a leaf for every generated builder, with no annotation. Sources that
can list their keys (simple props files, URI queries, maps) can normalize when loaded.
Lookup only sources (system properties, Spring's `Environment`) cannot be asked "any key
equal ignoring case", so the generated lookup would try a fixed set: the canonical leaf,
then all lowercase, then all uppercase. Three variants, not every possible casing.

### The worry: painful errors

If one property can be found under three spellings, its errors could become noisy:

```
Error for property. key: 'logging.encoder.console.keyValues' (or 'keyvalues' or 'KEYVALUES') ...
```

I think most of the pain can be avoided:

- **An invalid value** was found under exactly one spelling, so the error names that
  key only, the one the user actually wrote.
- **A missing required property** names only the canonical key. Users should be told
  the spelling we want, not every spelling we tolerate.
- **Two spellings set at once** (`keyValues=json` and `keyvalues=percent`) is the one
  truly confusing case. That should be its own error naming both keys and where each
  came from, rather than silently picking one.
- **A non canonical spelling that works** could raise a low priority alert ("found
  keyvalues, the documented key is keyValues") so typos get corrected without breaking
  anything. That overlaps with idea 4 and might be all that is needed.

That last point suggests a middle ground: do not accept other spellings at all, but
recognize them. A case only near miss is almost never a false positive, so the error or
alert can be specific and actionable without making three spellings legal. Accepting
spellings is hard to take back later; detecting them is not.

### Direction: fail on hinted misspellings, never accept them

Agreed with Adam: a known misspelling fails, it is never accepted as an alias. The
"alias" is a hint of how a key is likely to be misspelled, not a second legal spelling.

Checking every camel case leaf for every misspelling is too expensive for lookup only
sources. Most properties are optional with defaults, so a missing canonical key is the
normal case, not the exception: every unset property on every builder would pay one
extra lookup per variant (two for lowercase and uppercase) on every startup. So the
check is opt in, per property, on the leaves that are likely to be mistyped:

```java
@LogConfigurable.Misspelling({ "keyvalues", "KEYVALUES" }) @ConvertParameter("convertKeyValues") LogFormatter keyValues
```

The generated lookup checks the hinted keys only when the canonical key is missing,
so a correctly configured property costs nothing extra. If a hinted key is set, the
builder fails with the same kind of validation error as a bad value:

```
Validation failed for io.jstach.rainbowgum.format.TTLLFormatterBuilder:
Property key 'logging.encoder.console.keyvalues' from PROPERTIES_STRING[...] is a misspelling of 'logging.encoder.console.keyValues'.
```

Notes:

- **Name.** `Misspelling` (or `MisspelledAs`) says what it is; `Alias` would suggest the
  key is accepted.
- **Which leaves.** Leaves with an inner capital that people commonly lowercase or
  hyphenate: `keyValues`, `keyValuesWhenEmpty`, `maxFileSize`. A kebab form
  (`key-values`) is just another hint, which also covers people coming from Spring Boot.
- **The processor can help.** It can reject a hint equal to the canonical leaf, and it
  could default the hints (lowercase, kebab) when the annotation is present with no
  value, so most uses are just `@Misspelling`.
- **Enumerable sources need no hints.** Simple props files, URI queries, and maps can
  list their keys, so they can check every leaf for case only and near misses against
  the catalog (ideas 3 and 4) at no per lookup cost. The hints exist for the sources
  that can only be asked about one key at a time.
- **Environment variables** are already uppercase with no dots, so case hints do not
  apply to them.

## Who has the problem

Rainbow Gum has three kinds of users, and only one of them is ours to help here.

1. **Framework users.** Spring Boot users and other framework users configure logging
   through the framework. The framework owns their configuration and its tooling, so the
   help belongs there: Spring Boot's own approach is IDE metadata, not startup checks
   (idea 7).
2. **Advanced users.** They know Java and use the programmatic builders, where a
   misspelled key cannot exist. They are minimalists and do not want startup spent on
   checking they do not need (idea 9 is their answer).
3. **New and legacy users.** They use simple props: a `logging.properties` file,
   profiles, system properties, and environment variables. This is where the silent typo
   happens and where nothing else will catch it.

From loose observation so far, advanced users are the largest group today, then simple
props users. Framework users are expected to outgrow both once the framework
integrations are better known. That makes groups 2 and 3 the early adopters, the people
who will recommend Rainbow Gum or not, so both need a good experience now: group 2
must not pay for checking it does not need, and group 3 must not be left guessing why a
property did nothing.

Simple props is also the part we control most, which is why it already validates the
`logging.` prefix and sniffs `java.util.logging` files. Its checks run only for its
users, so nobody else pays for them.

## What others do

- **Log4j2** checks configuration *files*: an element or attribute no plugin accepts is
  reported through the status logger as "contains an invalid element or attribute"
  ([example](https://github.com/apache/logging-log4j2/issues/1386),
  [LOG4J2-3321](https://issues.apache.org/jira/browse/LOG4J2-3321)). It works because each
  plugin's builder declares its attributes.
- **Quarkus** builds a catalog of every known key from its build time config roots and
  warns at startup for an unknown key under `quarkus.`: "Unrecognized configuration key
  ... was provided; it will be ignored; verify that the dependency extension for this
  configuration is set or that you did not make a typo". Its issue tracker is also full
  of false positives: keys set by other tooling or read in ways the catalog missed
  ([#21396](https://github.com/quarkusio/quarkus/issues/21396),
  [#32591](https://github.com/quarkusio/quarkus/issues/32591),
  [#41577](https://github.com/quarkusio/quarkus/issues/41577)). Lesson: warn, do not fail,
  and expect the catalog to be incomplete.
- **Spring Boot** generates configuration metadata JSON with an annotation processor, so
  IDEs flag unknown keys while editing; startup does not complain. The separate
  properties migrator reports renamed and removed keys at startup.

## IDE metadata

Several frameworks generate configuration metadata with an annotation processor, the
same way Rainbow Gum generates builders. There is no shared standard:

- **Spring Boot** writes `META-INF/spring-configuration-metadata.json` from
  `@ConfigurationProperties`
  ([annotation processor](https://docs.spring.io/spring-boot/specification/configuration-metadata/annotation-processor.html)).
  This is the closest thing to a de facto format: IntelliJ IDEA Ultimate and the Spring
  tools for VS Code read it for completion and unknown key warnings in
  `application.properties` and `application.yml`.
- **Micronaut** builds the same kind of metadata from its own `@ConfigurationProperties`,
  and its `ConfigurationMetadataBuilder` is documented as producing data that can be
  written to a format IDEs read, "like spring-configuration-metadata.json"
  ([API](https://docs.micronaut.io/4.0.x/api/io/micronaut/inject/configuration/ConfigurationMetadataBuilder.html)).
  Micronaut 5 can also generate JSON Schema documents from `@ConfigurationProperties`
  for IDE completion and validation.
- **Helidon** is the closest to how Rainbow Gum works: builders configured from config.
  Its `helidon-config-metadata-processor` reads `@Configured` on a builder and
  `@ConfiguredOption` on its methods and writes its own format,
  `META-INF/helidon/config-metadata.json`, with each key's type, default, and
  description (taken from the javadoc), linking nested configured types
  ([wiki](https://github.com/helidon-io/helidon/wiki/Generated-config-documentation)).
  Its documented use is generating the reference documentation; Helidon 4's builder
  codegen took over this role. Helidon had an IntelliJ plugin with config key
  completion, but JetBrains now lists it as
  [no longer supported](https://www.jetbrains.com/help/idea/helidon.html), and I could
  not confirm it read this file.

What this means for Rainbow Gum:

- `ConfigProcessor` already has everything Helidon's processor collects (key, type,
  default, javadoc description) for every `@LogConfigurable` builder; it renders them
  into the builders' javadoc today. Writing them to a shipped resource (idea 3) is a
  small step, and the same data could feed the overview's property tables.
- IDE metadata only helps where an IDE knows the file. Spring's metadata applies to a
  Spring Boot project's `application.properties`, not to a simple props
  `logging.properties`, so it helps framework users (idea 7) but not simple props users,
  who still need the runtime check.
- If one format is picked for IDEs, Spring's is the one tools already read; a Rainbow
  Gum specific format (like Helidon's) is simpler to own but only useful to our own
  tools. JSON Schema is neutral but fits YAML and JSON files better than `.properties`.

## What Rainbow Gum already has

- **One lookup path.** Every typed lookup goes through `LogProperty` (`forKey(...)` then
  `properties.visit`), so recording what was read is one place. A few direct
  `valueOrNull` calls bypass it: the JAnsi configurator, the pattern module's property
  function, the Spring modules' pattern system properties, and the reporter itself.
- **A partial catalog.** `ConfigProcessor` can already write every generated builder's
  keys (`-Aio.jstach.rainbowgum.apt.propertyList`, the `property-list` profile), but only
  to `SOURCE_OUTPUT` as a test coverage audit, so it is never shipped.
- **About 60 hand written `*_PROPERTY` and `*_PREFIX` constants** not covered by any
  generated builder.
- **A precedent for shipping metadata.** `EnumAliasProcessor` writes
  `META-INF/rainbowgum/enum-aliases.txt` to the class output and a test reads it back.
- **Narrow checks.** Simple props validates the `logging.` prefix and sniffs
  `java.util.logging` files; enum *values* already fail with a list of valid values.

## Ideas

### 1. Track the keys that were read

Record every fully qualified key `LogProperty` resolves (found or not) and which source
answered. Debug mode then reports the keys used and where each came from. Level keys are
left out since any logger name is valid.

On its own this flags nothing, but it answers "did my property do anything?" and it is
the input for ideas 4 and 5. Cost is a concurrent set filled at startup. Because reads are
lazy, the report should be made after start and say so.

### 2. Namespace owners know their complete set

A generated builder under a prefix, for example the `ttll` encoder under
`logging.encoder.console.`, knows every key it accepts. Any other key set under that
prefix is suspicious:

```
logging.encoder.console.keyvalues is not a property of the ttll encoder (did you mean keyValues?)
```

This is local and precise: only the component that owns the prefix decides. It needs the
set keys under a prefix (idea 6).

### 3. A shipped catalog of known keys

Like `@EnumAlias`, let the processor write the catalog to the class output so it ships:

- Generated builders already know their keys (`{name}` parameters become wildcards).
- Hand written constants need marking, for example a `@LogPropertyKey` annotation on the
  `*_PROPERTY` and `*_PREFIX` constants, documented like `@EnumAlias`.
- At startup, load every `META-INF/rainbowgum/properties.txt`, and warn for a set key
  under `logging.` that matches nothing.

Third party components that do not use the processor make false positives certain
(the Quarkus lesson), so this must be an alert, never a failure by default.

### 4. Near miss suggestions (my addition)

Most typos are close to a real key. Compare unknown keys against the catalog or the keys
read (idea 1):

- **Case only** (`keyvalues` against `keyValues`) is the likeliest mistake and almost
  never a false positive, so it can be a stronger alert than a generic unknown key.
- **Small edit distance** (`apenders` against `appenders`) gives the "did you mean".
- Once kebab case keys land (todo), compare the normalized forms so `key-values`,
  `keyValues`, and `keyvalues` are the same key.

### 5. Escalate in tests and CI, not production (my addition)

An enum property, for example `logging.properties.unknown=ignore|alert|fail` with
`alert` the default, lets CI fail on a typo while production only alerts. It would carry
`@EnumAlias` like the other enums.

### 6. Key enumeration as an optional capability (my addition, prerequisite)

None of ideas 2 to 5 work without the keys that were set. Most sources can list them:
system properties, environment variables, simple props, URI queries, mutable maps, and
Spring's enumerable property sources. Add an optional way to ask a `LogProperties` for
its keys (all keys, or under a prefix); sources that cannot answer are skipped, not
guessed.

Environment variables need care: `RAINBOWGUM_` names lose dots, so map catalog keys
forward to environment names and compare those, instead of trying to reverse the
mapping.

### 7. IDE metadata for Spring Boot (my addition)

Spring Boot users edit `application.properties`, where IDEs read Spring configuration
metadata. The Spring modules could ship `META-INF/spring-configuration-metadata.json`
for Rainbow Gum's own `logging.` keys, built from the same catalog as idea 3, so typos are
flagged while editing at no runtime cost. See "IDE metadata" above for why Spring's
format rather than one of our own.

### 8. Misspelled names, not keys (my addition, to check)

Some typos are in values that name things: `logging.appenders=consol` refers to an
appender with no configuration. Check what happens today; a name that no
`logging.appender.{name}.` key and no built in default explains is as silent as a
misspelled key.

### 9. Do nothing

Keep properties as they are and point users who want certainty at the programmatic
builders. In Java a misspelled key does not exist: `new TTLLFormatterBuilder("console")
.keyValues(...)` fails to compile if misspelled, the IDE completes it, and values are
typed. Properties then stay the quick, forgiving path for simple setups, and anyone who
needs checked configuration has it already. The cost is that most users start with
properties, which is exactly where the silent typo happens, and debug mode would still
not explain why a property had no effect.

### 10. Used, missed, and unused keys (Adam's direction)

No annotations and no catalog. A small decorator on `LogProperties` records every key
passed to `valueOrNull` and whether any source answered. Every typed lookup ends there
(the `listOrNull`, `mapOrNull`, and `visit` defaults build on it), so this also sees the
few reads that bypass `LogProperty`. That splits keys into three sets:

- **Used:** read and found.
- **Missed:** read but not set anywhere. Mostly optional properties left at their
  defaults, which is normal.
- **Unused:** set in a source that can list its keys, but never read.

Simple props can list every key in its files, so for simple props users the unused set
is exact. A misspelling is an unused key that is close to a missed key:

```
logging.encoder.console.keyvalues (logging.properties:7) is set but never read.
Did you mean logging.encoder.console.keyValues?
```

Matching only unused against missed keeps it cheap and precise: both sets are small, and
the "did you mean" candidate is a key some component actually asked for, so it is never
a stale or third party guess. In order:

1. **Same key ignoring case and `-`/`_`:** `keyvalues`, `KEYVALUES`, `key-values` all
   match `keyValues`. Almost never a false positive.
2. **Small edit distance** (Levenshtein, for example at most 2, compared within the same
   prefix): `apenders` against `appenders`.
3. **Otherwise** just "set but never read", which also catches keys for a component
   that is not on the classpath.

Why this is better than the per property `@Misspelling` hints above:

- **Nothing to maintain.** Every builder, generated or hand written, third party or ours,
  is covered by being read.
- **No lookup cost.** Recording a key is a set insert per lookup; nothing is looked up
  twice. The comparison runs once, after start.
- **No catalog drift.** The missed set is what the running configuration actually asked
  for, so a misspelling of a key from a component that is not installed is not
  "corrected" to it.

Things to settle:

- **When to decide.** Reads are lazy, so the check runs after `RainbowGum` starts, when
  every configured component has been built. A key read later (a component created on
  demand) could be reported unused at start; those should be rare and the message says
  "never read during startup".
- **Level keys.** `logging.level.*` keys are read per logger name as loggers are created,
  so they are excluded from unused. A level key whose logger never appears is a separate
  question (see open questions).
- **Fail or alert.** This is where simple props' strict enum fits: alert by default,
  fail for CI (idea 5). A case only match is safe to fail on.
- **Scope.** Only keys under `logging.` from simple props files (base and profiles), so
  the decorator and the comparison run only for simple props users; advanced and
  framework users pay nothing. The decorator itself is general, so idea 1 (debug mode
  reporting of used keys) falls out of it.

This leaves the `@Misspelling` hints only for sources that cannot list their keys
(system properties, Spring's `Environment`), which these notes do not focus on, so they
are probably unnecessary.

#### Spike result

Branch `spike/unused-property-check` implements this in simple props. Against a real
file and a real started `RainbowGum` it reported `keyvalues`, `KEYVALUES`, and
`key-values` as `keyValues`, `therad` as `thread`, `levle` as `level`, and a made up key
with no suggestion. It did not flag any valid key or the level key. Reads did happen by
the end of start: the only lazy reads are the level keys, which are excluded anyway.
The check runs from `RainbowGum.onGlobalChange` right after the global `RainbowGum`
starts and sends each message as a warning alert.

#### System properties: the discovery case

Playing with the spike from the command line changed the priority. The common story is:
you want something turned on, you pass `-Dlogging...` and nothing happens, so you rerun
with `-Dlogging.debug=true` to find out why. That is discovery, and an unused key report
with a suggestion answers it directly:

```
java -Dlogging.encoder.console.levle=plain -jar app.jar
[WARN] ... Property key 'logging.encoder.console.levle' from SYSTEM_PROPERTIES[logging.encoder.console.levle] was set but not read during startup. Did you mean 'logging.encoder.console.level'?
```

So system properties are now checked too (the spike does this), and they may deserve the
most stringent check of all:

- **They can be listed.** `System.getProperties()` gives every name, like a simple props
  file and unlike environment variables (whose names lose the dots).
- **Only `logging.` keys**, the same prefix rule as the files.
- **Intent is explicit.** A `-D` was typed for this run, on purpose. A dormant group in a
  file may be switched off deliberately; a `-D` nothing read almost never is. Profiles
  and `logging.properties` have reasons to carry keys that are not read (the profile
  encoder switch above, dormant groups); a system property does not.
- **Suggest from every key read, not only missed ones.** A `-D` usually overrides a key
  already set in a file, so the key it was meant to be was *found*, not missed. The spike
  first suggested only missed keys and gave `levle` no suggestion for exactly this
  reason; it now suggests from every key read, which is still only keys some component
  asked for.

Two lists keep it honest:

- **Read before or outside `LogConfig`**, so the recorder never sees them:
  `logging.debug`, `logging.global.queue.level`, `logging.global.queue.error`,
  `logging.systemlogger.initialize`.
- **Other libraries' system properties under `logging.`** (a whitelist that will need
  to grow): Spring Boot's `logging.config` and JBoss LogManager's
  `logging.configuration`.

With `-Dlogging.debug=true` already the tool people reach for, alerts may be all that is
needed for now; failing (below) can wait. If anything does fail one day, unread system
properties with a suggestion are the strongest candidate.

#### Failing is the hard part now

Alerting is easy and done. Failing is not, because the check can only run after start:

- **Everything is already running.** Outputs are open, publisher threads started, and
  the router is live. A failure has to close that `RainbowGum` cleanly, not just throw.
- **It happens on someone else's call.** The global `RainbowGum` starts lazily, usually
  inside the application's first `LoggerFactory.getLogger` or log call. Throwing there
  fails a random line of application code, possibly in a static initializer, with an
  error about a properties file.
- **Before start is too early.** Components read most of their keys while being built
  and started, so a check before start would report almost everything as unused.

Options:

1. **A core hook between "configured" and "started"**, if most reads happen while the
   router and components are created rather than when they start. Failing there leaves
   nothing running. Needs measuring which reads happen in which phase.
2. **Close then throw**: close the just started `RainbowGum` and throw from global
   initialization, the same place strict `fail` already throws for prefix errors today
   (`SimpleProperties.properties()` during property loading). Brutal, but it is the same
   kind of failure users of strict `fail` already opted into.
3. **Fail only where failing is cheap**: CI and tests. A test helper or a build plugin
   that starts Rainbow Gum with the application's `logging.properties` and fails the
   build, while production only alerts (idea 5).

#### Where fail lives: `logging.debug=help` (agreed direction)

Agreed with Adam: the fail switch belongs on `logging.debug`, as a new value rather than
a simple props enum. Working name `help`, since it behaves almost like `--help` for
configuration:

- **Everything `all` does**: dump alerts and print the `LogReporter` report.
- **Then fail** on unread keys with a suggestion, closing the just started `RainbowGum`
  and throwing from global initialization (option 2 above), so the run stops with the
  report and the reasons in front of you.
- **Print javadoc links** for the components that were configured (the builders, so
  their property tables are one click away). This is the parked validation doc URL idea
  (branch `feature/validation-doc-url`): in `help` mode the links are wanted, so the open
  questions there (snapshot URLs, third party builders) matter less.

**Documented as troubleshooting.** The overview gets a "Troubleshooting" section built
around `-Dlogging.debug=help`: run with it, read the suggestions and links, and if it is
still unclear, paste the printed report into a bug report. That makes the report part
of the support flow, so it should be complete and safe to paste: versions, modules,
sources and which one answered each key, and no secret values (keys are fine, values
from sources like environment variables may need masking).

Why `logging.debug`:

- **It is already the discovery switch.** People reach for it when something does not
  work, which is when stopping is welcome.
- **It is set on purpose, per run or in CI**, never left in a shipped
  `logging.properties`, so failing never surprises production.
- **It is read from system properties before `LogConfig` exists**, so it works with or
  without simple props and can cover more checks later.
- **The existing values stay print only.** Turning on diagnostics must not change
  behavior by itself, which is why failing is a new, explicitly named value and not part
  of `all`.

This replaces the separate `logging.simpleprops.unused` enum below: simple props always
alerts, and `help` is the one way to fail.

**Later, not yet: other configuration sources.** The misspelling check could extend
beyond simple props (Spring Boot, Avaje, Micronaut, Helidon users), but only for
`logging.` system properties there, since those can always be listed and a `-D` is
always deliberate. The framework's own configuration stays the framework's business
(and its IDE metadata).

#### Its own enum, with predictable failure rules

Decided with Adam: unused keys get their own enum rather than sharing
`logging.simpleprops.strict`, because prefix checks fail while properties load and unused
key checks can only fail after start. For example `logging.simpleprops.unused`:

| Mode | Behavior |
|---|---|
| `off` | not checked, and no recorder (zero cost) |
| `alert` | every unused key is an alert |
| `fail` | the keys below fail; other unused keys alert |

What `fail` fails on should be predictable from the key alone, not from how clever the
matching was:

1. **Any unused key with a suggestion.** A case only or near miss match of a key some
   component asked for is almost certainly a typo.
2. **Any unused key under a component namespace**, suggestion or not:
   `logging.appender.{name}.`, `logging.encoder.{name}.`, `logging.output.{name}.`,
   `logging.publisher.{name}.`, `logging.route.{name}.`. Every component under these is
   created at start and reads its keys then, third party ones included, so a key there
   that nothing read is wrong: `logging.encoder.console.colour` fails even though no
   suggestion is close enough.
3. **Everything else alerts**: an unknown key like `logging.something` with no
   suggestion may belong to a module that reads it later or is not installed.

Exceptions to rule 2:

- **Level keys inside a namespace**, `logging.route.{name}.level.*`, are read lazily per
  logger like `logging.level.*` and are ignored.
- **A component that was not created at all.** This is a feature, not a mistake: the
  overview's [plural keys](https://jstach.io/rainbowgum/#config_plural) section promises
  that a group left out of `logging.appenders` (its `appender3`) "will not be used and
  will not produce a configuration error if incorrect", so groups can be switched on
  and off. See "Dormant groups" below.

#### Found by the simple props example: keys for a component type not chosen

Running `examples/simple-props` against the spike with `-Dlogging.profiles=json`
reported two keys as unused:

```
Property key 'logging.encoder.console.level' from SIMPLE_PROPS[classpath:/logging.properties:15][...] was set but not read during startup.
Property key 'logging.encoder.console.logger' from SIMPLE_PROPS[classpath:/logging.properties:16][...] was set but not read during startup.
```

Both are correct TTLL options in the base file. The `json` profile switched the
console encoder to `json5`, which does not read them. This is the normal way to use
profiles, and it breaks rule 2 above: the keys are under `logging.encoder.{name}.` with
no suggestion, so `fail` would fail a perfectly valid setup.

So rule 2 cannot be "any unread key under a component namespace". A key can be unread
because the component under that name is a different type than the one the key was
written for. Options:

- **Only fail with a suggestion**, even under component namespaces (rule 1 only). The
  json profile case then alerts at most, and `colour` against `color` still fails.
- **Know which keys each type accepts** (the catalog, idea 3): unread but valid for some
  other encoder type is "configured for a different type", not a mistake.
- **Do not alert at all** for keys under a namespace when they would be read by another
  type, which also needs the catalog.

The first is simplest and keeps `fail` predictable, at the cost of not catching a key
under a component namespace that is wrong for every type.

#### Dormant groups: possibly the biggest problem

Because of plural keys, "no key under this `{name}` was read" is normal, and it looks
exactly the same whether the group was disabled on purpose or its name was mistyped:

```properties
logging.appenders=console
logging.appender.consle.encoder=json   # meant console: silently ignored
logging.appender.file.output=file:///var/log/app.log   # disabled on purpose
```

`console` starts on its defaults and both other groups are dormant. Note the opposite
mistake is already caught today: `logging.appenders=consol` fails, because a named
appender has no default output unless it is `console` or `file`.

The same matching helps here, applied to the `{name}` segment instead of the leaf:

- **Dormant name close to a created name** in the same namespace (`consle` against the
  created `console`) is a misspelling, with a suggestion, so it follows rule 1 and fails
  under `fail`.
- **Dormant name close to nothing** (`file` when only `console` was created, or
  `appender3`) is a disabled group. It is not an error; at most an info alert listing
  the dormant groups, so a user can see what is switched off.

The same applies to every plural driven namespace: `logging.routes` and route names,
appender names (which also name their encoder and output keys), and publishers.

For simple props users this is less of a problem than it sounds: profiles (including the
optional `default` profile) are the better tool for switching groups on and off, since a
group kept in `logging-file.properties` is simply not loaded unless the `file` profile
is selected. So dormant groups in the files that are loaded should be rare, and the docs
can point people at profiles instead of dormant groups.

Open: whether `fail` is ever the default. The notes so far say alert by default, `fail`
for CI.

## Thoughts on focusing on simple props users

Aiming at the simple props user changes what is hard:

- **Enumeration is already solved there.** `SimpleLogProperties` reads every entry of
  the file with its line number, so idea 6 is not needed in core to check the files.
  Messages can point at the line, like the `java.util.logging` sniffing does:
  `SIMPLE_PROPS[logging.properties:7][logging.encoder.console.keyvalues]`.
- **The files are the place to check.** The same user may also set system properties
  and environment variables, but the file is where most of the configuration and most
  of the typos are. Checking only the files simple props loads (base and profiles) keeps
  the scope clear.
- **Only simple props loads the catalog.** With a shipped catalog (idea 3), simple props
  can read the `META-INF/rainbowgum/properties.txt` resources at startup and compare the
  file's keys. Core stays unchanged at runtime, so advanced and framework users pay
  nothing; the cost is compile time in the annotation processor.
- **It fits the existing switch.** Simple props already has a key checking enum,
  `logging.simpleprops.strict`. The default `short` mode could add an alert for keys that
  match nothing, with a near miss suggestion (idea 4), while `fail` fails on them for CI
  (idea 5). Or the unknown key check gets its own enum if mixing it with the prefix modes
  is confusing.
- **The catalog only has to be good enough for files.** False positives from third party
  components (the Quarkus lesson) are still possible, but an alert naming the file and
  line, which `off` silences, is a small cost for a user who wrote the file by hand.

- **Advanced users stay at zero cost.** They are the largest group today, so nothing in
  this plan runs for them: the catalog is read only by simple props, and tracking keys
  that were read (idea 1), if done at all, runs only in debug mode, where a user has
  asked for diagnostics.

## A possible order

For the simple props user, which these notes suggest focusing on:

1. Add the recording decorator on `LogProperties` (10), installed only by simple props.
2. After start, report simple props file keys under `logging.` that were never read,
   matched against missed keys: case only first, then near misses (4), with the file and
   line.
3. Let the strict enum, or a new one, turn those alerts into failures for CI (5).

The shipped catalog (3) is no longer needed for this; it stays useful for IDE metadata
(7) and documentation.

The broader version, for every source and every user:


1. Key enumeration (6), since everything else needs it.
2. Track reads and show them in debug mode (1). No false positives because it only
   reports.
3. Case only and near miss checks against the keys read (4). High signal without a
   catalog.
4. The shipped catalog (3), with prefix owners (2) as the precise case, warning by
   default and `fail` available for CI (5).

## Open questions

- Should an unknown key ever fail by default? These notes say no.
- How are keys of third party components without the processor treated: unknown,
  or ignored under prefixes they register at runtime?
- When is "unused" decided, given lazy reads? After start, or at shutdown in the report?
- Do level keys get anything, for example a warning for a level key whose logger name
  never appears among loggers created (now that logger names are tracked)?

Researched and written by Claude Opus 5.5.
