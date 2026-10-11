# Black-box QA for 0.15.0 (Claude, main at 20592241)

Six QA agents tested Rainbow Gum from its documentation only, the way a user
would: they read the rendered overview and Javadoc, built small Maven
applications against the installed `1.0.0-SNAPSHOT` jars, and ran them with
`java`. They never saw the source code.

- **Tested commit:** `20592241` on `main`.
- **Agents:** Claude Sonnet 5.5, one headless `claude -p` session per doc area,
  run 2026-10-11. Triage and reproduction by Claude Opus 5.5.
- **Isolation:** each session ran in its own directory under `/tmp/rg-qa/` with
  permission deny rules on the repository, `-sources.jar` files, `javap`,
  GitHub and web access. Every tool call was logged, and an audit of all six
  logs found no access to the source.
- **Areas:** core config, publishers/appenders/outputs, encoders/formatters,
  alerts/troubleshooting, simple props/facades, framework integrations.

The triage below is the summary. The agents' own reports, with exact
reproduction steps, follow it unedited. Paths in them such as `app/run.sh`
refer to the agents' scratch projects, which are not in this repository.

## Code bugs (reproduced or confirmed in code)

1. **Spring Boot level handling is unimplemented.** `setLogLevel` and
   `getLoggerConfiguration(s)` in both Boot modules call the Spring base class,
   which throws `UnsupportedOperationException`.
   - Every startup prints `ERROR ... Cannot set level 'DEBUG' for 'demo'` for
     each `logging.level.*` key, although the levels do apply.
   - `/actuator/loggers` returns HTTP 500.
2. **JFR with `-XX:StartFlightRecording` and `jfr:///` crashes the
   application** with `LinkageError: duplicate class definition for
   io.jstach.rainbowgum.jfr.RainbowGumLogEvent$InfoEvent`. Reproduced on JDK
   21, 25 and 27. This is the documented way to start a recording.
3. **The level file does not apply edits at runtime.** Watching the file
   supplies `logging.global.change=true`, but each logger also needs
   `logging.change=level`. The level file tests checked the level resolver
   rather than existing loggers, so they missed it. Fix: also supply
   `logging.change=level` as a lowest precedence default.
4. **Pattern encoder prints a keyword after a group literally.** A `%keyword`
   directly after `%group(...)` without `{options}` is output as text:
   `%red(%msg)%n` puts every event on one line. Logback configs commonly use
   `%highlight(%-5level)%n`.
5. **A route built in code with only per-logger levels defaults other loggers
   to TRACE.** The INFO fallback in `LogRouter` only applies when the route has
   no level config at all.
6. **Invalid booleans are silently `false`.** `file.append=yes` truncates the
   log file; `ecs.structured=yes` gives the flat layout.
7. **Duplicate JSON keys.** Logstash, ECS, GELF and logfmt write a second
   `level`, `message` or `@timestamp` when an MDC key has that name. JSON5 and
   the Logback encoder already avoid this.
8. **`SYSTEM_PROPERTIES` is listed twice** in the report and in "Tried:" when
   simple props loads a `logging.properties`. Cosmetic; cause not located yet.

## Reported, not yet reproduced

- The JFR alert listener (`logging.jfr.alerts.enabled=true`) records no events.
- Typos in URI query parameters (`?maxFileSiz=`) are never reported, even with
  `logging.debug=help`.
- The disruptor's "bufferSize must be a power of 2" error does not name the
  property or value.
- Commons Logging 1.3.0 to 1.3.5 logs `error(msg, throwable)` at DEBUG through
  SLF4J; 1.3.6 is fine. Probably an upstream bug, but Spring 7 ships 1.3.5 and
  the docs say no action is needed.

## Decisions (behavior, not bugs)

- **Level key typos get no suggestion.** `logging.levle.*` and
  `logging.levels.*` are never warnings because of the level key exemption.
  Near misses of `logging.level`, `logging.route` and `logging.change` could be.
- **No hint when a typo breaks startup.** A misspelled required key
  (`pattern.patern`) fails with "Property missing" and no "did you mean",
  because unread keys are only checked after a successful start.
- **Unknown schemes.** The error does not list registered schemes or suggest a
  close one.
- **Failure presentation.** A bad config prints a long stack trace and leaves
  the whole application without logging. Defensible, but not documented.

## Doc fixes

- **The `rainbowgum` dependency does not include pattern, json or file.** Its
  pom leaves them out deliberately, but the docs say it pulls in the commonly
  used modules, so the first Getting Started example fails. Say which module
  each scheme needs.
- **Getting Started builder example** configures no output and crashes; it also
  lacks imports and the service file registration.
- **`logging.level.root` / `RAINBOWGUM_level_root` does nothing.** The root key
  is `logging.level`.
- **Snippets that fail as written:**
  - The GELF examples omit the required `host`.
  - The Example Configuration's "WARN everywhere" is not true, since each
    route's own level wins over the group.
  - The troubleshooting example needs `encoder.uri=ttll` when pattern is on the
    classpath.
  - The Gradle snippet is Groovy syntax in a Kotlin block.
  - Inline `# comments` in properties snippets break when copied.
- **Extension guide** needs `-proc:full` or `annotationProcessorPaths` on JDK
  23+, and the generated builders need jspecify.
- **The level file is absent from the overview.**
- **Missing details:**
  - The JBoss Logging and Log4j2 snippets have no `<version>`.
  - Reporting omits the `LOGGERS` and `FACADES` sections.
  - `logging.keyvalues.disabled` names are not listed.
  - The disruptor's power of two `bufferSize` and ignored `shutdownTimeout`.
  - Queued events are lost if the JVM exits before `shutdownTimeout`.


---

# Agent report: a1-core-config

Rainbow Gum 1.0.0-SNAPSHOT (from ~/.m2), Java 27, Maven. Projects are in this directory:
`p1` (aggregate `rainbowgum` + system properties), `p2` (service-loader builder examples), `p3` (`rainbowgum-simple`), `p4` (dependency-scope check), `w*` (work dirs), helper scripts `emit.sh` / `runp.sh` (they turn `-D` args and a `.properties` file into a `java -cp` run of a tiny SLF4J app).

### Test plan
1. Getting Started: System-properties example verbatim, Builder (`RainbowGumProvider`) example verbatim, `rainbowgum-simple`.
2. Config: property keys, plural keys, implicit default config, runtime change (`logging.global.change`).
3. Router / Level resolvers / groups / route priority / additivity (verbatim doc examples, checked by real output and files).
4. Caller info.
5. Error paths: typo'd level names, typo'd keys, unknown route, level for a disabled route, missing modules, unknown URI schemes.
6. Example Configuration (Properties and Builder), Installation snippets.

### 1. Summary

| Label | High | Medium | Low |
|---|---|---|---|
| BUG | 0 | 2 (F4, F5) | 0 |
| DOC-WRONG | 2 (F1, F2) | 2 (F3, F8) | 0 |
| DOC-UNCLEAR | 0 | 2 (F6, F9) | 1 (F10) |
| ERROR-MESSAGE | 0 | 1 (F7) | 1 (F14) |
| NIT | 0 | 0 | 4 (F11, F12, F13, F15) |

Overall: the core engine is in good shape. Every property-based level, group, route-priority, additivity, runtime-change and caller-info example behaved as documented once the right modules were on the classpath, and most error messages for bad level values, missing modules and misspelled keys (with `logging.debug=help`) are excellent. A brand-new user following Getting Started literally does **not** end up with working logging, though. The `rainbowgum` dependency does not contain the pattern module that the very first example uses (F1), and the builder example in Getting Started has no output, so it crashes at startup (F2). The "Example Configuration" does not do what its prose promises for the noisy-package group (F3). Builder-defined routes without a root level behave inconsistently (F4). The most common level typo, `logging.levle.*`, is silently ignored even in `help` mode (F5). I would not ship the docs as they are. The product issues are medium severity.

### 2. Findings (most severe first)

#### F1. DOC-WRONG, high: `rainbowgum` does not pull in the pattern/file/json modules, so the first Getting Started example crashes
**Doc section:** Getting Started > Using System properties; Installation.
**Doc says:** "For most simply including the dependency `io.jstach.rainbowgum:rainbowgum` is enough. That dependency will transitively pull in the most commonly used modules." The system-properties example uses `-Dlogging.appender.console.encoder.uri=pattern`.
**Steps:**
`pom.xml` has `io.jstach.rainbowgum:rainbowgum:1.0.0-SNAPSHOT` (runtime scope, as in the docs) plus `org.slf4j:slf4j-api`. Code: `LoggerFactory.getLogger("com.myapp").info("x")`.
```
java -cp target/classes:$(cat cp.txt) \
  -Dlogging.appender.console.encoder.uri=pattern \
  '-Dlogging.appender.console.encoder.pattern.pattern=[%thread] %-5level %logger{15} - %msg%n' \
  -Dlogging.level.com.myapp=DEBUG com.myapp.Main
```
**Expected:** the documented pattern output.
**Actual:** SLF4J fails to initialize and the application dies:
```
io.jstach.rainbowgum.LogProvider$ProvisionException: Error for property. key: 'logging.appender.console.encoder.uri' ..., NotFoundException No encoder found. Scheme not registered. scheme: 'pattern', URI: 'pattern:///'. Scheme 'pattern' is provided by module 'io.jstach.rainbowgum.pattern' (Maven: 'io.jstach.rainbowgum:rainbowgum-pattern:1.0.0-SNAPSHOT'). Add that dependency. ...
Exception in thread "main" java.lang.IllegalStateException: Unexpected initialization failure
```
The `rainbowgum` pom declares `rainbowgum-pattern`, `rainbowgum-json` and `rainbowgum-file` with `<scope>test</scope>`, so they are not transitive. `mvn dependency:tree` shows only core, annotation, slf4j, systemlogger, jdk and jul. The same happens with the doc's `file:///./mycompany.log` output in the Additivity example ("Scheme 'file' is provided by module 'io.jstach.rainbowgum.file'") and with `gelf` (json). The error message itself is very good (it names the Maven artifact). The docs should say which module each scheme needs (pattern, file, json, rolling), or the aggregate should pull them in.

#### F2. DOC-WRONG, high: the Getting Started builder example crashes at startup (no output configured)
**Doc section:** Getting Started > Using RainbowGum Builders.
**Doc says:** the `GettingStartedExample implements RainbowGumProvider` class, "a simple configuration example".
**Steps:** `p2/src/main/java/com/myapp/GettingStartedExample.java` is the doc class verbatim, plus the imports I had to guess (see F9) and `META-INF/services/io.jstach.rainbowgum.spi.RainbowGumServiceProvider` containing `com.myapp.GettingStartedExample`. Dependencies are `rainbowgum` plus `rainbowgum-core` and `rainbowgum-pattern` at compile scope. The app calls `LoggerFactory.getLogger("com.myapp.App").info("x")`. Run `java -cp target/classes:$(cat cp.txt) com.myapp.App`.
**Expected:** a console line in the `[%thread] %-5level ...` pattern.
**Actual:**
```
io.jstach.rainbowgum.LogProvider$ProvisionException: Property missing. key:
    'logging.appender.console.output.uri' from:
        SYSTEM_PROPERTIES[logging.appender.console.output.uri]
  ↳ Failure providing Appenders for route: 'default'.
	at com.myapp.GettingStartedExample.provide(GettingStartedExample.java:14)
```
The route's `console` appender has an encoder but no `a.output(...)`, and the doc says nothing about outputs. Adding `-Dlogging.appender.console.output.uri=stdout` or `a.output(LogOutput.ofStandardOut())` fixes it. The error does at least name the missing key. Compare the builder in "Example Configuration", which does call `a.output(LogOutput.ofStandardOut())`.

#### F3. DOC-WRONG, medium: Example Configuration does not turn noisy packages down to WARN "everywhere"
**Doc section:** Example Configuration > Properties (and the intro bullets).
**Doc says:** "Noisy third party packages (`org.apache.http`, `io.netty`, `com.zaxxer.hikari`) turned down to `WARN` everywhere via a level group."
**Steps:** `w2/ex.properties` is the doc's Properties block verbatim. Run `../runp.sh ex.properties com.mycompany.orders.Svc org.apache.http.X io.netty.Y other.O` (loggers emit T/D/I/W/E) from `w2`, then look at `logs/`.
**Expected:** `org.apache.http.X` and `io.netty.Y` only emit WARN and ERROR on every route.
**Actual:**
- Console output (route `console`, `level=INFO`) shows `INFO org.apache.http.X` and `INFO io.netty.Y`.
- `logs/orders.json.log` (route `structured`, `level=DEBUG`) contains `DEBUG`, `INFO`, `WARN` and `ERROR` records for `org.apache.http.X` and `io.netty.Y`.

This is the documented "Route Level Priority" rule: every route sets a root `level`, so each route shadows the global `logging.level.noisy=WARN`. The Builder variant of the example does apply WARN on the console route, so the two versions are not equivalent. The same prose also says the `errors` route needs no extra override, which is true, but nothing explains that the group needs `logging.route.<r>.level.noisy=WARN` on every route that has a root level.

#### F4. BUG, medium: builder routes without a root level are enabled at TRACE, and a second route changes the result
**Doc section:** Getting Started > Using RainbowGum Builders; Implicit default configuration ("`logging.level=INFO`").
**Steps:** `p2` (`GS2`, `GS3`; both in `src/main/java/com/myapp`). `GS2` is the Getting Started builder with `a.output(LogOutput.ofStandardOut())` added, registered in `META-INF/services`. Run `java -cp target/classes:$(cat cp.txt) com.myapp.Emit other.Z`.
**Expected:** unconfigured loggers get the documented default of INFO (or the docs say what the builder default is).
**Actual:**
- Single route (`GS2`): `other.Z` logs TRACE, DEBUG, INFO, WARN and ERROR. Adding `-Dlogging.level=INFO` limits it to INFO and above.
- `GS3`, `-Dfirst=true`: route "a" has `r.level(Level.INFO)` and route "b" has only `r.level(Level.DEBUG,"com.myapp")`. Then `other.Z` logs **nothing at all**, not even ERROR to route "a". `isInfoEnabled()` is false for `other.Z`, but true for `com.myapp.x`. The output of the doc's own multi-route builder example (`ExampleBuilder`) also depends on route order in a way I could not explain from the docs.
- The docs never say what the effective level is when a builder route has no root level or when `logging.level` is unset.

#### F5. BUG / DOC-UNCLEAR, medium: a misspelled `logging.level` key is silently ignored, even with `logging.debug=help` or `logging.alerts.fail=warning`
**Doc section:** Troubleshooting > Misspelled and unused property keys.
**Doc says:** "A misspelled property key is otherwise silently ignored… An unread key close to a key that was read is reported as a warning with a suggestion." Also: "Some keys are never reported: `logging.level` keys (and route level keys)…".
**Steps:** `java -cp ... -Dlogging.debug=help -Dlogging.levle.com.myapp=DEBUG com.myapp.Main` (also `-Dlogging.alerts.fail=warning`).
**Expected:** warning, "Did you mean 'logging.level'?", and a failed start with `fail=warning`.
**Actual:** only `[INFO] ... Property key 'logging.levle.com.myapp' ... was set but not read during startup.` There is no suggestion and no failure. With `fail=warning` there is no output and the run continues. The same happens for a short key in `rainbowgum-simple`'s `logging.properties` (`levle.other=error`). `logging.apenders` and `logging.routs` do get a suggestion and fail startup, as documented. `logging.level.*` typos are the single most common mistake. The doc does list the exemption, but it does not warn the reader that the typo'd *prefix* is in the same blind spot. The fix would be to treat an unread key whose prefix is within edit distance 2 of `logging.level`, `logging.route`, or `logging.change` as a warning.

