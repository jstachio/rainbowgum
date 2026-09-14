# Rainbow Gum with Micronaut, Helidon, and Quarkus

Research notes on how Micronaut, Helidon, and Quarkus handle logging out of the
box, and what it would actually take to put Rainbow Gum underneath each one.
None of these three have a dedicated Rainbow Gum integration module today (unlike
Spring Boot, see `doc/overview.html`'s Spring Boot section). This document is
findings and a proposed approach, not a shipped, tested integration. Claims about
Rainbow Gum's own code are verified against this repository; claims about the
other frameworks are sourced from their own documentation (linked at the bottom
of each section) and should be re-checked against whatever version you're
actually running.

## Summary

| Framework | Own logging facade | Default backend | SLF4J is a first-class citizen? | Effort to put Rainbow Gum underneath |
| --- | --- | --- | --- | --- |
| Micronaut | SLF4J | Logback (`logback-classic`) | Yes, natively | Low: standard SLF4J provider swap |
| Helidon | `java.util.logging` (JUL) | JUL (`logging.properties`) | Optional, via a bridge | Medium (good enough) / High (first-class): SLF4J app code already works, but a correct swap of Helidon's own JUL calls needs a new custom `LogManager` (see below) |
| Quarkus | JBoss Logging, on JBoss Log Manager | JBoss Log Manager | Bridged in, not swappable without surgery | Medium/High: additive handler is safe, full replacement is experimental |

The common thread: Rainbow Gum's `rainbowgum-slf4j` module (a normal SLF4J
provider) and `rainbowgum-jul` module (a `java.util.logging.Handler`
subclass installed on the root JUL logger, not a custom `LogManager`,
confirmed by reading its source) are the two levers available today. Whether
that's enough, or whether a framework really wants a full `LogManager`
replacement instead, differs a lot per framework. Helidon turns out to want
the latter for a first-class integration, per the correction in its section
below.

## Micronaut

**Facade and default backend.** Micronaut applications and the framework's own
internals both log through plain SLF4J (`LoggerFactory.getLogger(...)`).
Projects generated with the Micronaut CLI/Launch include `ch.qos.logback:logback-classic`
plus a default `logback.xml` under `src/main/resources`. There's nothing
Micronaut-specific baked into the log calls themselves: it's the same SLF4J
provider-discovery mechanism any plain Java application uses.

**Swapping to Rainbow Gum.** Because it's a standard SLF4J setup, this is the
same recipe as any other SLF4J application:

```xml
<dependency>
  <groupId>ch.qos.logback</groupId>
  <artifactId>logback-classic</artifactId>
  <exclusions>
    <exclusion>
      <groupId>*</groupId>
      <artifactId>*</artifactId>
    </exclusion>
  </exclusions>
  <!-- or drop the dependency entirely if nothing else pulls it in -->
</dependency>
<dependency>
  <groupId>io.jstach.rainbowgum</groupId>
  <artifactId>rainbowgum</artifactId>
</dependency>
```

