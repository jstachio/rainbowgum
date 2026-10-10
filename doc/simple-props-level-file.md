# Watching a level file in simple props

Exploratory, branch `explore/simple-props-level-file`. Today only the avaje-config
integration changes log levels while running, because avaje-config watches its own
files. This adds the smallest useful version of that to `rainbowgum-simple-props`.

## What others do

Not verified against current releases; check before using any of this in user facing
docs.

| Framework | Built-in file watching | What reloads |
|---|---|---|
| Logback | `<configuration scan="true" scanPeriod="30 seconds">` | the whole `logback.xml` (and included files), fully reconfigured |
| Logback 1.5.x | `<propertiesConfigurator>` with scanning | a properties file of logger levels only (`logback.logger.<name>=LEVEL`) |
| Log4j2 | `monitorInterval` on `<Configuration>` | the whole configuration, rebuilt and swapped |
| JUL | none | `LogManager.readConfiguration()` or `updateConfiguration()`, called by the application |
| Spring Boot | none for logging | levels through the Actuator `/loggers` endpoint |
| Micronaut | none for logging | `/loggers` endpoint, configuration refresh |
| Quarkus | dev mode reload only | levels through an optional logging manager extension |
| avaje-config | polls its files | what Rainbow Gum's avaje module hooks into |

The frameworks that watch files mostly reload the whole configuration, which is where
their reload bugs live. Logback's newer levels-only properties file is the closest to
this design and suggests the minimal version is the useful part.

## Design

Decided with Adam: safe, minimal, easy.

- **Off by default.** Enabled with `logging.simpleprops.levelFile=watch` (`true` is an
  alias, `off` or `false` is the default), read from system properties, environment
  variables, or the base `logging.properties`, like the other simple props settings.
  Without a base `logging.properties` simple props supplies nothing, so nothing is
  watched.
- **One fixed file:** `level.properties` in the working directory (`user.dir`). It may
  be missing at startup and appear later.
- **Levels only.** Only `logging.level` and `logging.level.*` keys are read from it,
  since levels are the only thing core changes while running. They take precedence over
  every other source, system properties included, so an operator can change a level
  without a restart. Any other key in the file is ignored with a warning alert.
- **Turns on level changes.** Enabling the watcher supplies `logging.global.change=true`
  as a default, since the file can appear after startup. An explicit
  `logging.global.change` anywhere still wins.
- **Polling, not `WatchService`.** A daemon thread checks the file's modification time
  and size every few seconds. `WatchService` behaves differently across platforms and
  container volumes; one file check is cheap. The thread stops when the configuration
  closes.
- **Info alert when levels change.** Rewriting the file without changing any level
  publishes nothing and alerts nothing.

## Not included

A configurable file name or poll interval, route level keys, and anything other than
levels. Each can be added later if someone asks.