#### F6. DOC-UNCLEAR / ERROR-MESSAGE, medium: a route (or a route level) that is not in `logging.routes` is silently ignored; an unknown route errors with a generic "Property missing"
**Doc section:** Router.
**Steps:**
- `-Dlogging.route.foo.level=ERROR -Dlogging.route.foo.level.com.myapp=TRACE` (route `foo` not listed in `logging.routes`): everything stays at INFO, with no message. With `-Dlogging.debug=help` there is also no message, because route level keys are exempt from the unused-key check.
- `-Dlogging.routes=nothere` fails with `PropertyMissingException: Property missing. key: ['logging.route.nothere.appenders' ...]`.
- `-Dlogging.routes=other -Dlogging.appenders=console` fails the same way for `logging.route.other.appenders`. The doc comment says the shorthand only works for the "default" route, but the error does not hint at it.

**Expected:** a hint such as "route 'foo' has level settings but is not listed in logging.routes", and "route 'nothere' is listed in logging.routes but has no logging.route.nothere.appenders".
**Actual:** silent, or the generic message above (the message does at least name the key to set).

#### F7. ERROR-MESSAGE, medium: unknown URI scheme errors do not list the registered schemes or suggest a close one
**Doc section:** Config > Properties based.
**Steps:** `-Dlogging.route.default.publisher.uri=asyc`, `-Dlogging.appender.console.output.uri=stdot`, `-Dlogging.appender.console.encoder.uri=ttl` (each in its own run).
**Actual:** `No publisher found. Scheme not registered. scheme: 'asyc', URI: 'asyc:///'` (the same shape for output and encoder). Compare the level-name error, which lists all valid values, and the missing-module error, which names the Maven artifact. Here the message gives no list of known schemes and no "did you mean 'async'".

#### F8. DOC-WRONG, medium: Gradle snippet is not valid Kotlin DSL
**Doc section:** Installation > Gradle.
**Doc says** (fenced as `kotlin`): `runtimeOnly 'io.jstach.rainbowgum:rainbowgum:1.0.0-SNAPSHOT'`.
**Actual:** that is Groovy DSL syntax. Kotlin DSL needs `runtimeOnly("io.jstach.rainbowgum:rainbowgum:1.0.0-SNAPSHOT")`. (Gradle is not installed here, so I only checked the syntax by eye.) Both snippets also contain an empty line after `dependencies {`.

#### F9. DOC-UNCLEAR, medium: snippets lack imports, required dependencies and service registration
**Doc section:** Getting Started; Installation; Example Configuration (Builder).
- No imports are shown. A user has to find `RainbowGumServiceProvider.RainbowGumProvider`, `io.jstach.rainbowgum.pattern.format.PatternEncoderBuilder`, `io.jstach.rainbowgum.file.FileOutputBuilder`, `LogAppender.AppenderType`, `LogPublisher.PublisherFactory` and `java.lang.System.Logger.Level` in the Javadoc. `Level` is easily mistaken for another type.
- The second Maven snippet (programmatic config) only has `rainbowgum-core`. The Getting Started builder example does not compile with that: `package io.jstach.rainbowgum.pattern.format does not exist`. The Example Configuration builder additionally needs `rainbowgum-file` (`package io.jstach.rainbowgum.file does not exist`).
- Application code that imports `org.slf4j.*` does not compile with only the doc's `rainbowgum` dependency at `runtime` scope (`package org.slf4j does not exist`). `slf4j-api` arrives only at runtime scope, and the docs never say to add it.
- The `META-INF/services/io.jstach.rainbowgum.spi.RainbowGumServiceProvider` registration is only hinted at ("has additional documentation on how to register a service loader aware jar").

#### F10. DOC-UNCLEAR, low: `logging.level.root` is silently ignored; `fatal` and `config` are rejected although "SLF4J and JUL format" is promised
**Doc section:** Level Resolvers; Simple logging and properties.
**Doc says:** "Parsing of levels from property values allows SLF4J, java.util.logging (JUL), and System.Logger format…". The simple-props section says "`logging.level.root` maps to `RAINBOWGUM_level_root`".
**Actual:**
- `-Dlogging.level.root=ERROR` has no effect, in either the aggregate or `rainbowgum-simple` (INFO and WARN lines still print). `root` is just treated as a logger name.
- `-Dlogging.level.com.myapp=CONFIG` and `=fatal` produce `'CONFIG' is not a valid value for java.lang.System.Logger.Level. Valid values: 'all', 'trace', 'debug', 'info', 'warning', 'error', 'off', 'finest', 'fine', 'warn', 'severe'`. JUL `CONFIG` and Spring/Log4j `FATAL` are rejected.

If `root` is not special, the env-var example in the docs is misleading. A short statement of which names are accepted would help.

#### F11. NIT / ERROR-MESSAGE, low: a bad level value prints a very long stack trace and falls back to INFO
**Steps:** `-Dlogging.level=DEUBG`, or `-Dlogging.route.default.level=WRN`.
**Actual:** a good first line ("'DEUBG' is not a valid value … Valid values: …"), then ~30 stack-trace lines and 4 alerts (the INFO "Adding configurator" alerts are dumped together with the error). The app keeps running at INFO. For a plain typo this is noisy. Also, `logging.alerts.fail=error` only fails startup when the bad value is the global `logging.level`. A bad `logging.level.<logger>` or route level is resolved lazily when the logger is created, so it never fails startup, even with `-Dlogging.debug=help`. That contradicts the Troubleshooting claim "fails startup if any warning or error alert was recorded" unless the reader notices "Alerts recorded after startup never fail anything".

#### F12. NIT, low: console `%d` and `ttll` use different time zones
**Steps:** the Additivity properties with a file appender, `TZ` unset (local zone is EDT).
**Actual:** the `pattern` encoder (`%d`) prints local time (`15:33:16.274`). The default `ttll` encoder, and the file output with default encoder, print UTC (`19:33:16.272`). The "implicit default" block says `ttll` is the default, so it is documented, but the zone difference is not. A user comparing console and file output sees a 4 h offset. The GELF `_time` is UTC; that is fine.

#### F13. NIT, low: caller keywords render as empty text when caller info is off
`%class#%method:%line %file` prints `[#: ]` for loggers without caller info. This is consistent with the docs but could be mentioned. With `logging.global.change=true` and `logging.caller.com.mycompany=true` (or `BASIC`) it prints `com.myapp.Emit#main:6 Emit.java` as documented. Without `logging.global.change=true` the caller key is silently inert (documented: "this is required").

#### F14. ERROR-MESSAGE, low: duplicate route names give an unrelated message
**Steps:** `-Dlogging.routes=default,default` → `ProvisionException: Name is already in use. name = 'console'` with the context line `Failure providing Appender: 'console' from property: Fallback[logging.route.default.appenders]`. It does not say that route `default` is listed twice.

#### F15. NIT, low: the `logging.debug=help` report lists routers in a different order than `logging.routes`
`Router: structured`, `Router: console`, `Router: errors` is printed for `logging.routes=console,structured,errors` (order differs from the config). Cosmetic.

### 3. What passed

- `rainbowgum-simple` with no `logging.properties`: logging works out of the box (TTLL on stdout, INFO).
- `rainbowgum` + defaults, no properties: logging works at INFO (`ttll`).
- The "Implicit default configuration" block, set explicitly, behaves like the default.
- `logging.file.name=./f.log` adds a file appender and keeps the console; `logging.appenders=file` replaces it.
- A system property pattern override (`logging.appender.console.encoder.pattern.pattern`) overrides the builder's `.fromProperties(...)` pattern.
- Level inheritance example (`com.mycompany.stuff=DEBUG`, `com.mycompany=INFO`, global `ERROR`) resolves exactly as described.
- Level names are case-insensitive. `debug`, `Debug`, `FINE`, `SEVERE`, `WARNING`, `ALL`, `OFF` and `FINEST` parse. Logger names are case-sensitive (`logging.level.COM.a` does not match `com.a`).
- Level groups: with `logging.groups=tomcat` all three packages and their descendants get TRACE, `org.apachex` does not. Without `logging.groups` the group is ignored. Group names are case-sensitive. A route-level group override (`logging.route.default.level.tomcat=info`) wins over the global group.
- Route Level Priority example: the `errors` route with `level=ERROR` writes only ERROR even though `logging.level.com.mycompany.orders=DEBUG`.
- Additivity example verbatim: console gets WARN+, `mycompany.log` gets DEBUG+ for `com.mycompany` and INFO+ for everything else. With `logging.route.mycompany.level=OFF` added it contains only `com.mycompany` events.
- Router example: `logging.routes=example` with 2 appenders (stdout and file) and `publisher.uri=async` works.
- Example Configuration, Properties version: all three routes start, and the `gelf` JSON file, the error file and the console output are produced. `logging.debug=help` shows no unused keys, including `appender.jsonfile.type` and the `?bufferSize=` URI query.
- Example Configuration, Builder version: compiles and runs once `rainbowgum-file` and `rainbowgum-pattern` are added. The console route applies WARN to `org.apache.http`, and the error file contains only ERROR.
- Runtime change: with `logging.global.change=true` and `logging.change.com.mycompany=level` (or `true`), `System.setProperty(...)` followed by `RainbowGum.of().config().changePublisher().publish()` changes existing loggers (INFO→DEBUG→ERROR). Loggers outside the allowed prefix do not change. A bad value (`levl`) gives a clear message with the valid values.
- Caller info: `logging.global.change=true` + `logging.caller.com.mycompany=true|BASIC` fills `%class %method %line %file` for `com.mycompany.*` only. `yes` is rejected, listing the valid values.
- Troubleshooting: `-Dlogging.debug=help` with `-Dlogging.routs=x` and `-Dlogging.apenders=console` gives the documented WARN with "Did you mean 'logging.routes'?" / "'logging.appenders'?" and fails startup with `IllegalStateException`.
- Missing module errors name the module and the exact Maven coordinates.
- Plural keys: a trailing empty element is ignored (`console,`); an empty element in the middle is reported ("Invalid appender name ''…").
- `logging.alerts.fail=error` with a bad global `logging.level` fails startup.
- `rainbowgum-simple` short keys (`level.com.myco=debug`, `appenders=console`) work.

### 4. Not tested