Delete or ignore `logback.xml` (harmless if left, since nothing binds to
Logback anymore once it's off the classpath) and configure Rainbow Gum the
normal way (System properties, environment variables, or a custom
`PropertiesProvider` (see below).

**What you lose.** Micronaut's management module exposes a `/loggers` endpoint
for reading/setting logger levels at runtime, backed by an
`io.micronaut.management.endpoint.loggers.LoggingSystem` bean. Micronaut ships
`LogbackLoggingSystem` and `Log4jLoggingSystem` implementations; there is no
generic SLF4J-based one. Swapping to Rainbow Gum without also registering a
custom `LoggingSystem` bean that delegates to Rainbow Gum's own
`LogConfig`/level-resolution API means `/loggers` has nothing to talk to. This
would be a small, genuinely useful bean to write (Rainbow Gum's
`LevelResolver`/`ChangePublisher` already expose everything the interface
needs) but does not exist today.

**What should keep working.** MDC propagation across Micronaut's reactive/
virtual-thread/coroutine context propagation operates at the SLF4J `MDCAdapter`
level, which Rainbow Gum implements (`rainbowgum-slf4j`), so this should
continue to work without any special handling; it isn't tied to Logback
internals.

**No Micronaut `Environment`/`application.yml` bridge exists yet.** Rainbow
Gum will not automatically read `logging.*` keys out of Micronaut's own
`application.yml`/`application.properties` just because they look similar in
shape. Out of the box Rainbow Gum reads System properties, `RAINBOWGUM_`-
prefixed environment variables, or its own properties file (see
`rainbowgum-simple-props`). A `PropertiesProvider` bridging Micronaut's
`Environment` into `LogProperties` is straightforward to write (see
"Bridging a framework's own config system" below) but nobody has written one.

Sources: [Micronaut Logging docs](https://docs.micronaut.io/5.1.x/logging/),
[`LoggingSystem` javadoc](https://docs.micronaut.io/1.2.0.RC2/api/io/micronaut/management/endpoint/loggers/LoggingSystem.html),
[`LogbackLoggingSystem` javadoc](https://docs.micronaut.io/2.2.3/api/io/micronaut/management/endpoint/loggers/impl/LogbackLoggingSystem.html).

## Helidon

Helidon ships two programming models, SE (Helidon's own APIs, no CDI) and MP
(MicroProfile, CDI via Weld). Both sit on the same logging plumbing described
here.

**Facade and default backend.** Unlike Micronaut, Helidon's own internal
framework logging is `java.util.logging` (JUL), not SLF4J. It's configured the
classic JUL way: a `logging.properties` file on the classpath or working
directory, or the `java.util.logging.config.file`/`java.util.logging.config.class`
system properties. Helidon SE docs recommend calling
`LogConfig.configureRuntime()` as the very first thing in `main()` so logging
is set up before anything else runs.

Helidon additionally has its own small SPI,
`io.helidon.logging.common.spi.LoggingProvider` (module `io.helidon.logging.common`,
discovered via `ServiceLoader`), with `JulProvider` (default), `Slf4jProvider`,
and `Log4jProvider` implementations. This is *not* what decides which backend
receives log output: it's what Helidon's own diagnostics/MDC glue
(`HelidonMdc`, trace-id propagation for access logs, etc.) targets. Adding
`helidon-logging-slf4j` tells that glue code to talk to SLF4J's `MDC` instead
of JUL's (nonexistent) equivalent.

**Putting Rainbow Gum underneath (correction).** An earlier version of this
document claimed `rainbowgum-jul`'s existing Handler-based bridge (confirmed
by reading its source: `SystemLoggerQueueJULHandler extends Handler`, not a
custom `LogManager`) would attach cleanly under Helidon since Helidon doesn't
replace the default `java.util.logging.LogManager`. That's true for Helidon's
*own* default `JulProvider` (confirmed: it only calls
`LogManager.getLogManager().readConfiguration(...)` on the stock LogManager,
never replaces it), but it's not what Helidon itself does when it wants a
*different* backend under its own JUL calls. Helidon's `helidon-logging-log4j`
module ships this native-image build argument
([source](https://github.com/helidon-io/helidon/blob/release-4.5.4/logging/log4j/src/main/resources/META-INF/native-image/io.helidon.logging/helidon-logging-log4j/native-image.properties)):

```
-Djava.util.logging.manager=org.apache.logging.log4j.jul.LogManager
```

That's a full `java.util.logging.LogManager` replacement (Log4j2's own
`log4j-jul` artifact), not a Handler attached after the fact. A LogManager
replacement is resolved on the very first `Logger.getLogger(...)` call
anywhere in the JVM, so `Logger` instances themselves are backed by the real
target system from the start: no early-boot messages missed before a bridge
Handler gets installed, and no separate step needed to keep a JUL Logger's own
cached level in sync with the real system's (often more granular) level
resolution. A Handler-based bridge, including Rainbow Gum's current
`rainbowgum-jul`, doesn't get either property for free.

For a genuinely first-class Helidon integration, Rainbow Gum would need the
same thing Log4j2 built: an actual `java.util.logging.LogManager` subclass,
installable via `-Djava.util.logging.manager=...`. That doesn't exist today -
this project previously considered and rejected a custom `LogManager` in
general (`rainbowgum-jul` stays Handler-based deliberately, see its own
history), specifically because of the early-initialization risk
`rainbowgum-jdk`'s module javadoc documents at length: a `LogManager` is
constructed the moment anything touches JUL, which can easily be before
Rainbow Gum itself is meant to initialize, and the JDK explicitly recommends
against heavy work in that constructor. Revisiting that decision for Helidon
specifically would need the same queue-and-replay approach `rainbowgum-jdk`
already uses for `System.LoggerFinder` (queue events until a real Rainbow Gum
is bound, replay them, print `ERROR`-and-above to `System.err` if one never
binds) applied to a `LogManager` implementation instead of just a
`LoggerFinder`: a real, nontrivial piece of new work, not a dependency swap.

The existing Handler-based `rainbowgum-jul` bridge still works as a "good
enough for most apps" fallback under Helidon (same mechanics as the Quarkus
additive-handler path below), it just won't have full early-boot fidelity or
automatic per-logger level sync the way a LogManager replacement would.

Sequencing matters regardless of which approach is used: call
`LogConfig.configureRuntime()` (or skip it if fully replacing Helidon's own
JUL setup) and get Rainbow Gum initialized as early as possible, the same
"initialize before anything else logs" concern Rainbow Gum already documents
for its own `System.Logger`/JUL early-init handling (see `rainbowgum-jdk`'s
module javadoc on queueing events until Rainbow Gum is bound).

**If you want Helidon's own MDC/context propagation to line up with Rainbow
Gum's key values**, also add `helidon-logging-slf4j` so `HelidonMdc` writes
through the real SLF4J `MDC` (which Rainbow Gum backs) instead of its JUL-only
path. This is an "if you want deeper integration" nice-to-have, not required
for basic log output to work.

Sources: [`LoggingProvider` javadoc](https://helidon.io/docs/v4/apidocs/io.helidon.logging.common/io/helidon/logging/common/spi/LoggingProvider.html),
[`LogConfig` javadoc](https://helidon.io/docs/v4/apidocs/io.helidon.logging.common/io/helidon/logging/common/LogConfig.html),
[Helidon, Logging, and MDC (Helidon team blog)](https://medium.com/helidon/helidon-logging-and-mdc-5de272cf085d).

## Quarkus

This is the hardest of the three, and the one where "just swap the SLF4J
provider" genuinely does not apply.

**Facade and default backend.** Quarkus uses JBoss Logging as its API and
**JBoss Log Manager** as the backend, and it isn't optional: Quarkus requires
`org.jboss.logmanager.LogManager` to be installed as the active
`java.util.logging.manager` before any other class touches JUL, since a JVM's
`LogManager` is resolved once on first touch and can't be swapped afterward.
This is normally handled for you by Quarkus's build/launch tooling (and needs
setting explicitly for `@QuarkusTest` in Maven/Gradle if it isn't already).

Quarkus also auto-bridges every other common logging API directly into JBoss
Log Manager via its own adapter artifacts, confirmed from Quarkus's own docs:

- SLF4J: `org.jboss.slf4j:slf4j-jboss-logmanager`
- Apache Commons Logging: `org.jboss.logging:commons-logging-jboss-logging`
- Log4j 2: `org.jboss.logmanager:log4j2-jboss-logmanager`
- `java.util.logging`: built in (JBoss Log Manager *is* a `LogManager`)

The important detail: `slf4j-jboss-logmanager` is itself an SLF4J provider
(`SLF4JServiceProvider`). It's on the classpath by default in every Quarkus
app, which means it's already occupying the one SLF4J-provider slot a JVM
resolves per run. Rainbow Gum's own SLF4J provider (`rainbowgum-slf4j`) would
be competing for that same slot.

**Two paths, with very different risk levels:**

*Path A: additive handler (documented, low risk).* Quarkus lets you attach
named handlers to the root logger or specific categories entirely through
`application.properties`, confirmed from Quarkus's own logging guide:

```properties
quarkus.log.handler.rainbowgum.enabled=true
quarkus.log.handlers=rainbowgum
```

paired with a CDI bean (or, for a purpose-built extension, a build step)
registering a `java.util.logging.Handler`/`org.jboss.logmanager.ExtHandler`
that forwards records into a Rainbow Gum `LogRouter`, the mirror image of
what `rainbowgum-jul`'s existing `SystemLoggerQueueJULHandler` already does
against the plain JUL root logger, just registered against JBoss Log
Manager's root logger instead. This receives *everything* (JBoss Logging,
SLF4J-via-the-bridge, JUL, Log4j2-via-the-bridge; all of it funnels through
JBoss Log Manager already) as an **additional** sink, without touching
Quarkus's own console/file output or fighting for the SLF4J provider slot.
Nobody has built this handler yet, but it only needs documented Quarkus
extension points.

*Path B: full replacement (undocumented, higher risk, not attempted).*
Exclude `org.jboss.slf4j:slf4j-jboss-logmanager` and put `rainbowgum-slf4j` in
its place, then bridge Quarkus's own JBoss-Logging-originated messages into
Rainbow Gum the same way as the Helidon case (`rainbowgum-jul`'s handler
attached to the JBoss Log Manager root logger, plausible since JBoss Log
Manager stays largely `java.util.logging.Handler`-API-compatible for this).
This is realistically JVM-mode only: Quarkus's native-image build has
build-time substitutions wired around JBoss Log Manager's specific class
shapes, and excluding its bundled SLF4J binding risks interfering with
whatever Quarkus's own build-time extension processing, dev-mode live-reload
log capture, or `@QuarkusTest` log capture assume about the logging pipeline.
This would be worth a dedicated `rainbowgum-quarkus` extension to actually
build and test properly rather than a drop-in Maven dependency swap; treat
it as an open question, not a recommendation.

Sources: [Quarkus Logging Configuration guide](https://quarkus.io/guides/logging/),
[Quarkus `logging.adoc` source](https://github.com/quarkusio/quarkus/blob/main/docs/src/main/asciidoc/logging.adoc).

## Bridging a framework's own config system

None of the three read Rainbow Gum's `logging.*` properties out of their own
config system (`application.yml`, MicroProfile Config, etc.) automatically -
Rainbow Gum has no idea those systems exist unless something tells it. The
blueprint for doing this already exists in this repository:
`rainbowgum-avaje-config`'s `AvajePropertiesProvider` implements
`RainbowGumServiceProvider.PropertiesProvider` (discovered via
`ServiceLoader`), wraps the target config system's own value lookup as a
`LogProperties`, and optionally implements `Configurator` too so config
changes call `LogConfig#changePublisher()#publish()` and get picked up by
Rainbow Gum's dynamic level-changing machinery. A Micronaut `Environment`,
Helidon `Config`, or MicroProfile `Config` equivalent would follow the exact
same shape. None of the three have one today.

## Bottom line

- **Micronaut**: drop-in SLF4J provider swap, same as Spring Boot without the
  starter module. The only real gap is the `/loggers` management endpoint.
- **Helidon**: default facade is JUL, not SLF4J, and application-level SLF4J
  code already works unmodified. Rainbow Gum's existing Handler-based
  `rainbowgum-jul` bridge is good enough for most apps, but Helidon's own
  `helidon-logging-log4j` module shows the officially-blessed way to fully
  replace Helidon's own JUL calls is a custom `java.util.logging.LogManager`,
  which Rainbow Gum doesn't have and would need to build.
- **Quarkus**: don't fight JBoss Log Manager. An additive handler using
  documented `quarkus.log.handlers` config is low-risk and captures
  everything; becoming the *sole* backend by excluding Quarkus's bundled SLF4J
  binding is an open, unverified, JVM-mode-only experiment that would want its
  own extension and its own testing before anyone recommends it.
