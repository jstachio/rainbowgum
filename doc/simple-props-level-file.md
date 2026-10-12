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
- **One file:** `level.properties` in the working directory (`user.dir`) by default, or
  `logging.simpleprops.levelFilePath`. A relative path resolves against the working
  directory and `../` is allowed: the path comes from system properties, environment
  variables, or the base `logging.properties`, never from profiles or the level file
  itself, and whoever sets those already controls all of logging. (Not
  `levelFile.path`, since `levelFile` already has a value and YAML cannot nest under
  it.) The file may be missing at startup and appear later.
- **Refused while others can write it.** Whoever can write the file controls levels,
  and so what reaches the logs. Like ssh's StrictModes, where POSIX permissions are
  available the file is refused with an error alert while it or its directory is
  writable by group or others, or is owned by someone other than the user running the
  application or root. This matters most for the working directory default: an
  application started from `/tmp` refuses rather than trusting a world writable
  directory. A file that becomes refused while running keeps the levels last read from
  it, alerts once, and is read again once fixed. Symbolic links also have their resolved
  target's directory checked, and dangling links are refused.
- **Levels only.** Only `logging.level` and `logging.level.*` keys are read from it,
  since levels are the only thing core changes while running. Keys may leave off
  `logging.`, as in `logging.properties`: `level.com.myco=DEBUG` is
  `logging.level.com.myco=DEBUG`. They take precedence over
  every other source, system properties included, so an operator can change a level
  without a restart. Any other key in the file is ignored with a warning alert.
- **Turns on level changes.** Enabling the watcher supplies `logging.change.mode=on` and
  `logging.change.logger=level` as defaults, since the file can appear after startup. An
  explicit `logging.change.mode` or `logging.change.logger` anywhere still wins.
- **Polling, not `WatchService`.** A daemon thread rereads and parses the file every two
  seconds, comparing its level entries so quick edits with unchanged timestamps or size
  are detected. `WatchService` behaves differently across platforms and container
  volumes. The thread stops when Rainbow Gum closes or configuration construction fails.
- **Info alert when levels change.** Rewriting the file without changing any level
  publishes nothing and alerts nothing.

## Not included

A configurable poll interval, route level keys, and anything other than levels. Each can be added later if someone asks.