- Gradle snippets (no `gradle` installed); only syntax-checked by eye (F8).
- `logging.global.change` with a real reloading property backend (Avaje Config etc.). I only simulated it with `System.setProperty` + `publish()`.
- Modular (`module-info`) setups, GraalVM native image and jlink.
- Spring Boot integration and `logging.level.*` behavior there (other areas' scope).
- Rolling files, other publishers' tuning, and JSON encoder details beyond a smoke check (other areas).
- `rainbowgum-simple` profiles and strict mode beyond a short-key smoke test.

---

# Agent report: a2-publishers-appenders-outputs

Environment: Java 27, jars from ~/.m2 at 1.0.0-SNAPSHOT. Test project: `app/` (apps `qa.Main N [sleepMs]`, `qa.Lv`, `qa.Follow`, `qa.Alert`, `qa.Exit`, `qa.Reentry`).
`app/run.sh` runs `java -cp` with the `rainbowgum` dependency classpath, plus extra module jars via `EXTRA=rainbowgum-file,rainbowgum-jfr`.
Scratch outputs are in `o/`. All commands below were run from `o/` as `../app/run.sh <java args> qa.Main ...`. All `-D` keys are system properties.

### Test plan (executed)
Publishers: sync / async / batchswap / disruptor; `bufferSize` and `shutdownTimeout` as properties and as URI query; invalid values; shutdown loss; System.exit.
Appenders: type, flags, multiple appenders and routes, reentry flags, bad names.
Outputs: stdout, stderr, `stream=follow`, file (append, prudent, bufferSize, `./` caveat, `logging.file.name`), rolling (all 5 settings, precedence, units), JFR (URI file form, `-XX` form, alert listener).
Misspelled and misplaced keys, checked with `-Dlogging.debug=help`.

### 1. Summary
| Label | High | Medium | Low | Total |
|---|---|---|---|---|
| BUG | 2 | 2 | 0 | 4 |
| DOC-WRONG | 0 | 1 | 0 | 1 |
| DOC-UNCLEAR | 0 | 2 | 2 | 4 |
| ERROR-MESSAGE | 0 | 0 | 2 | 2 (F10, F11; each bundles several messages) |
| NIT | 0 | 0 | 2 | 2 |

Overall impression: the core of this area is solid.
- Sync, async, batchswap and disruptor publishers deliver every event, including at `System.exit`.
- File and rolling outputs behave as documented. Rolling rolls, prunes, and honors the units and precedence rules.
- Flush and buffer settings take effect, and so do the reentry flags.
- Most error messages are excellent: a missing module names the Maven artifact, enums list their valid values, and size parsing gives examples.
- `logging.debug=help` gives good "Did you mean" hints for most misplaced keys.

Release concerns:
- **F1 (high):** the documented JFR recording flow (`-XX:StartFlightRecording` with `jfr:///`) crashes the application with a `LinkageError`.
- **F2 (high):** `logging.jfr.alerts.enabled=true` produces no alert events at all.
- Misspelled URI query parameters are never reported, although the docs recommend the query form.
- Invalid boolean property values are silently read as `false`. For `append` that truncates the log file.
- The doc examples contain inline `# comments` that break when copied.

### 2. Findings

#### F1. BUG (high): the documented `-XX:StartFlightRecording` + `jfr:///` flow crashes with `LinkageError`
- **Doc section:** "JDK Flight Recorder (JFR)" and "Starting A Recording".
- **Doc says:** `logging.appender.myappender.output.uri=jfr:///`, and "The usual way is wiring JFR in at the JVM level: `java -XX:StartFlightRecording:filename=recording.jfr,dumponexit=true,settings=profile -jar myapp.jar`".
- **Repro** (jars `rainbowgum` + `rainbowgum-jfr`, any app that logs):
  ```
  cd o; EXTRA=rainbowgum-jfr ../app/run.sh -XX:StartFlightRecording:filename=y.jfr,dumponexit=true -Dlogging.appender.console.output.uri=jfr:/// qa.Main 2
  ```
  Same result with `settings=profile` or `settings=none`. Same result on the module path (`java -p ... --add-modules ALL-MODULE-PATH`).
- **Expected:** events appear in `y.jfr`, and the application runs normally.
- **Actual:** exit code 1.
  ```
  [warning][jfr,system ] Unexpected error when adding instrumentation for event type io.jstach.rainbowgum.Info. null
  [warning][jfr,system ] ... loader 'app' attempted duplicate class definition for io.jstach.rainbowgum.jfr.RainbowGumLogEvent$InfoEvent
  Exception in thread "main" java.lang.LinkageError: loader 'app' attempted duplicate class definition for io.jstach.rainbowgum.jfr.RainbowGumLogEvent$InfoEvent. (... is in unnamed module of loader 'app')
  ```
  Dozens of the warning lines are printed.
- **Not affected:** the self-owned recording form `jfr:///path/x.jfr` without the `-XX` flag works. `jfr:///` without a running recording runs fine.

#### F2. BUG (high): `logging.jfr.alerts.enabled=true` produces no JFR alert events
- **Doc section:** "JFR Alert Listener".
- **Doc says:** "commit Rainbow Gum's own alerts ... as JFR events, opt-in via `logging.jfr.alerts.enabled=true` ... `io.jstach.rainbowgum.AlertTrace/AlertDebug/AlertInfo/AlertWarn/AlertError`".
- **Repro:** `qa.Alert` logs, calls `RainbowGum.of().close()`, then logs three more events. That makes the async publisher raise a real error alert ("dropped 1 event(s)"), which is printed on stderr.
  ```
  EXTRA=rainbowgum-jfr ../app/run.sh -Dlogging.jfr.alerts.enabled=true -Dlogging.route.default.publisher.uri=async -XX:StartFlightRecording:filename=al.jfr,dumponexit=true qa.Alert
  jfr summary al.jfr | grep -i rainbow ; jfr print --events 'io.jstach.rainbowgum.Alert*' al.jfr
  ```
- **Expected:** at least one `io.jstach.rainbowgum.AlertError` event.
- **Actual:** no `io.jstach.rainbowgum.*` events at all.
  - `jfr metadata` does not even list the Alert event types.
  - Adding `settings=profile,+io.jstach.rainbowgum.AlertError#enabled=true` makes no difference.
  - Same result with `-Dlogging.debug=all`, which also records startup alerts.
  - Same result when the appender output is `jfr:///path.jfr` (a startup `LinkageError` appears only with the F1 flags).
- **Note:** a typo `logging.jfr.alert.enabled` is correctly flagged by `-Dlogging.debug=help` with "Did you mean 'logging.jfr.alerts.enabled'?".

#### F3. BUG (medium): typos in URI query parameters are never reported, even in `logging.debug=help`
- **Doc section:** "Properties based" (query form, rolling example), "Troubleshooting → Misspelled and unused property keys".
- **Doc says:** the query-string form is equivalent to separate properties. Troubleshooting says "A misspelled property key is otherwise silently ignored ... reports each key that was set but never read".
- **Repro:**
  ```
  EXTRA=rainbowgum-file ../app/run.sh "-Dlogging.appender.console.output.uri=rolling:///tmp/x/app.log?maxFileSiz=10MB" -Dlogging.debug=help qa.Main 1
  EXTRA=rainbowgum-file ../app/run.sh "-Dlogging.appender.console.output.uri=file:///tmp/x/q.log?apend=false" -Dlogging.debug=help qa.Main 1
  ../app/run.sh "-Dlogging.appender.console.output.uri=stdout:///?strem=follow" -Dlogging.debug=help qa.Main 1
  ```
- **Expected:** a WARN such as "query parameter 'maxFileSiz' of logging.appender.console.output.uri was not read. Did you mean 'maxFileSize'?". The user then fails startup in help mode.
- **Actual:** no warning and no failure. The typo is silently ignored, so the file never rolls or appends as intended. The same typo written as a property (`...output.rolling.maxFileSiz`) is caught.
- **Doc fix if intentional:** state that query parameters are not checked.

#### F4. BUG (medium): invalid boolean property values are silently treated as `false`, so `append=yes` truncates the log file
- **Doc section:** "File Output" (`append`, `prudent`).
- **Repro:**
  ```
  echo old > f.log; EXTRA=rainbowgum-file ../app/run.sh -Dlogging.appender.console.output.uri=file:///tmp/x/f.log -Dlogging.appender.console.output.file.append=yes qa.Main 1; head -1 f.log
  ```
  Same result for `ture`, `no`, `0`, and `maybe`. Only `TRUE`/`true` appends (`TRUE` verified).
- **Expected:** a validation error like the one numeric and enum properties give.
- **Actual:** no error and no warning. The existing file is overwritten ("old" is gone).
- **Inconsistent with:** `-Dlogging.global.threadlocalDisabled=yes`, which fails startup with a validation error.

#### F5. DOC-UNCLEAR (medium): what happens to queued events when the JVM exits before `shutdownTimeout` drain completes
- **Doc section:** "Publishers".
- **Doc says:** "If delivery takes longer, close returns with an alert; the worker continues draining and closes the appenders afterward."
- **Observation:** a FIFO output was read by a slow consumer, with 20000 events from `qa.Main`:
  ```
  async.shutdownTimeout=0, 1 or 100: 18713 of 20000 events delivered
  async.shutdownTimeout=1000 or 3000: all 20000 delivered
  ```
  Without the FIFO, 2M events to a regular file with `shutdownTimeout=0` were all delivered.
- **Why it matters:** the "ERROR ... still draining after close" alert is printed, but then the JVM exits and the events still queued are lost. "Continues draining afterward" holds only if the process stays alive, and the docs do not say so.
- **Suggest:** state that events not yet written when the JVM exits are lost, and that `shutdownTimeout` is the only protection.

#### F6. DOC-WRONG (medium): doc snippets have inline `# comments` that break when copied into a properties file
- **Doc section:** "Appenders" (`logging.appender.myappender.type=reuse_buffer # an example config of appender`), "Plural keys for enabling/disabling" (`logging.appenders=appender1,appender2 # appenders that are enabled.`).
- **Repro:** `logging.properties` containing the Appenders snippet, with `rainbowgum-simple-props`:
  ```
  logging.appenders=myappender
  logging.appender.myappender.output.uri=stdout
  logging.appender.myappender.type=reuse_buffer # an example config of appender
  ```
- **Actual:** startup fails with `'reuse_buffer # an example config of appender' is not a valid value for ...AppenderType. Valid values: ...`.
- **Fix:** put comments on their own line. The error message itself is clear.

#### F7. DOC-UNCLEAR (medium): file output, where each key is placed, and what is silent
The docs say "See FileOutputBuilder for the full list of supported properties", but Overview → "Outputs" never says that the keys are `logging.appender.{name}.output.file.<prop>`.
- `logging.appender.console.output.append=false` (no scheme) is silently ignored by default. In help mode it is a WARN with a good suggestion ('...output.file.append').
- `output.file.name=x.log`, `output.type=stderr`, `output.out.uri` and `logging.publisher.uri=async` get only INFO "not read" with no suggestion, so they do not fail help mode.
- `logging.route.default.async.bufferSize=5` (publisher segment missing) gets only INFO with no hint.
- Suggest a short "common mistakes" list in "Publishers" and "Outputs".

#### F8. DOC-UNCLEAR (low): disruptor publisher's setting rules are undocumented
- **Doc section:** "Publishers" (last paragraph).
- **Doc says:** the disruptor jar "will replace the default async publisher". The `async` example sets `shutdownTimeout`.
- **Observed** (disruptor module plus the lmax 4.0.0 jar):
  - `publisher.async.bufferSize=1000` fails with `IllegalArgumentException: bufferSize must be a power of 2`. This does not name the key or the value, and the power-of-two rule is not documented. 1024 works.
  - `bufferSize=0` gives "bufferSize must not be less than 1".
  - `publisher.async.shutdownTimeout=500` is silently ignored with disruptor (only an INFO "not read" in debug mode; `DisruptorLogBuilder` documents only `bufferSize`).
  - `publisher.uri=disruptor` also works (not documented).
- All 100000 events were delivered with disruptor.

#### F9. DOC-UNCLEAR (low): `bufferSize` meaning conflicts
- **Doc section:** "Publishers" versus the `BatchSwapAsyncLogPublisherBuilder` Javadoc.
- The Overview says "Each buffer holds up to bufferSize events, so up to two batches may be retained". The Javadoc property table says "Positive pending capacity, excluding the batch being written".
- The `-Dlogging.debug=all` report prints `Publisher: BatchSwapAsyncLogPublisher (asynchronous)` but not `bufferSize` or `shutdownTimeout`, so the effective values cannot be confirmed.

#### F10. ERROR-MESSAGE (low): publisher/appender validation errors that omit the key
- `publisher.async.bufferSize=0` or `-5` gives `Validation failed for ...BatchSwapAsyncLogPublisherBuilder: bufferSize must be positive.` with no key or value (compare file `bufferSize=lots`, which names both).
- `shutdownTimeout=-1` gives the same shape of message.
- `shutdownTimeout=10s` or `PT10S` gives `NumberFormatException For input string: "10s"` without saying the unit is milliseconds.
- `logging.appenders=` (empty) gives `IllegalArgumentException: A single appender is required` with no key name.
- `rolling.maxFileSize=0`, `maxHistory=-1` and `maxHistory=0` are accepted silently. 0 means no rolling for size, and a negative history deletes all archives. The behavior is not documented.

#### F11. ERROR-MESSAGE (low): URI problems
- `output.uri=app.log` (the documented wrong form) gives `No output found. Scheme not registered. scheme: 'app.log', URI: 'app.log:///'`. It does not hint at the `./` prefix that the docs call out.
- Unknown schemes (`flie`, `asnyc`, `console`, `STDOUT`) do not list the registered schemes. Missing modules are very good about this.
- Uppercase `STDOUT` is rejected, although URI schemes are case-insensitive.
- `file://relative.log` is parsed with host `relative.log`, and the error says `FileNotFoundException: /tmp/.../o (Is a directory)`, naming the current directory.
- `output.uri=` (empty) gives `Expected scheme name at index 0: :///`.
- `stdout://` gives `URISyntaxException Expected authority at index 9`.
- A space in a path gives a raw `URISyntaxException`.
- `-Dlogging.appenders=console,console` is accepted silently.

#### F12. NIT (low): JFR default encoder output shows `- {}` for events without key values
Events logged with no key values have message `event 0 - {}`. The docs only show the `some message - {key=value}` example.

#### F13. NIT (low)
Docs: `-Dlogging.appender.console.encoder.pattern.pattern=...` in "Using System properties" needs the `rainbowgum-pattern` module. This was out of my area and not tested.

### 3. What passed
- Zero-config start, with `ttll` on stdout.
- `publisher.uri=sync|async|batchswap`, with properties `publisher.<scheme>.bufferSize` and `shutdownTimeout`, and the URI form `batchswap:///?bufferSize=8&shutdownTimeout=5000`.
- Async, batchswap and sync deliver every event (100k to 2M events), including at `System.exit`.
- Async `shutdownTimeout` behavior: with a slow FIFO reader and `shutdownTimeout=1000`, close returns, one ERROR alert is printed, and all data is still delivered.
- Async "one error alert per reason" after close: 3 dropped events gave 1 alert.
- Mixed routes: `logging.routes=default,example`, `logging.route.example.appenders`, route-specific publisher and appender encoders. The report shows the right publisher per route.
- A duplicate appender name across routes gives a clear "Name is already in use" error.
- Appender `type` for all five enum values (lowercase and uppercase), reported correctly in the `debug=all` output.
- `flags` is case-insensitive.
- `disable_immediate_flush` and the default immediate flush, observed on disk.
- Reentry: without a flag, 51 recursive writes; with `REENTRY_DROP`, 1 write; with `REENTRY_LOG`, an error with a stack trace on stderr.
- `logging.global.threadlocalDisabled=TRUE` selects `LockNewBufferLogAppender`.
- `stdout`, `stderr`, and `stream=follow` as a URI query and as a property (verified with `System.setOut` capture). The default does not follow. A bad `stream` value lists the valid values.
- File output: `file:///abs`, `file:///./x`, `./x.log`, and `fileName` as a property.
- File output: `append` default true and `append=false` (query and property, with `TRUE` appending), `bufferSize` honored with `disable_immediate_flush` (1KB/16KB/64KB), `bufferSize=0`, and the >2GB error.
- `logging.file.name=sub/e.log` creates the directory and logs to both file and console (file first).
- Rolling:
  - `maxFileSize`, `maxHistory`, `totalSizeCap` and `fileNamePattern` (`.%i.bak`) work, via query and via property; the plain property wins over the query.
  - `fileNamePattern` with `%d` or without `%i` gives clear errors.
  - `cleanHistoryOnStart=true` prunes old archives at start.
  - `1MB` is 1048576 bytes and `1000000` is bytes.
  - The default `maxFileSize` of 10MB (9.6MB written, no roll).
  - A restart appends and continues numbering.
- Missing-module errors for `file` and `jfr` output and `disruptor` publisher name the Maven artifact. Enum, size and validation errors are helpful.
- "Did you mean" suggestions in `-Dlogging.debug=help` for: output vs outputs/outpt, `encoder`, `flag`/`flags`, `types`, `apenders`, `route.default.publishr.uri`, `publisher=async`, `publisher.bufferSize`, `shutdownTimout`, `output.append`, `output.file.apend`.
- `rolling://` and `jfr:///path` return missing-permission and nonexistent-directory errors clearly.
- JFR: `jfr:///path/x.jfr` records all five level types with `eventThread`, message, `logger` and `throwable`, and key values appear as `{key=value k2=v2}`.

### 4. Not tested
- `prudent=true`: the output runs, but multi-process locking was not exercised.
- A real logrotate / reopen signal flow ("External Tool Support"): needs an external signal module.
- Native-image locking strategies.
- `jfr print` of throwables and stack traces.
- A JFR flow that sets level overrides or a `.jfc` file.
- Spring/Helidon/Micronaut integrations.
- The bundle and OTLP outputs.
- Disruptor: only tested with the lmax jar added by hand to the classpath. Without it, `NoClassDefFoundError` appeared, which may be an artifact of how the classpath was built.
- `batchswap` internals (`events.queued` and `events.dropped` metrics) were not read, because there is no easy metrics export without custom code.

---

# Agent report: a3-encoders-formatters

Test projects: `app/` (Main, Main2, Main3 log MDC/key values, exceptions with causes/suppressed, unicode, control chars, newlines, odd keys; `r.sh`/`run.sh` run it on a classpath, `ptyrun.py` runs it under a real pseudo-terminal) and `prog/` (programmatic builders). Dependencies: `rainbowgum` (aggregate), `rainbowgum-pattern`, `rainbowgum-json`, `rainbowgum-jansi` (jansi kept off the classpath except for the JAnsi tests), `rainbowgum-file` for file tests. Java 27.

### Test plan
1. Each JSON encoder (gelf, ecs, logstash, logback): output shape, MDC/key values, exceptions, escaping, unicode, pretty print, every documented option, bad values. Validate with `jq` and Python `json` (duplicate-key detection).
2. TTLL: every option value, aliases, invalid values, color modes x themes, real pty vs pipe, `NO_COLOR`, `TERM=dumb`, `logging.global.ansi.disable`.
3. logfmt, json5: every option, collisions, odd keys, colors.
4. JSON key values formatter (builder example verbatim plus variants).
5. Pattern: doc examples, keyword coverage, invalid patterns, PatternConfig properties, highlight, exception handling.
6. JAnsi. Property precedence (plain vs URI query), typo/old keys.

### 1. Summary

| Label | Count |
|---|---|
| BUG | 4 (1 high, 2 medium, 1 low) |
| DOC-WRONG | 2 |
| DOC-UNCLEAR | 5 |
| ERROR-MESSAGE | 3 |
| NIT | 4 |

Overall impression: the text and JSON encoders are in good shape. Output shapes match the docs. Escaping is correct and every JSON encoder's output parsed with `jq`, pretty-printed or not. Colors, themes, `color`/`NO_COLOR`/`ansi.disable` behave as documented, and the enum-valued option errors (TTLL, json5, logfmt) are excellent: they say what was wrong and list the valid values. Release blockers or near-blockers are (a) the pattern encoder printing following `%keyword`s literally after a group like `%highlight(...)`/`%red(...)` that has no `{options}` (F1), (b) JSON encoders, logfmt, and GELF emitting duplicate keys when an MDC key collides with a reserved field (F2), and (c) the doc examples that don't run as written (F3, F4). Invalid boolean values are silently treated as false (F5), which is inconsistent with the otherwise strict validation. The pattern "invalid pattern" error hides the real cause (F6).

### 2. Findings (most severe first)

#### F1. BUG (high): pattern keyword directly after a `%group(...)` without `{options}` is printed literally (e.g. `%n` -> literal `%n`)
- **Doc section**: Pattern Formatter (builtin keywords "follow Logback pattern syntax"; ColorKey list)
- **What the doc says**: "Rainbow Gum implements most of the builtin Logback pattern keywords"; examples such as `[%thread] %-5level %logger{15} - %msg%n`.
- **Repro** (`app/` project: any app that logs, with `rainbowgum` + `rainbowgum-pattern`):
  ```
  java -cp <cp> -Dlogging.appender.console.encoder.uri=pattern \
    '-Dlogging.appender.console.encoder.pattern.pattern=%red(%msg)%n' qa.Main
  ```
- **Expected**: one colored message per line (Logback behaviour).
- **Actual**: the `%n` is output as the text `%n`, so all events run together on one line:
  ```
  hello world%nstarted%nkv msg%nmulti
  ```
  Same with `%red(x)%n`, `%highlight(%level)%n`, `%-5(%msg)%n`, `%(%msg)%n`, `%msg%red(x)%n`, `%green(%msg)%msg%n` (-> `hello world%msg`), `%green(%msg)%level%n` (-> `hello world%level`).
  Works when something other than `%` follows `)`: `%red(%msg) %n`, `%red(%msg)|%n`, and when `{options}` follow: `%highlight(%level){INFO=red}%n`.
  The silent failure (no error, just wrong output) makes this particularly nasty. `%highlight(%-5level)%n`-style patterns are very common in Logback configs being migrated.

#### F2. BUG (medium): key values / MDC whose key equals a reserved field name produce duplicate keys in the output (ECS, Logstash, GELF, logfmt)
- **Doc section**: JSON Encoders (ECS: "MDC / key values ... are written as top-level fields using their own key name"; Logstash: "Key values are flattened top-level fields"), logfmt Formatter. Contrast with JSON5 Formatter, which documents that colliding keys go to a nested `keyValues` object "so nothing is dropped, renamed, or written twice".
- **Repro**: `app/src/main/java/qa/Main3.java`:
  ```java
  log.atInfo().addKeyValue("message","M").addKeyValue("@timestamp","T").addKeyValue("log.level","LL")
     .addKeyValue("level","L").log("real");
  ```
  `java -cp <cp> -Dlogging.appender.console.encoder.uri=logstash qa.Main3 | python3 -c 'import json,sys; ...'`
- **Expected**: either a documented rule (rename / nest / drop) or at least valid unambiguous JSON.
- **Actual** (trimmed; all produce duplicate member names, which RFC 8259 says are unpredictable, and most parsers keep the *last*, i.e. the user's bogus value overrides the real level/timestamp/message):
  - logstash: `..."message":"real",...,"level":"INFO",...,"message":"M","@timestamp":"T","level":"L"`  (duplicates: message, logger_name, @timestamp, level)
  - ecs (flat and structured): `"@timestamp":"...","log.level":"INFO","message":"real",... "message":"M","@timestamp":"T","log.level":"LL"`
  - gelf: `"_level":"INFO",... "_level":"L"` (and with Main2, `"_time"` twice from a `time` key)
  - logfmt: `msg=collide ... msg=m2 level=L time=t` (a logfmt parser takes the last `msg`/`level`, so the real values are lost). With Main2: `time=... level=INFO logger=... thread=main msg=collide msg=m2 level=L time=t ok=1`.
  - logback encoder is fine (key values live under `"mdc"`), as is json5 (nests).
  Realistic keys: `level`, `host`, `message`, `time`, `logger_name` are plausible MDC keys.

#### F3. DOC-WRONG (medium): Getting Started programmatic example does not run as written (no output configured)
- **Doc section**: Getting Started / "Using RainbowGum Builders"
- **What the doc says**: the `GettingStartedExample implements RainbowGumProvider` snippet is presented as "a simple configuration example". (It also has no imports and no mention of the `META-INF/services/io.jstach.rainbowgum.spi.RainbowGumServiceProvider` registration; the class is `RainbowGumServiceProvider.RainbowGumProvider`, a nested type. See F9.)
- **Repro**: `prog/` project, copy snippet verbatim, register it in META-INF/services, add `rainbowgum` + `rainbowgum-pattern`, run `java -cp ... qa.Main`.
- **Expected**: output in `[main] INFO c.example.App - ...` format.
- **Actual**: logging fails to initialize:
  ```
  SLF4J(E): Failed to instantiate SLF4J LoggerFactory
  io.jstach.rainbowgum.LogProvider$ProvisionException: Property missing. key:
      'logging.appender.console.output.uri' from:
          SYSTEM_PROPERTIES[logging.appender.console.output.uri]
  ```
  Works after adding `a.output(new StdOutOutputBuilder("console").build());` (the output builder isn't mentioned in the snippet). The error message itself is clear.

#### F4. DOC-WRONG (medium): GELF examples in "Config" and "Encoders" omit the required `host`; they fail at startup
- **Doc section**: Config > Properties based ("logging.appender.example.encoder.uri=gelf / ...gelf.prettyPrint=true" and `gelf:///?prettyPrint=true`) and Encoders ("The above will use the GELF JSON encoder and configure it to pretty print.")
- **Repro**: `java -cp <cp> -Dlogging.appender.console.encoder.uri=gelf -Dlogging.appender.console.encoder.gelf.prettyPrint=true qa.Main` (also with `uri=gelf:///?prettyPrint=true`).
- **Expected** (per the docs): pretty-printed GELF.
- **Actual**: startup failure `Validation failed for ...GelfEncoderBuilder: Property missing. key: 'logging.appender.console.encoder.gelf.host' from: SYSTEM_PROPERTIES[...], [logging.appender.console.encoder.uri]->URI(gelf:///)[host]`. The Javadoc says `host` is `Required: true`; the GELF section's own example sets `host`, but the examples above do not. The error message is good. Also: the whole application's logging init fails (the app then dies with `IllegalStateException: Unexpected initialization failure`) with a ~60 line stack trace dump, for a configuration error. See F8.

#### F5. BUG (medium): invalid boolean values are silently accepted as `false`
- **Doc section**: JSON Encoders (`structured`, `prettyPrint`), Pattern (`ansiDisabled`)
- **Repro**:
  ```
  -Dlogging.appender.console.encoder.uri=ecs -Dlogging.appender.console.encoder.ecs.prettyPrint=maybe
  -Dlogging.appender.console.encoder.uri=ecs -Dlogging.appender.console.encoder.ecs.structured=yes
  -Dlogging.appender.console.encoder.uri=pattern -Dlogging.pattern.config.console.ansiDisabled=maybe
  ```
- **Expected**: a validation error like the one for `ttll.level=loud` / `gelf.timeFractionalDigits=12` / `maxBufferSize=abc`, which are all rejected helpfully.
- **Actual**: startup succeeds and the option is treated as false (`structured=yes` gives flat ECS; `prettyPrint=maybe` gives compact output; no warning). `yes`, `on`, `1` and typos therefore silently disable the feature. (Note `TTLL` booleans `true`/`false` are properly validated as enum aliases, so the behaviour is inconsistent.)

#### F6. ERROR-MESSAGE (medium): "Pattern is invalid" hides which keyword is wrong
- **Doc section**: Pattern Formatter
- **Repro**: `-Dlogging.appender.console.encoder.uri=pattern '-Dlogging.appender.console.encoder.pattern.pattern=%d %bogus %msg%n'`
- **Expected**: message naming the unknown keyword (`unknown pattern keyword %bogus at position 3; see PatternRegistry.KeywordKey`).
- **Actual**: top-level message is only `Error for property. key: 'logging.appender.console.encoder.uri' ... Pattern is invalid: %d %bogus %msg%n`. The real reason (`Caused by: java.lang.IllegalStateException: Missing formatter for key: bogus`) is only in the last `Caused by` of a long stack trace. Also the error blames `...encoder.uri` rather than `...encoder.pattern.pattern`. Malformed patterns (`%msg %(%level%n`, `%msg{%n`) and an empty pattern give no hint at all (`Pattern is invalid: ` with nothing after the colon for an empty pattern).
  Keywords valid in Logback but unsupported here (`%kvp`, `%replace(...)`, `%caller`, `%marker`, `%contextName`, `%lsep`, `%tid`, `%sn`, `%u`, `%i`, `%F`... see F11) get the same message, so a Logback migrator cannot tell "typo" from "not supported".

#### F7. BUG (low): logfmt key sanitizing is silent and unspecified; `color` also colors keys of key values
- **Doc section**: logfmt Formatter ("Keys, `msg`, `error`, and `stacktrace` are never colored")
- **Repro**: `Main2` key values `"e f"`, `"a=b"`, `""`, `k"q` with `encoder.uri=logfmt`.
- **Actual**: keys are rewritten: `e f` -> `e_f`, `a=b` -> `a_b`, `""` -> `_`, `k"q` -> `k_q`; `a.b`/`c-d` are kept. (JSON/ECS/Logback encoders keep the original key.) Not documented. Two different keys (`a b`, `a_b`) can silently merge/duplicate.
- **Color**: `-Dlogging.appender.console.encoder.uri=logfmt -D...logfmt.color=force` gives `msg="hello world" ^[[2;39mrequestId=42 user="Ada Lovelace"^[[0;39m`, i.e. the key names `requestId=`/`user=` are inside the faint escape although the doc says keys are never colored. (Same for the "values are colored" line: the whole `k=v k2=v2` run is a single colored span.)

#### F8. ERROR-MESSAGE (low): config errors dump ~60-line stack traces and abort the application's logging with a generic `IllegalStateException`
- **Doc section**: Config / Encoders in general
- **Repro**: any invalid encoder config above (e.g. `ttll.level=loud`).
- **Actual**: SLF4J prints `Failed to instantiate SLF4J LoggerFactory`, then the full stack trace of the cause chain twice, then the application throws `IllegalStateException: Unexpected initialization failure` from `LoggerFactory.getLogger`. The useful line is 3-4 lines into the output and repeated in each `Caused by`. The text of that line is excellent (property key, bad value, enum type, valid values), but the user must scroll to find it. Arguably intentional (fail fast), but not documented: there is no documentation stating that an invalid logging config prevents the application from starting logging at all rather than falling back.

#### F9. DOC-UNCLEAR (medium): programmatic setup is hard to follow from the docs
- **Doc section**: Using RainbowGum Builders
- Snippet lacks imports, the nested interface name (`RainbowGumServiceProvider.RainbowGumProvider`), how to register it (a link to `RainbowGumServiceProvider` is the only hint), and the output (F3). I had to read Javadoc for `LogAppender.Builder.output(...)`, `StdOutOutputBuilder`.

#### F10. DOC-UNCLEAR (medium): pattern default vs TTLL default regarding key values / MDC
- **Doc section**: TTLL Formatter vs Pattern Formatter
- TTLL section: "By default each line also carries the event's key values ... Key values are on by default because modern logging relies on them." But the Pattern section says that when `rainbowgum-pattern` is on the classpath it is the default console encoder. With the aggregate dependency + `rainbowgum-pattern` the default console output is the pattern default, which *does not show* MDC/key values (`15:32:09.691 [main] INFO  com.example.App - hello world`, while with `-Dlogging.pattern.disable=true` the same event prints `{requestId=42&user=Ada%20Lovelace}`). A reader of the TTLL section would not expect key values to vanish just because a module was added. Also the pattern default time is in the local zone vs TTLL's UTC (`15:32` vs `19:32` in the same run); the TTLL doc says UTC but the pattern section does not mention that the default pattern uses the system zone, nor that "the same coloring" does not include the key values and has a different time zone.

#### F11. DOC-UNCLEAR (medium): pattern keyword spellings are not listed anywhere
- **Doc section**: Pattern Formatter; Javadoc `PatternRegistry.KeywordKey` / `ColorKey`
- The doc says "The builtin keywords are listed in `PatternRegistry.KeywordKey` and `ColorKey`", but those pages only list enum constants (`ENCODED_MDC`, `LOCAL_SEQUENCE_NUMBER`, `FILE`, ...) with one-line descriptions, not the actual spellings. I had to guess by trial: `%encodedMdc` works, `%lsn` works, `%micros` works, `%file` is accepted (empty output) but `%F` (the Logback spelling) is rejected; `%emdc`, `%kv`, `%sn` are rejected. "Most of the builtin Logback keywords" with no list of what is not supported (`%kvp`, `%replace`, `%marker`, `%caller`, `%contextName`, `%tid`, `%lsep`; `%F` in Logback is file name) forces trial and error. `%C %M %L %F` print empty strings unless caller information is enabled (separate "Caller Information" section), not mentioned in the pattern section.

#### F12. DOC-UNCLEAR (low): `lineSeparator=\n` is shown in properties-file syntax; as a system property it is the 2 characters backslash-n
- **Doc section**: Configuring PatternConfig with Properties (`logging.pattern.config.myappender.lineSeparator=\n`)
- With `-Dlogging.pattern.config.console.lineSeparator='\r\n'` output contains the literal text `\r\n` (confirmed with `od -c`). Docs say system properties are the default `LogProperties`; the snippet only works from a properties file/unescaping source.

#### F13. DOC-UNCLEAR (low): typo'd property keys are silently ignored
- **Doc section**: Config ("switching `uri` ... leaves the old type's settings unread")
- `encoder.ecs.prettyprint=true` (wrong case), `ecs:///?structuredd=true`, `encoder.pattern.patern=...` (this one fails because pattern is required), `encoder.ecs.structured=true` without `encoder.uri=ecs`, and `-Dlogging.appender.console.encoder=ecs` (old/obvious key) are all silently accepted and ignored; output is the default encoder with no warning. Documented as design, but there is no suggestion in the docs that the user can find out which keys were read (the "diagnostics" for property keys are not mentioned in this area). The Spring-style `logging.pattern.console` is also ignored outside Spring Boot (the overview says Rainbow Gum "natively understands" `logging.pattern.*` in the Spring Boot section; fine there, but the same name has no effect in plain use and gives no hint).

#### F14. ERROR-MESSAGE (low): unknown encoder scheme does not list the registered schemes
- **Repro**: `-Dlogging.appender.console.encoder.uri=ecss`
- **Actual**: `NotFoundException No encoder found. Scheme not registered. scheme: 'ecss', URI: 'ecss:///'`. Compare with the excellent message for a *known* scheme whose module is missing (`gelf` without rainbowgum-json: "Scheme 'gelf' is provided by module ... Add that dependency"). Listing the available schemes (or "did you mean ecs?") would help typos.

#### F15. NIT (low): Logback JSON pretty print mis-indents the nested `mdc` object
- `logback.prettyPrint=true`: 
  ```
  "mdc":{
   "requestId":"42",
   "user":"Ada Lovelace"},
  ```
  Valid JSON, but the nested members are at the same indent as the top level and the closing brace is on the last member's line. (ECS structured, logstash pretty print are flat so there is nothing nested to compare.)

#### F16. NIT: `json5.format=xml` error lists `percent` and `logfmt` as valid values, but they are then rejected (`format=percent is not JSON. Use json or json5.`); docs list only `json5`/`json`.

#### F17. NIT: JAnsi on JDK 27 prints `WARNING: A restricted method in java.lang.System has been called ... Use --enable-native-access=ALL-UNNAMED` on startup
- The doc note says "Future versions of the JDK may require command line arguments"; on JDK 27 (the docs' own link target) it is already a warning on every start. Suggest "already warns on JDK 24+".

#### F18. NIT: `log.atInfo().addKeyValue("k", null)` fails with an NPE in `RainbowGumEventBuilder.addKeyValue` (`valueSupplier is null`) because the literal `null` binds to the `Supplier` overload. This is plain Java overload resolution but the stack trace points into Rainbow Gum; `(Object) null` works and null values are rendered as documented (see "What passed"). Not an encoder issue; found while testing null values.

### 3. What passed

- Docs "Using System properties" example verbatim: pattern `[%thread] %-5level %logger{15} - %msg%n` works (`[main] INFO  c.example.App - hello world`).
- Doc `Config` example `encoder.uri=json5` and `json5.prettyPrint=spacing`; URI-query form `gelf:///?host=h2&prettyPrint=false`; precedence "plain property beats URI query" verified for json5 and ttll.
- GELF: required `host` (with `somehost` example), output shape (host, short_message, full_message with stack trace, `_` additional fields, level mapping INFO=6/WARN=4/ERROR=3/DEBUG=7, version 1.1), `prettyPrint`, `timeFractionalDigits` 0-9 (12 rejected with a good message), `headers` as `a=1&b=x%20y` and as `headers.keys=` form, missing header value error (good message). Valid JSON per `jq`.
- ECS: flat (`log.level`, `ecs.version`, `process.thread.name`, `error.type/message/stack_trace`) and `structured=true` nested shapes, all `service*`/`eventDataset` options (flat and structured nesting), `prettyPrint`; MDC as top-level fields. Valid JSON.
- Logstash: `@timestamp` with offset, `@version`, `level_value`, `stack_trace`, `zoneId` (UTC, Asia/Tokyo; `Mars/Base` rejected with a good message), `prettyPrint`.
- Logback JSON: `mdc` object, `throwable` with `stepArray` and recursive `cause`, `prettyPrint`.
- JSON escaping for quotes, backslash, newline, tab, control char `\u0001`, and unicode (é, 日本, emoji) is correct in ecs/logstash/logback/gelf/json5/logfmt; multi-line messages stay one line in all but TTLL/pattern (where raw newline is expected).
- Exceptions with cause chain and suppressed exceptions in all encoders; throwable with null message; empty message.
- TTLL: every documented value of `timestamp` (ttll, iso, none, DateTimeFormatter pattern, true/false/default), `thread`, `level`, `logger`, `keyValues`, `keyValuesWhenEmpty` (auto/omit/show, `omit`+`json` rejected with a clear, helpful message), `color`, `theme` (rainbowgum, spring, one_dark 24-bit, darcula 24-bit) behave as described; `theme=default` ok; invalid values give excellent errors (key, value, enum, valid values).
- Colors: with a real pty, default color on; piped: none; `color=off/false/detect(without theme)` none; `detect` + theme colors on pty; `force` colors even when piped; `NO_COLOR=1`, `TERM=dumb` and `logging.global.ansi.disable=true` disable colors; file output default encoder is uncolored and uses TTLL with key values.
- logfmt: documented shape, `levelFormatter=right_pad_level_formatter`, `error` / `stacktrace` with escaped newlines, color/theme, URI-query form.
- json5: default (`level:"INFO" ,`), `format=json` (valid JSON), `keyValues` merged/nested/none, key collision nesting in `keyValues` (merged), `prettyPrint` level_padding/spacing/off/full/true/false, quoting of keys with `.`, `-`, spaces, digits first, and an empty key.
- KeyValuesFormatterBuilder doc example verbatim: `hello {requestId:"42"}`, `{}` when no keys; `keys(...)` selection with null strategies; percent and logfmt formats.
- Pattern: `%d{...}`, `%X{k}`/`%mdc`, `%property{app}`-style lookup (empty if unset), `%r`, `%micros`, `%lsn`, `%encodedMdc`, `%highlight` with and without `LEVEL=style` options (bad style `bogus` gives a helpful message listing valid words), `%rEx`, `%nopex` suppressing the automatic exception, automatic exception appended when the pattern has no exception keyword, `logging.pattern.config.{name}.zoneId` (bad zone rejected well), `logging.pattern.disable=true` falls back to TTLL.
- Pattern formatting modifiers (`%-5.10level`, `%20.-5msg`, `%logger{0}`/`{5}`/`{15}`).
- JAnsi: works (colors on tty, stripped when piped even with `color=force`), `logging.jansi.disable=true` and `logging.global.ansi.disable=true` turn it off.
- Missing-module error for `gelf` without `rainbowgum-json` is excellent (names the module and Maven coordinates).
- Programmatic `PatternEncoderBuilder(...).fromProperties(...)` property override works once output is configured.

### 4. Not tested
- Spring Boot structured logging properties (`logging.structured.format.*`) and `logging.pattern.*` under Spring: another area / needs Spring.
- `%highlight`/theme colors on a Windows console, IntelliJ terminals (no such environment).
- GELF over network/UDP chunking (encoder only).
- Caller information (`%C`, `%M`, `%L`, `%F`) enabling: separate section; only verified that they print empty by default.
- JSON5 output validated only by eye (no JSON5 parser available); JSON mode validated with `jq`.
- Custom pattern keywords and programmatic `PatternConfig` (module Javadoc), OTLP/JSON encoder, custom LogEncoder implementations.
- Performance/thread-safety of encoders; `maxBufferSize` only checked for value validation, not behaviour.

---

# Agent report: a4-alerts-troubleshooting

All runs used a Maven project in `app/` (deps: `rainbowgum`, `rainbowgum-simple-props`, `rainbowgum-pattern`, jspecify), run with
`java -cp target/classes:<mvn dependency classpath>`. Helper scripts: `app/run.sh` (with pattern module), `app/run2.sh`
(without pattern module), `app/run3.sh <cfgdir> ...` (adds `app/cfg/<dir>/logging*.properties` to the classpath). Java test programs: `app/src/main/java/qa/*.java`.
Output below is trimmed. I did not look at any Rainbow Gum source.

### Test plan
1. Troubleshooting: run the `-Dlogging.debug=help` examples verbatim, then vary: all debug modes (`off/error/info/all/help/true/false/bogus`), put `logging.debug` in `logging.properties`.
2. Misspelled/unused keys: system properties and `logging.properties`, profile files, unprefixed keys, case/`-` variants, distance-2 typos, keys of an unselected encoder or publisher, keys in old or wrong layouts, level keys, env vars.
3. `logging.alerts.fail` (`off/error/warning/warn/true/false/bogus`), `logging.alerts.unusedKey`, `logging.alerts.unobservedErrorsAction`: sources (system property, file, env var, profile) and exact fail or no-fail behaviour.
4. Invalid values and missing deps for other areas (encoders, outputs, publisher, appender type/flags, levels, profiles, simpleprops strict): are the errors helpful?
5. Alerts/Metrics/Reporting API: copy the three doc snippets verbatim, check that the standard metrics exist and change (`events.queued`, per-logger alert counters, gauges).
6. Filtering: copy the router filter example and the SLF4J decorator example verbatim and run them.

### Summary

| Label | Count |
|---|---|
| BUG | 0 confirmed (one unreproduced hang, see Not tested) |
| DOC-WRONG | 1 |
| DOC-UNCLEAR | 5 (findings 3, 4, 6, 7, 8) |
| ERROR-MESSAGE | 2 (findings 2, 5) |
| NIT | 2 (findings 9, 10) |

Overall: this area is close to release-ready. The core promise holds in every combination I tried. A misspelled key close to a read key gives a WARN with the right "Did you mean", and `logging.debug=help` and `logging.alerts.fail` fail startup exactly as documented, from system properties, `logging.properties`, profiles and env vars. Most invalid-value errors list the valid values and the key's source. The Alerts/Metrics/Reporting snippets and the Filtering examples compile and work. The weak spots:
- The headline Troubleshooting example silently does not reproduce when the `rainbowgum-pattern` module is on the classpath, which is a common setup.
- The most common typo class, `logging.level.*` / `logging.route.*.level`, gets no suggestion and cannot fail startup.
- A startup failure caused by a *missing required* property gives no "did you mean" for the typo'd key that caused it, even in `help` mode.
- Several documented limitations (`logging.debug` in a file, `unusedKey` alone records nothing) are silent in practice.

### Findings (most severe first)

#### 1. DOC-WRONG (medium): the Troubleshooting example does not behave as shown when the pattern module is on the classpath
- **Doc section**: Troubleshooting. The doc says `java -Dlogging.debug=help -Dlogging.appender.console.encoder.ttll.levle=plain -jar app.jar` "prints the report and then: `[WARN] ... Did you mean '...ttll.level'?` / `IllegalStateException: 1 alert(s) ...`".
- **Steps**: `app/pom.xml` (deps `rainbowgum`, `rainbowgum-simple-props`, `rainbowgum-pattern`), then `app/run.sh -Dlogging.debug=help -Dlogging.appender.console.encoder.ttll.levle=plain`.
- **Expected**: WARN with "Did you mean ...ttll.level", startup fails (per doc).
- **Actual**: exit 0, the app runs, and only an INFO line is printed, with no suggestion:
  ```
  [INFO] - RAINBOW_GUM - LogConfig - Property key 'logging.appender.console.encoder.ttll.levle' from SYSTEM_PROPERTIES[...] was set but not read during startup.
  ```
  The default encoder is `FormatterEncoder ... description="%cyan(%date{HH:mm:ss.SSS}) ..."`, i.e. pattern, so no `ttll.*` key is ever read and "Suggestions only ever name keys that something actually read" applies. The same command with `rainbowgum-pattern` removed (`app/run2.sh`) behaves exactly as documented.
- **Notes**:
  - The Pattern section says the pattern module becomes the default console encoder, but the Troubleshooting page never says the example needs `encoder.uri=ttll`, nor that a typo in an unselected encoder's key is only INFO.
  - Adding `-Dlogging.appender.console.encoder.uri=ttll` makes the doc output appear.
  - Suggest adding `encoder.uri=ttll` to the example, or a note.
  - Also in the doc's "prints the report and then" sample, the real output has more lines (see finding 9).

#### 2. ERROR-MESSAGE (medium): a startup failure caused by a typo'd *required* key gives no suggestion, even in `help` mode
- **Doc section**: Troubleshooting ("Misspelled and unused property keys"); the promise is that `help` mode finds typos.
- **Steps**:
  - `app/run.sh -Dlogging.debug=help -Dlogging.appender.console.encoder.uri=pattern -Dlogging.appender.console.encoder.pattern.patern=x`
  - `app/run.sh -Dlogging.debug=help -Dlogging.routes=a -Dlogging.route.a.apenders=console`
- **Expected**: since the misspelled key is in the property source and is the likely cause, the failure should mention `Did you mean '...pattern.pattern'?` / `'logging.route.a.appenders'`. Unread-key checking runs only after a *successful* start, so there is no hint exactly when the typo breaks startup.
- **Actual**:
  ```
  ...ProvisionException: Validation failed for ...PatternEncoderBuilder:
  Property missing. key: 'logging.appender.console.encoder.pattern.pattern' from: SYSTEM_PROPERTIES[...], [logging.appender.console.encoder.uri]->URI(pattern:///)[pattern]
  ```
  and for the route: `PropertyMissingException: Property missing. key: ['logging.route.a.appenders' from SYSTEM_PROPERTIES[logging.route.a.appenders]]`, with a stack trace.
  `grep -ci mean` on the output is 0. The user sees only "missing" while their typo'd key sits in the same property source.
- **Also**: the doc says "setting a pattern on an appender is often all that is needed" (Selecting and Configuring the Pattern Encoder). With `encoder.uri=pattern` and no pattern, startup fails, which is reasonable but not stated.

#### 3. DOC-UNCLEAR / improvement (medium): misspelled level keys get no suggestion and cannot fail startup
- **Doc section**: "Misspelled and unused property keys": "Some keys are never reported: `logging.level` keys (and route level keys)...".
- **Steps**: `app/run.sh -Dlogging.debug=help -Dlogging.leveel.com.foo=debug` and `... -Dlogging.levels.com.foo=debug`.
- **Actual**: exit 0, only `[INFO] ... Property key 'logging.leveel.com.foo' ... was set but not read during startup.` with no suggestion. `-Dlogging.alerts.fail=warning` with `-Dlogging.debug=help` still starts. `logging.level.*` is probably the most-typo'd family (`logging.levels.`, `logging.loglevel.`, `logging.level:` etc.). The limitation is documented, but the doc does not say that `logging.leveel.*` and `logging.levels.*` are likewise never warnings. The user has no way to opt into catching these other than `unusedKey=warning`, which flags every INFO key.
- A related case: `-Dlogging.level.com.foo=LOUD` (invalid level for a logger that is never created) is silently accepted. `-Dlogging.level=LOUD` is reported (finding 5).

#### 4. DOC-UNCLEAR (medium): `logging.alerts.unusedKey=warning|error` alone records nothing, and `logging.debug` in `logging.properties` is silently ignored
- **Doc section**: Misspelled and unused property keys / Failing on alerts. The doc says "`warning` ... reports every unread key as a warning", but also "Unless debug mode is `all` or `help`, or `logging.alerts.fail` would fail on a misspelled key, nothing is recorded".
- **Steps**: `app/cfg/a/logging.properties` has typos (`ttll.keyvalues`, `ttll.colour`, `logging.apenders.foo`, `logging.zzzzzzz`). `app/run3.sh a -Dlogging.alerts.unusedKey=warning` prints nothing and exits 0. `-Dlogging.alerts.fail=warning` fails on 2 typo keys only. Adding `-Dlogging.alerts.unusedKey=warning` makes it fail on all 4.
- **Expected/Actual**: `unusedKey` alone being a no-op is derivable from the doc, but a user setting only `unusedKey=warning` and seeing nothing will assume there are no stray keys. A one-line "has no effect on its own" would help.
- **Also**: `logging.debug=help` placed in `logging.properties` (`app/cfg/b`) is silently ignored (exit 0, no report, no warning). The doc states this ("cannot be set in `logging.properties`"), but a `logging.debug` key in the file is not flagged, even with `-Dlogging.alerts.fail=warning`, since the "read before properties load" keys are exempt. Silent is hostile here; a WARN would be better.
- **Also**: `-Dlogging.simpleprops.strict=fail` as a system property is silently ignored. Per doc it is for the base resource, but nothing says that other sources are ignored.

#### 5. ERROR-MESSAGE (low): invalid `logging.level` message names the logger `''`
- **Steps**: `app/run.sh -Dlogging.level=LOUD` (also with `-Dlogging.debug=help`).
- **Actual**: `[ERROR] - RAINBOW_GUM - AlertingLevelConfig - Failed to resolve level for logger '', falling back to INFO ...PropertyConvertException: Error for property. key: 'logging.level' ..., 'LOUD' is not a valid value for java.lang.System.Logger.Level. Valid values: 'all', 'trace', ...`
- The tail is helpful (key, source, valid values). The head "logger ''" is confusing for the root level; "the root level" would be clearer. The same alert block is printed twice (immediate and backlog dump) plus three nested `Caused by:` lines for one problem. In default mode the app continues at INFO, which is reasonable and documented by the message.

#### 6. DOC-UNCLEAR (low): Reporting section lists only 4 sections; there are 6
- **Doc**: "controls which sections are printed (`VERSION`, `COMPONENTS`, `METRICS`, `ALERTS`)".
- **Actual (Javadoc `LogReporter.Section`)**: also `LOGGERS` (with `loggerLevels(boolean)`) and `FACADES`. Neither is mentioned in overview.md.

#### 7. DOC-UNCLEAR (low): doc snippets omit imports for `@Nullable`
- **Doc section**: SLF4J based filtering. The decorator example uses `@Nullable Marker`. There is no import and no hint which `@Nullable`. I used `org.jspecify.annotations.Nullable`, which required adding a `jspecify` dependency (it is not on the transitive classpath of `rainbowgum`). With that, the example compiles and works verbatim.
- The `Level` in `isEnabled(Level, ...)` is `org.slf4j.event.Level`, easily confused with `System.Logger.Level` used in the router example.

#### 8. DOC-UNCLEAR (low): decorator behaviour caveats are only in the Javadoc
- The overview example is described as "surfaces any Marker as a key value". Verified: `info(Marker, msg)` gives `{marker=M2}` (with TTLL, `-Dlogging.pattern.disable=true`), but `atInfo().addMarker(m).log(..)` does not (the `AbstractFilteringLogger` Javadoc says the fluent API bypasses the hooks; the overview does not).
- Also, the sampling example makes `isDebugEnabled()` false for the logger. I hit this myself: I saw `debugEnabled=false` and wasted time debugging "level not applied". That follows from the code but is not mentioned.

#### 9. NIT (low): report content
- `Properties: SYSTEM_PROPERTIES, SYSTEM_PROPERTIES, ENV[RAINBOWGUM_], SIMPLE_PROPS[classpath:/logging.properties]`: `SYSTEM_PROPERTIES` is listed twice. The "Tried:" lists in errors also show `SYSTEM_PROPERTIES[logging.alerts.fail]` twice:
  ```
  'logging.alerts.fail' from:
      SYSTEM_PROPERTIES[logging.alerts.fail],
      SYSTEM_PROPERTIES[logging.alerts.fail],
      ENV[RAINBOWGUM_alerts_fail],
  ```
- The encoder line shows no effective settings (`description="ttll color=default theme=rainbowgum colored=false"` is the same whether or not `keyValues=none` etc. were set). A user checking "did my property apply?" in the report cannot tell. The doc claims the report "shows ... which appenders, encoders, and outputs were actually chosen"; this is true for the type, not the settings.
- The `Alerts (total=10, capacity=512)` section lists only the last 5 entries with no "showing last N of 10" note.
- With a custom router (the Filtering example with `RouterFactory.of(...)`), the report shows `Router: ` (blank name) and `(no appender information available for DefaultSyncLogPublisher)`; the component tree the doc promises is missing for such setups.
- The doc example block does not show the extra lines (header, `Global Properties:` block of 7 `(unset)` lines) the real report prints.

#### 10. NIT (low): `[WARN]`/`[WARNING]` and long duplicated failure output
- A failed `help`/`fail` startup prints the alert twice (once as `[WARN] - RAINBOW_GUM - ...`, once inside the `IllegalStateException`), and again in the `Caused by:` of the `ExceptionInInitializerError`, i.e. 3 to 4 copies. The doc acknowledges and shows the first two. Fine, but noisy with many typos (the `fail=warning` run with 4 keys prints 12+ long lines).

### What passed
- `-Dlogging.debug=help -Dlogging.appender.console.encoder.ttll.levle=plain` verbatim, **without** the pattern module: exact documented behaviour (WARN with `Did you mean 'logging.appender.console.encoder.ttll.level'?`, `IllegalStateException: 1 alert(s) at warning or above ... logging.debug=help`, cause printed on its own before the stack trace).
- Key-similarity rules: `levle`, `apenders`, `bufferSze`, `strem` (distance 2), `KEYVALUES` / `keyvalues` / `key-values` (case, `-`) suggest the right key, in system properties and in `logging.properties` (reported with file and line `SIMPLE_PROPS[classpath:/logging.properties:3]`).
- Unprefixed keys in `logging.properties` (`apenders=x`) are reported as `logging.apenders ... Did you mean 'logging.appenders'?`.
- Other-component keys (pattern key under json5, `batchswap.*` under `async` publisher, `layout`, `console.pattern`, `logging.pattern`) are INFO only, never fail with default `unusedKey`.
- `logging.alerts.fail=off|error|warning|warn|true|false|WARNING`: exit codes as documented. `error` does not fail on warn-level typos. Works from system property, `logging.properties`, profile file (`-Dlogging.profiles=ci`) and env var `RAINBOWGUM_alerts_fail`.
- `unusedKey=warning|error` plus `fail` at the same level fails on all unread keys. `unusedKey=error` plus `fail=warning` also fails. `fail=error` plus `unusedKey=error` fails (4 alerts).
- `logging.alerts.unusedKey` / `fail` with an invalid value: error lists valid values (`'off','error','warning','true','false','warn'`) and the sources tried.
- Env var keys are not checked (verified: `RAINBOWGUM_level_roott=debug` is not reported). `logging.level.*` keys are not reported (valid ones).
- Debug modes: `off`, `false` (silent), `true` (= ALL, prints `Debug mode: ALL`), `info` (dump after build), `error` (silent on success), `all` (report + INFO unread keys, exit 0), `help`. Invalid `logging.debug=bogus` gives a helpful error with valid values.
- `logging.alerts.unobservedErrorsAction=none|dump|fail`: behaves as in the Javadoc. `bogus` is rejected with valid values.
- Misspelled `logging.alerts.unobservedErrorAction` gets `Did you mean 'logging.alerts.unobservedErrorsAction'?`.
- Helpful errors: unknown encoder, output or publisher scheme (`No encoder found. Scheme not registered. scheme: 'bogus'`), missing module (`Scheme 'file' is provided by module 'io.jstach.rainbowgum.file' (Maven: ...:rainbowgum-file:1.0.0-SNAPSHOT). Add that dependency.`), invalid enum values (`ttll.keyValues=bad`, `json5.prettyPrint=spacin`, `stdout.stream=bogus`, appender `type`/`flags`, `simpleprops.strict`), invalid number (`bufferSize=abc`), missing profile resource and bad profile name, `logging.appenders=console,,x` (invalid appender name), unconfigured appender (`missing`), `logging.appenders=file` without `logging.file.name`, java.util.logging-style `logging.properties` rejected with "looks like java.util.logging configuration".
- `simpleprops.strict=fail|alert|off`: errors name key, file and line.
- Alerts API: snippet `subscribeToAlerts` verbatim works (listener notified for warn, error and info; `close()` unsubscribes). `alerts().stats()` and `dump()` OK; ring buffer capacity 512.
- Metrics API: snippet `reportMetrics` verbatim works. All 8 `StandardMetric` constants exist with names and levels (`events.dropped`, `events.queued` gauge, `buffer.trimmed`, `events.failed`, `logger.names`, `reopen.fail`, `reopen`, `roll.fail`). Per-logger-name alert counters increment (`qa.Api` ERROR/WARNING/INFO). `errorCounter`/`warnCounter`/`infoCounter` and `gauge` increment/decrement work; DEBUG and OFF gauges are not registered. `events.queued` goes to `bufferSize` (100) when a slow consumer (blocked stdout pipe) backs up an `async` publisher.
- `LogReporter` snippet verbatim works for all 4 sections.
- Event-based filtering example works verbatim (only DEBUG events delivered once DEBUG is enabled for the loggers).
- SLF4J decorator example works verbatim (after adding the `@Nullable` import): keeps every 10th DEBUG and adds the marker key value.

### Not tested
- Spring Boot, Helidon, Micronaut, Avaje config, Listable custom property sources (none installed or out of scope).
- `events.dropped`, `events.failed`, `buffer.trimmed`, `reopen*`, `roll.fail` increments: I did not build an output that triggers them (needs a custom `LogOutput`/rolling files; out of area). Only `events.queued`, `logger.names` and alert counters were exercised.
- "Closes components before failing" after a failed start: not observable from outside.
- `ttll.*` / other doc config combinations beyond what produces alerts.
- One unreproduced anomaly: a single run of `-Dlogging.debug=help -Dlogging.appender.console.output.uri=stdout -Dlogging.appender.console.output.stdout.strem=follow` (through a `| head` pipeline) reported `exit=143` (process killed by the 60 s `timeout`). It did not reproduce in 5 further runs (immediate exit 1 with the documented WARN), so I treat it as a harness or pipe artefact, but it is worth a glance if anyone sees a hang in `help` mode.

---

# Agent report: a5-simple-props-facades

Area: Simple logging and properties (rainbowgum-simple / simple-props), SLF4J 2.0, System.Logger/JUL, Scoped Key Values, Default Key Values.
Setup: Maven project `app/` (Java release 25, deps `rainbowgum-simple` runtime, `slf4j-api`, scopedkeyvalues API + provider, keyvalues-contributor, all 1.0.0-SNAPSHOT), run with `app/run.sh <resource-dir> [-D...] -- <mode>` (java -cp target/classes:<resdir>:<dep classpath>). Test mains: `app/src/main/java/qa/*.java`. I did not run `java -jar` with a shaded jar (classpath runs only).

### Test plan
1. Doc examples verbatim: short keys file, profiles, env vars, strict modes.
2. Precedence: sysprop > env > profile > base file; profile source order; default profile.
3. Level file: startup, runtime change within 60s, g+w / o+w / dir g+w refusal at start and at runtime, recovery, symlinks, path, bad values.
4. SLF4J MDC / fluent / null handling, NOOP MDC; ScopedKeyValues; Default Key Values + disabled.
5. System.Logger / JUL routing, early-queue behavior, error cases.

### 1. Summary
| Label | Count |
|---|---|
| BUG | 1 |
| DOC-WRONG | 2 |
| DOC-UNCLEAR | 4 |
| ERROR-MESSAGE | 1 |
| NIT | 3 |

Overall: the core of this area works well and error messages for bad enum values, bad profiles, strict mode and permission refusals are generally specific and actionable. The level-file permission checks and recovery, profile ordering, strict modes, MDC/fluent/scoped/default key-value precedence all behave as documented. The most serious problems are documentation-versus-behaviour mismatches: the doc's own example for env vars (`logging.level.root` -> `RAINBOWGUM_level_root`) does not work (the root key is `logging.level`), the level file does NOT pick up changes at runtime unless `logging.change.<name>=level` is configured although the Javadoc says changes are allowed by default, and the whole level-file feature is absent from overview.md (only in Javadoc), as is the level file layer in the "Loading order" list. I'd call this area releasable only after those docs (or the level-file default) are fixed.

### 2. Findings

#### F1 – BUG/DOC-WRONG (high): level file changes are not applied at runtime by default
- **Label**: BUG (or DOC-WRONG if intended; one of the two must change)
- **Severity**: high
- **Doc section**: Javadoc `SimpleProperties.LevelFileType.WATCH`
- **What the doc says**: "Polls the level file ... Its logging.level keys take precedence over every other source, and level changes are allowed unless logging.global.change is set otherwise."
- **Steps**:
  `res/logging.properties`:
  ```
  logging.simpleprops.levelFile=watch
  level.com.myco=info
  ```
  `work/level.properties` (chmod 644, dir 755): `level.com.myco=info`. From `work/` run a loop app logging debug+info every 1s on logger `com.myco.App` (`app/run.sh <res> -- loop 40`); after 4s, `echo level.com.myco=debug > level.properties`.
- **Expected**: DEBUG lines appear within a few seconds (changes "allowed" by default).
- **Actual**: no DEBUG line even after 40 s. Initial values in the level file at startup ARE honored (level file beats logging.properties). Matrix (loop for ~12 s, count of DEBUG lines after the change):
  - nothing extra: 0 (also waited 40 s: 0)
  - `logging.global.change=true` only: 0
  - `logging.global.change=false`: 0
  - `logging.change.com.myco=level` only: works (~1 s latency)
  - `logging.change=level`: works
  So the behavior needed is `logging.change.<prefix>=level` (documented in "Changing Levels at Runtime"), not the absence of `logging.global.change=false`. A user following the Javadoc sees a silent no-op: no alert, no warning. At minimum a WARN alert "level file watched but level changing not enabled for any logger; set logging.change.<name>=level" would help.

#### F2 – DOC-WRONG (high): env var example `logging.level.root` / `RAINBOWGUM_level_root` does not set the root level
- **Doc section**: Simple logging and properties > rainbowgum-simple-props > Loading order
- **What the doc says**: "`RAINBOWGUM_`-prefixed environment variables. For example, `logging.level.root` maps to `RAINBOWGUM_level_root`."
- **Steps**: `r3/logging.properties`: `level.root=info`. Run `RAINBOWGUM_level_root=trace app/run.sh r3 -- basic` (logs t/d/i/w/e on `com.myco.App`). Also `-Dlogging.level.root=trace`, and file `level.root=debug` with no other settings.
- **Expected**: root level changes (as in Logback/Spring where `logging.level.root` is the root).
- **Actual**: no effect; output starts at INFO, no warning or error. The root key is `logging.level` (no `.root`): `RAINBOWGUM_level=trace` and `-Dlogging.level=trace` both work (TRACE shown). `logging.level.root` is treated as a logger named `root` silently. The doc's example should say `logging.level` -> `RAINBOWGUM_level`. Spring Boot users (the doc pitches Spring property compat) will hit this too; worth a note in the doc.

#### F3 – DOC-UNCLEAR (medium): level file is not documented in overview.md; Loading order list omits it
- **Doc section**: "Loading order"
- **What the doc says**: the list is "1. System properties. 2. RAINBOWGUM_ env ... 3. profiles 4. base logging.properties". The Javadoc of `SimpleProperties` says the optional watched level file is the highest layer. `logging.simpleprops.levelFile`, `levelFilePath`, the permission rules, the poll interval, and the need for `logging.change.*` appear nowhere in overview.md (grep confirms). Poll interval is not documented anywhere (observed latency <= ~1 s).
- Also undocumented in the user guide: level file only works when the base `logging.properties` exists (Javadoc says so; verified: `-Dlogging.simpleprops.levelFile=true` with no base resource is silently ignored), and `levelFile` set in a profile resource is ignored (verified).

#### F4 – DOC-UNCLEAR (medium): pure System.Logger/JUL app with rainbowgum-simple loses logs silently
- **Doc section**: "java.lang.System.Logger and java.util.logging"
- **What the doc says**: "The integration will make sure that neither the System.Logger or java.util.logging initialize Rainbow Gum too early by queueing the events if some other facade is detected like SLF4J ... The exception ... if no other facade is detected the module will initialize Rainbow Gum." And for rainbowgum-simple: "Adding it activates logging without application code".
- **Steps**: classpath of `rainbowgum-simple` (which includes slf4j) + app that only calls `System.getLogger(..).log(INFO,..)` and `java.util.logging.Logger.info(..)` and never touches SLF4J (`qa.JdkOnly`): `java -cp target/classes:r2:$(cat cp.txt) qa.JdkOnly`
- **Actual**: the System.Logger line is not printed at all (queued forever), JUL lines go to the JDK default console handler (`Oct 11, 2026 3:37:37 PM qa.JdkOnly main / INFO: jul info`). With only core+jdk(+systemlogger,jul) on the classpath all go through Rainbow Gum correctly (`19:37:37.505 [main] INFO  com.myco.Sys - system logger info`).
- Consistent with the text, but surprising for the "simple bundle" promise since the bundle always contains slf4j. Document it, or have the simple bundle initialize eagerly.
- Related: events logged via JUL *before* Rainbow Gum initializes are NOT queued/replayed (the doc sentence reads as if JUL events are queued too). `qa.Early`: JUL "EARLY jul info" printed by default JUL formatter, then lost to Rainbow Gum; System.Logger early events (INFO and ERROR) ARE replayed, and the ERROR is also printed to System.err as documented (`[ERROR] - RAINBOW_GUM - com.myco.Sys - EARLY system logger ERROR`).

#### F5 – DOC-UNCLEAR (low): names accepted by `logging.keyvalues.disabled` not listed
- **Doc section**: Default Key Values: "that property takes any of the key values source names" – the names are never given.
- Guessing `mdc` and `scoped` fail with an exception; the valid names appear only in the error: `Valid values: 'defaults', 'opentelemetry', 'scoped_key_values', 'jboss_logging', 'log4j2', 'slf4j', 'user'`. `slf4j` removed the MDC values only (fluent `addKeyValue` still shown); `user` had no visible effect in my test, so its meaning is unclear.

#### F6 – ERROR-MESSAGE (low): startup failures are raw stack traces with "Failed to instantiate SLF4J LoggerFactory"
- Example: `-Dlogging.profiles=nope` →
  ```
  SLF4J(E): Failed to instantiate SLF4J LoggerFactory
  io.jstach.rainbowgum.LogProperty$ValidationException: ... Error for property. key: 'logging.profiles' from SYSTEM_PROPERTIES[logging.profiles], Missing classpath resource 'classpath:/logging-nope.properties' for selected profile 'nope'
  Tried: 'logging.profiles' from SYSTEM_PROPERTIES[logging.profiles], ENV[RAINBOWGUM_profiles]
  ```
  The message itself is good (names key, source, resource). Slight improvement: say "create logging-nope.properties or remove 'nope' from logging.profiles". Same for invalid name `a.b` (good message: "use only ASCII letters, digits, underscores, and hyphens"), and strict=fail ("Property key should start with: 'logging.'. key: 'level.com.myco' from ...:2" – good, location included; add the fix "use logging.level.com.myco").
- `-Dlogging.global.queue.level=bogus` produces a raw `ExceptionInInitializerError` in the main thread with the useful message only in the 2nd "Caused by" (good message, poor presentation).

#### F7 – DOC-WRONG (low): Javadoc/overview vs observed alert printing
- Doc: "Informational alerts are retained without printing to stderr by default."
- Actual: as soon as any ERROR alert occurs (strict=alert, refused level file) all backlog INFO alerts are dumped to stderr: `[ERROR] - RAINBOW_GUM - DefaultLogAlerts - 10 alert(s) were recorded before any LogAlerts.Listener was registered - dumping the backlog now ...` followed by ~10 INFO lines, and the ERROR itself is printed twice (once, then again inside the dump). Probably intended, but the doc statement is incomplete; the duplicated ERROR is a NIT. Also the dump contains "Watching level file: ..." INFO even though the same dump says "Refusing level file ..." (contradictory, NIT).

#### F8 – NIT (low): `logging.simpleprops.strict` short key / sysprop silently ignored
- `strict=fail` in logging.properties (without prefix) is silently ignored (documented "always written with its full key", but a typo like `logging.simpleprops.strcit=fail` is also silently accepted; only `-Dlogging.debug=help` reports unread keys). `-Dlogging.simpleprops.strict=fail` as system property is ignored (doc lists resource/builder only). Fine per docs but easy to trip over.

#### F9 – NIT (low): Default Key Values parsing of malformed input silent
- `-Dlogging.keyvalues.contributor.defaults='novalue&a=b=c&=x'` → only `a=b%3Dc` appears; `novalue` and `=x` silently dropped, no alert. Doc doesn't state the format rules (`=` inside value, escaping, empty).
- With `rainbowgum-keyvalues-contributor` missing the property is silently unused (only reported with `-Dlogging.debug=help`: "was set but not read during startup") – acceptable, documented in Troubleshooting.

#### F10 – NIT (low): `logging.mdc.type` valid values
- Doc says `NOOP`; error lists `'thread_local', 'noop'` (case-insensitive works). Doc doesn't name the other value `thread_local`.

#### F11 – DOC-UNCLEAR (low): `rainbowgum-systemlogger` alone vs doc
- With only core+systemlogger the System.Logger calls go to the JDK default (JUL) console rather than Rainbow Gum (core+jul likewise). The doc says depend on `rainbowgum-jdk`; behavior is consistent but the doc doesn't say that the individual modules do nothing alone for System.Logger (only JUL is called out).

### 3. What passed
- No `logging.properties`: simple bundle logs INFO+ to stdout with TTLL default format.
- Empty `logging.properties` works.
- Short keys without `logging.` (`level.com.myco=debug`, `appenders=console`) work; mixed with prefixed keys later line wins (both orders verified).
- Sysprop beats env var beats file; env var `RAINBOWGUM_level_com_myco` works; `RAINBOWGUM_level` works; sysprops/env ignored when no base resource (as documented).
- Profiles: `-Dlogging.profiles=a,b` first listed wins; `RAINBOWGUM_profiles` works; sysprop beats env; explicit empty sysprop replaces whole list and falls back to `default` profile; `logging-default.properties` loaded when none selected and not when others selected, loaded with `dev,default`; missing profile file fails with clear message; bad profile name fails with clear message; profile without base file silently ignored.
- Strict: `short` (default) detects `handlers=` / `.level` JUL-style keys with line numbers; `fail`/`true` throws with line numbers; `alert` logs error and continues; `off`/`false` ignore; invalid value lists valid values.
- Level file: honored at startup; overrides logging.properties; custom `levelFilePath` (absolute); symlink followed; dangling symlink refused with message; symlink to group-writable directory refused naming that directory; non-level keys ignored with a WARN naming file:line; invalid level value gives readable error and falls back to INFO; `levelFile` bad value lists valid values; file g+w/o+w/dir g+w refused at startup with `chmod go-w` hint; missing file silently OK.
- Level file runtime (with `logging.change.com.myco=level`): change detected within ~1s; chmod g+w during run → "Refusing level file" error, previous level retained, changes ignored; chmod g-w → file contents applied again (recovery) and later edits applied.
- SLF4J: MDC output, fluent `addKeyValue`, overlay on MDC (fluent wins), `addArgument`, `setCause`, markers accepted, null MDC value accepted, `putCloseable`, NOOP MDC (`logging.mdc.type=NOOP`) drops MDC but keeps fluent kv, bad value rejected with valid-values list.
- ScopedKeyValues (JDK 25+ runtime): values on every line inside `run`, gone after; MDC overrides scoped; fluent overrides scoped; without the provider module the body runs with nothing attached.
- Default Key Values from sysprop and from logging.properties; lower precedence than MDC/scoped/fluent; `logging.keyvalues.disabled=defaults` removes them; `scoped_key_values` disables scoped.
- System.Logger and JUL through Rainbow Gum (with core+jdk): levels (incl. FINE/DEBUG with configured level), exceptions, parameters, `logging.jul.disable=true` bypasses bridge; early System.Logger events replayed; early ERROR printed to System.err; invalid `logging.systemlogger.initialize` gives valid values `'false','true','check','reuse','blank'`.

### 4. Not tested
- `java -jar` with a shaded jar and native-image metadata (classpath runs only).
- File owner-not-user/root permission check (cannot chown without root).
- `SimpleProperties.builder()` programmatic API (profiles/strict/resource/envPrefix) and custom resource names.
- `logging.global.queue.level/error` valid-value behavior beyond bad input; `systemlogger.initialize` options other than the invalid value; `RainbowGumLogManager`.
- Changing Logger vs Level Logger performance claims; Marker decorators; StructuredTaskScope fork inheritance.
- Process note: during cleanup I ran `pkill`/`kill` on all `java` processes by name once, which may have terminated other java processes running on this shared machine (including one with `-Dlogging.appender.console.output.uri=stdout` that was not mine).

---

# Agent report: a6-framework-integrations

Environment: Java 27, Maven, network to Maven Central available. `native-image` is not on the PATH.
Frameworks used: Spring Boot 4.1.0 and 3.3.3, Helidon 4.2.4 and 27.0.0, Micronaut 5.2.2, log4j-api 2.24.3, jboss-logging 3.6.1.Final, commons-logging 1.3.0 to 1.3.6, OpenTelemetry API 1.49.0.
All projects are in sibling directories of this file: `sb4`, `sb3`, `sb4x`, `sb4a`, `hel4`, `hel27`, `mn`, `bridges`, `otlp`, `ext`.

### Test plan
1. Spring Boot 4 and 3: docs pom verbatim, `java -jar`, `application.properties` keys (levels, file, pattern, structured), invalid/typo keys, and a run without the logback exclusion.
2. Spring Boot 4 actuator module (info, metrics, loggers).
3. Bridges: log4j2 (`rainbowgum-log4j2`), JBoss Logging (with and without the module), Commons Logging (1.3.x with SLF4J; with the Log4j API).
4. Helidon 4 and 27, Micronaut 5 (`logger.levels.*`, `/loggers`).
5. OTLP/HTTP output (local Python receiver: protobuf, JSON, gzip, headers, bad values) and the OpenTelemetry trace-id contributor.
6. Extension development guide: custom `LogOutput`, `Configurator`, and `@LogConfigurable` builder with `rainbowgum-apt`.
7. GraalVM: not testable.

### 1. Summary

| Label | Count |
|---|---|
| BUG | 3 (2 medium, 1 low) |
| DOC-WRONG | 2 (1 medium, 1 low) |
| DOC-UNCLEAR | 3 (1 medium, 2 low) |
| ERROR-MESSAGE | 3 (all low) |
| NIT | 2 |

Overall: the integrations mostly work as documented. Spring Boot 3 and 4, Helidon 4 and 27, Micronaut 5, and the log4j2, JBoss and JCL bridges all route framework and application logs through Rainbow Gum. Levels, file output, patterns, ECS structured output, OTLP export (protobuf and JSON, gzip, headers, trace context) and the custom extension all worked.

The Spring Boot integration is not yet a clean "drop-in replacement for Logback". Every Spring Boot startup that sets a `logging.level.*` key prints spurious `ERROR ... Cannot set level` lines, even though the levels are applied. `/actuator/loggers` returns HTTP 500. The extension guide's `pom.xml` does not produce a working build on JDK 23+ (the annotation processor never runs), and on any JDK the generated builder needs `org.jspecify`, which the guide does not mention.

I would fix F1, F2 and F3 before release.

### 2. Findings (most severe first)

#### F1. BUG, medium: spurious `ERROR Cannot set level` for every `logging.level.*` at Spring Boot startup
- **Doc section**: Getting Started > Using Spring Boot; Spring Boot.
- **What the doc says**: "Rainbow Gum natively understands almost all of Spring Boot's own documented logging properties (`logging.level.*`, ...) ... a genuinely **low-risk drop-in replacement for Logback**: same properties, same behavior". The Javadoc property table lists `logging.level.root`, `logging.level.<logger>` and groups as "Native" and does not mention any limitation.
- **Steps to reproduce**:
  - `sb4/pom.xml` is the docs' Boot 4 snippet verbatim, with `spring-boot-starter-parent` 4.1.0.
  - `application.properties`: `logging.level.demo=debug` and `logging.level.org.springframework.boot=info`.
  - Run: `cd sb4 && mvn -q package -DskipTests && timeout 30 java -jar target/sb4-1.jar`
- **Expected**: no errors. The levels are applied and nothing is logged at ERROR.
- **Actual** (the levels do take effect: `demo.App DEBUG` lines appear):
  ```
  ERROR 3958473 --- [main] o.s.b.c.l.LoggingApplicationListener : Cannot set level 'DEBUG' for 'demo'
  ERROR 3958473 --- [main] o.s.b.c.l.LoggingApplicationListener : Cannot set level 'INFO' for 'org.springframework.boot'
  ```
  - Same on Boot 3.3.3 (`sb3`).
  - With `--logging.level.root=warn` the message is `Cannot set level 'WARN' for 'null'`.
  - The errors make every startup look broken, and they would trip any alert on ERROR lines.
  - Cause appears to be that the `LoggingSystem` `setLogLevel` throws. F2 shows the related unsupported-operation failure.

#### F2. BUG, medium: Spring Boot Actuator `/actuator/loggers` returns HTTP 500; not documented as unsupported
- **Doc section**: Spring Boot > Actuator.
- **What the doc says**: the actuator module bridges metrics and info only. Nothing says `/actuator/loggers` is unsupported. The "drop-in replacement" claim implies it works.
- **Steps to reproduce**:
  - In `sb4a`: the Boot 4 starter, `rainbowgum-spring-boot4-actuator`, `spring-boot-starter-actuator`, `spring-boot-starter-micrometer-metrics`, with the logback exclusions.
  - `application.properties`: `management.endpoints.web.exposure.include=*`.
  - Run `java -jar target/sb4a-1.jar`, then `curl localhost:18081/actuator/loggers/demo`.
- **Expected**: logger configuration is returned, or a clear 4xx/501 plus a doc note.
- **Actual**: `{"status":500,"error":"Internal Server Error","path":"/actuator/loggers/demo"}`. The server log shows `java.lang.UnsupportedOperationException: Unable to get logger configuration`. A POST to change a level also returns 500.
- **Passed in the same app**: `/actuator/info` (with `management.info.rainbowgum.enabled=true`) returns the `rainbowgum` report, and `/actuator/metrics` lists the `rainbowgum.*` meters.
- **Note**: Micronaut's `/loggers` does work (see "What passed"), which makes the Spring gap more notable.

#### F3. DOC-WRONG, medium: Extension guide's pom does not generate builders on JDK 23+, and the generated builder needs jspecify
- **Doc section**: Extension development guide.
- **What the doc says**: the pom snippet has `rainbowgum-core` (compile) plus `rainbowgum-apt` (`provided`, `optional`). "Once the annotation processor is enabled one can create a properties aware builder with a single static factory method annotated with `LogConfigurable`."
- **Steps to reproduce**:
  - `ext/pom.xml` uses the doc snippet verbatim (annotation processor only as a `provided` dependency).
  - Add `demo/TagFactory.java`:
    ```java
    @LogConfigurable(name="TagOutputBuilder", prefix="logging.tag.{name}.")
    static TagOutput of(@KeyParameter String name, String label) { ... }
    ```
  - Reference `new TagOutputBuilder(name)` from another class and run `mvn -q package` on Java 27.
- **Expected**: `TagOutputBuilder` is generated.
- **Actual, step 1**: `cannot find symbol class TagOutputBuilder`. Nothing is generated, with no warning. The cause is that JDK 23+ no longer runs annotation processors found only on the classpath. `javac -processorpath <apt.jar>` does generate it. The guide should mention `-proc:full` (`<maven.compiler.proc>full</maven.compiler.proc>`) or `annotationProcessorPaths`.
- **Actual, step 2**: once the processor runs, compilation fails:
  ```
  target/generated-sources/annotations/demo/TagOutputBuilder.java:[43,52] package org.jspecify.annotations does not exist
  ```
  - The generated code uses `org.jspecify.annotations.Nullable`.
  - Neither `rainbowgum-core` nor `rainbowgum-annotation` brought jspecify onto my classpath.
  - Adding `org.jspecify:jspecify:1.0.0` (provided) fixes it. The guide does not mention this.

#### F4. DOC-UNCLEAR, medium: guide gives no working end-to-end extension example; I had to guess several API details
- **Doc section**: Extension development guide.
- **What the doc says**: "it is probably easiest to look at the other rainbow gum modules" (i.e. read source). There is no example of registering an output.
- **What I had to guess** (all resolved from the Javadoc):
  - A `Configurator` must implement `configure(LogConfig, Pass)`.
  - Registration is `config.outputRegistry().register("tag", ref -> LogProvider.of(...))`. The provider must return a `LogProvider<LogOutput>`, not the output.
  - The service file is `META-INF/services/io.jstach.rainbowgum.spi.RainbowGumServiceProvider`.
  - The builder is used with `new XBuilder(name).fromProperties(config.properties()).build()`. The property prefix convention for outputs is not documented.
- **Result**: once corrected, it works (see "What passed").
- **Suggestion**: add a 20-line example to the guide. The "you will need to make a module" statement in Installation did not turn out to be needed for classpath use.

#### F5. BUG, low: Commons Logging 1.3.0 to 1.3.5 routes `error(msg, Throwable)` to DEBUG when SLF4J (Rainbow Gum) is the backend
- **Doc section**: Commons Logging (jcl).
- **What the doc says**: Commons Logging "auto-detects `org.slf4j.Logger`... only since **1.3.0**. Action needed: Upgrade to 1.3.0 or later", and "Spring Framework 7.0.0 pins 1.3.5. Action: Nothing."
- **Steps to reproduce** (`bridges/`, jars in `lib/`):
  ```java
  var j = org.apache.commons.logging.LogFactory.getLog("jcl.test");
  j.error("jcl err w/ throwable", new RuntimeException("x"));
  ```
  - Run with `rainbowgum` and `rainbowgum-slf4j`, `commons-logging-1.3.5`, root level INFO, and no log4j-api on the classpath.
- **Expected**: an ERROR line with the stack trace.
- **Actual**:
  - Nothing is printed at the default INFO level.
  - With `-Dlogging.level.jcl=debug` the message appears as `DEBUG jcl.test - jcl err w/ throwable`.
  - Commons Logging 1.3.0 and 1.3.4 behave the same. 1.3.6 prints it as `ERROR` correctly.
  - `error("msg")` without a throwable, `fatal` and `warn(msg, t)` are fine.
  - With `log4j-api` also present, JCL picks the Log4j adapter and it works.
- **Assessment**: this looks like an upstream Commons Logging defect, not Rainbow Gum. Errors with exceptions are silently lost for any Boot 4 / Spring 7 app, which pins 1.3.5. The doc's "Nothing" and "1.3.0 or later" are therefore misleading. Please recommend 1.3.6+, or verify the cause.

#### F6. ERROR-MESSAGE, low: invalid level in Spring/SLF4J config prints a ~60-line stack trace
- **Doc section**: Config / Troubleshooting.
- **Steps to reproduce**: in `sb4`, `java -jar target/sb4-1.jar --logging.level.demo=loud`.
- **Actual**: the first output line is
  ```
  [ERROR] - RAINBOW_GUM - AlertingLevelConfig - Failed to resolve level for logger 'demo', falling back to INFO io.jstach.rainbowgum.LogProperty$PropertyConvertException: Error for property. key: 'logging.level.demo' ... 'loud' is not a valid value for java.lang.System.Logger.Level. Valid values: 'all', 'trace', 'debug', ...
  ```
  followed by about 60 internal stack frames.
- **Assessment**: the text is good (key, bad value, valid values). The trace is noise for a user typo.

#### F7. ERROR-MESSAGE, low: `otlp.endpoint=notaurl` gives "URI with undefined scheme"
- **Doc section**: OpenTelemetry > OTLP/HTTP Output.
- **Steps to reproduce**: `otlp/` project, `java -Dlogging.appender.otel.output.otlp.endpoint=notaurl -cp "target/classes:lib/*" demo.Main`
- **Actual**: `[ERROR] - RAINBOW_GUM - otlp.OtlpOutput - OTLP export to notaurl failed; dropped 1 log records java.lang.IllegalArgumentException: URI with undefined scheme`
- **Expected**: a startup validation error naming the property and expected form (`http://host:4318/v1/logs`), like `compression`, `protocol` and `maxBatchSize` do (see "What passed").
- **Actual behavior**: the bad value is only detected at the first export, and again on each batch.

#### F8. ERROR-MESSAGE, low: unreachable OTLP endpoint says nothing about the property or a fix
- **Actual**: `OTLP export to http://localhost:1/v1/logs failed; dropped 1 log records java.net.ConnectException: Connection refused (connect failed)`.
- **Assessment**: acceptable, since it includes the URL. The message could name the `otlp.endpoint` key.

#### F9. DOC-WRONG, low: "logback will be used otherwise" when `spring-boot-starter-logging` is not excluded
- **Doc section**: Spring Boot (pom comment).
- **What the doc says**: "This is the important part as logback will be used otherwise regardless if rainbow gum spring boot integration is used."
- **Steps to reproduce**: `sb4x` is the docs pom without the exclusion. Run `java -jar target/sb4-1.jar`.
- **Actual**:
  ```
  SLF4J(W): Class path contains multiple SLF4J providers.
  SLF4J(I): Actual provider is of type [io.jstach.rainbowgum.slf4j.RainbowGumSLF4JServiceProvider@...]
  ```
  Output looked like Rainbow Gum's.
- **Assessment**: which provider wins depends on classpath order, so the doc's claim is not reliable either way. The consequence is only a warning here.

#### F10. DOC-UNCLEAR, low: Spring/Boot keys silently ignored outside Spring; typo'd keys never reported
- **Doc section**: Config / Helidon / Troubleshooting.
- **Steps to reproduce**:
  - In `hel4` I wrote `logging.pattern.console=...` in `logging.properties`. It was silently ignored. The documented key is `logging.appender.console.encoder.pattern.pattern`, and it works.
  - In `sb4`, `--logging.lvel.root=debug` and `--logging.file.nam=/tmp/x.log` are silently ignored.
  - `--logging.structured.format.console=bogus` is silently ignored (the Javadoc says only an FQCN falls back silently).
  - In `otlp`, `otlp.endpiont` is silently ignored.
- **Expected**: the Troubleshooting section's unused-key detection is opt-in. The Helidon and Micronaut sections should say that only the `logging.appender.*` keys apply in `logging.properties`. They should also say Spring's `logging.pattern.*` is Spring-only.
- **Assessment**: the Boot Javadoc lists the Spring keys as supported, which invites the Helidon mistake.

#### F11. DOC-UNCLEAR, low: JBoss Logging and Log4j2 dependency snippets omit the version
- **Doc section**: JBoss Logging; Log4j2.
- **What the doc says**: `<dependency><groupId>io.jstach.rainbowgum</groupId><artifactId>rainbowgum-jboss-logging</artifactId></dependency>` with no `<version>`.
- **Actual**: copied verbatim, this fails to resolve (no BOM or `dependencyManagement` is described). The Maven snippets elsewhere include a version.
- **Note**: I added `1.0.0-SNAPSHOT` and it works. I did not run the verbatim snippet without a version; this is from reading the snippet, not a run.

#### F12. NIT: repeated lines in the property "Tried:" list
- Example (`ext`, unknown scheme):
  ```
  Tried:
      'logging.appender.console.output.uri' from:
          SYSTEM_PROPERTIES[logging.appender.console.output.uri],
          SYSTEM_PROPERTIES[logging.appender.console.output.uri],
          ENV[RAINBOWGUM_appender_console_output_uri],
  ```
- `SYSTEM_PROPERTIES` is listed twice. Otherwise this message is good.

#### F13. NIT: strict receivers reject a JSON `{}` 200 reply to an OTLP protobuf request
- **Steps to reproduce**: with the `otlp` project and my first receiver, which answered `200` with `Content-Type: application/json` and body `{}`.
- **Actual**: `[ERROR] - RAINBOW_GUM - otlp.OtlpOutput - Invalid OTLP response from http://localhost:18084/v1/logs java.lang.IllegalArgumentException: Unsupported protobuf wire type: 3` plus a stack trace.
- **Assessment**: arguably my receiver's fault. The error is correct but cryptic: it does not say that a protobuf response was expected. A 2xx response that is not a valid `ExportLogsServiceResponse` could arguably be treated as success, since the export already succeeded.

### 3. What passed
- Spring Boot 4.1.0 with the docs pom verbatim: boots, with Tomcat/Spring framework logs and app logs going through Rainbow Gum. (The F1 ERROR lines appear.)
- Spring Boot 3.3.3 with `rainbowgum-spring-boot3-starter`: same behavior.
- Spring `logging.level.demo=debug` / `logging.level.org.springframework.boot=info` are applied (the `demo.App` DEBUG lines appear).
- `--logging.level.root=warn` is applied.
- `--logging.level.demo=warn` suppresses INFO from the app.
- `logging.file.name` writes a file, and `logging.pattern.console` / `logging.pattern.file` are applied (`CON ...` / `FILE ...`; `%logger{0}` works).
- `logging.structured.format.console=ecs` with `logging.structured.ecs.service.name` produces ECS JSON including `service.name` and `service.version`.
- Boot 4 actuator: `/actuator/info` with `management.info.rainbowgum.enabled=true` returns the `rainbowgum` report. `/actuator/metrics` lists `rainbowgum.*` meters (events.dropped, events.queued, ...).
- log4j2 bridge (`rainbowgum-log4j2`): `LogManager.getFactory()` is `RainbowGumLoggerContextFactory`. Levels are honored per `logging.level.l4j=debug`. A throwable is rendered and `ThreadContext` shows up as key values.
- log4j2 API without `rainbowgum-log4j2`: the Log4j API's own "could not find a logging provider" message appears, as expected.
- JBoss Logging with `rainbowgum-jboss-logging`: `RainbowGumJBossLogger` is used, with levels, `debugf` and `errorf` working.
- JBoss Logging without the module: falls back to `JDKLogger` as documented. If SLF4J is touched first, it selects `Slf4jLogger`, matching the documented ordering caveat.
- Commons Logging 1.3.5 with SLF4J on the classpath selects `Slf4jLogFactory`. With `log4j-api` present it selects the Log4j adapter.
- Helidon SE 4.2.4 with the docs deps: Helidon logs, `System.Logger` and JUL all go through Rainbow Gum, and `logging.appender.console.encoder.pattern.pattern` is applied.
- Helidon SE 27.0.0 (`rainbowgum-helidon27`, Java 27): same behavior.
- Micronaut 5.2.2 with `rainbowgum-micronaut5` + `rainbowgum-simple-props`: `application.properties` `logger.levels.demo=DEBUG` and `logger.levels.io.netty=WARN` are applied ("Setting log level ..." lines, DEBUG from the app).
- Micronaut `/loggers`: `GET` shows configured levels. `POST` changes the level (200, `TRACE`), with `endpoints.loggers.write-sensitive=false`.
- OTLP/HTTP output (docs properties, local receiver): protobuf POST to `/v1/logs` has `service.name`, scope, severity and body.
- OTLP attributes: `thread.name`, `thread.id`, MDC key values and `exception.type`/`exception.message`/`exception.stacktrace` are all present.
- `rainbowgum-opentelemetry` adds `traceId`/`spanId` from the current span, and they appear in OTLP JSON.
- OTLP options `protocol=http/json`, `compression=gzip`, `headers=x-api-key=abc` and `resourceAttributes=deployment.environment=qa` are all applied (gzip body decoded as expected).
- Invalid OTLP values `protocol=grpc`, `compression=zstd` and `maxBatchSize=abc` fail at startup with a clear message naming the property and the valid values.
- Custom extension (after the F3 workarounds): a `Configurator` registers the `tag` output scheme, the `@LogConfigurable` builder gets `logging.tag.mine.label` from `logging.properties`, and output is `<XYZ> ... hello ext`.
- An unknown output scheme gives `No output found. Scheme not registered. scheme: 'nope'` with the failing key.

### 4. Not tested
- GraalVM Native Image (including `auto_detect` and `logging.global.optimize`): `native-image` is not on the PATH.
- Boot 3 actuator: the docs say it does not exist.
- Spring Boot features not exercised: rolling-policy keys, `logging.group.*`, `logging.charset.*`, `spring.output.ansi.enabled`, `debug`/`trace`, `logging.console.enabled`.
- JBoss Logging and Log4j2 `Marker` behavior: documented as ignored, not checked.
- OTLP retry/backoff on 429/5xx, `environmentVariables=on` / `OTEL_*`, `OtlpJsonEncoder`, and the `otlp.records.failed` metric.
- Helidon `LogConfig` ordering with an application config file.
- A Micronaut `ManagedLoggingSystem` check beyond `/loggers`, and running without `rainbowgum-simple-props`.
- Behavior of the verbatim version-less JBoss and Log4j2 dependency snippets (F11).
