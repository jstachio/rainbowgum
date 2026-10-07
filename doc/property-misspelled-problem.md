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

1. Ship the catalog (3): move `ConfigProcessor`'s property list to the class output and
   mark the hand written constants.
2. In simple props, alert for file keys under `logging.` that match no catalog entry,
   with case only and near miss suggestions (4) and the file and line.
3. Let the strict enum, or a new one, turn those alerts into failures for CI (5).

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
