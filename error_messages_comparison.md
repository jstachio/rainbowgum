# Error Messages: Rainbow Gum vs Logback vs Log4j2

A companion to [why_rainbowgum_is_better.md](why_rainbowgum_is_better.md), split out on
its own for now since it's likely to grow scenario by scenario.

The methodology: take the *same* kind of mistake - a config value that fails to convert
to the type it needs to be - and see what each framework actually does. Every example
below was actually run against real config for each framework (versions: Logback 1.6.3,
Log4j2 3.0.0-beta2, Rainbow Gum 0.12.0-SNAPSHOT), not reconstructed from memory or
documentation. Rainbow Gum's own outputs are the exact golden strings its own test suite
asserts on (see `RollingFileOutputPropertiesTest`, `PatternEncoderCharsetTest`,
`LogstashEncoderTest`), and use Rainbow Gum's current root-cause-first message format: the
most specific failure is always the first line, with each layer that wrapped it listed
below as a `  ↳ ` line, deepest wrapping context last.

**The headline finding: Rainbow Gum is the only one of the three that fails the same way
every time.** Logback's behavior for a bad value ranges from "throws all the way up and
crashes the app on first logger use" to "silently disables the appender and never tells
you," depending on *which* property is wrong. Log4j2 is more consistent about not
crashing, but that consistency comes at the cost of frequently just ignoring the bad
value and carrying on with a default. Rainbow Gum always does the same thing: refuse to
start, with one message naming the exact property key, where it came from, and why it
failed.

## Scenario 1: a rolling file size that isn't a number

Config sets a numeric rotation-size property to `notanumber`.

**Logback** (`<maxFileSize>notanumber</maxFileSize>` inside a
`SizeAndTimeBasedRollingPolicy`): **nothing is printed by default at all.** The failure
only becomes visible if you explicitly wire up a `StatusListener` or call
`StatusPrinter.print(...)` yourself - something most applications never do. With that
wired up, here's what was actually happening the whole time:

```
21:07:47,395 |-ERROR in ch.qos.logback.core.joran.util.PropertySetter@64d2d351 - Failed to invoke valueOf{} method in class [ch.qos.logback.core.util.FileSize] with value [notanumber]
21:07:47,395 |-WARN in ch.qos.logback.core.joran.util.PropertySetter@64d2d351 - Failed to set property [maxFileSize] to value "notanumber".  ch.qos.logback.core.util.PropertySetterException: Conversion to type [class ch.qos.logback.core.util.FileSize] failed.
21:07:47,403 |-ERROR in c.q.l.core.rolling.SizeAndTimeBasedRollingPolicy@1368594774 - maxFileSize property is mandatory.
21:07:47,418 |-WARN in ch.qos.logback.core.rolling.RollingFileAppender[FILE] - TriggeringPolicy has not started. RollingFileAppender will not start
```

The application does not crash. It also does not log anything to that file - the
appender just silently never starts, and by default nothing tells you that. If it's the
only appender configured, every log call for the life of the process is a silent no-op.

**Log4j2** (`<SizeBasedTriggeringPolicy size="notanumber"/>`): visible by default, but
terse:

```
ERROR StatusConsoleListener FileSize unable to parse bytes: notanumber
```

One line, printed to the console without any extra setup - better than Logback's silent
default. But it names neither the appender nor the config file/property path, and it's
easy to lose in a normal amount of startup log noise.

**Rainbow Gum** (`logging.output.file.maxFileSize=notanumber`):

```
Validation failed for io.jstach.rainbowgum.rolling.RollingFileOutputBuilder:
Error for property. key: 'logging.output.file.maxFileSize' from PROPERTIES_STRING[logging.output.file.maxFileSize], java.lang.NumberFormatException For input string: "notanumber"
```

Thrown as an actual exception at startup - the application does not start with a broken
logger. Names the exact property key, which underlying exception caused the failure, and
where the value was read from.

## Scenario 2: an unrecognized time zone

Config sets a timestamp zone to a string that isn't a real zone ID.

**Logback** (`%d{HH:mm:ss.SSS, NotAZone}` in the pattern): crashes hard, immediately, the
very first time any code calls `LoggerFactory.getLogger(...)` anywhere in the
application - which for most apps is one of the first things that happens on startup:

```
Failed to instantiate [ch.qos.logback.classic.LoggerContext]
Reported exception:
ch.qos.logback.core.LogbackException: Failed to initialize or to run Configurator: ch.qos.logback.classic.util.DefaultJoranConfigurator
	at ch.qos.logback.classic.util.ContextInitializer.invokeConfigure(ContextInitializer.java:166)
	...
Caused by: java.time.zone.ZoneRulesException: Unknown time-zone ID: NotAZone
	at java.base/java.time.zone.ZoneRulesProvider.getProvider(ZoneRulesProvider.java:272)
	...
	at ch.qos.logback.classic.pattern.DateConverter.start(DateConverter.java:45)
	...
```

Note the inconsistency with Scenario 1: a bad rolling-policy size silently disables one
appender and lets the app keep running; a bad time zone in a pattern takes down the
*entire logging system* at first use, with a raw stack trace whose actual cause
(`Unknown time-zone ID`) is 20 lines down, under several layers of Logback/Joran
plumbing the reader has to already know to ignore.

**Log4j2** (`%d{HH:mm:ss.SSS}{NotAZone}` in the pattern): no error at all, in any form.
The zone is silently ignored and the default system zone is used instead - the log line
comes out with a perfectly normal-looking timestamp, giving no indication anything was
misconfigured.

**Rainbow Gum** (`logging.encoder.list.zoneId=Not/AZone`), shown here as the full
end-to-end message a real misconfigured `RainbowGum.builder(config).build()` actually
produces, not just the innermost builder's own message:

```
Validation failed for io.jstach.rainbowgum.json.encoder.LogstashEncoderBuilder:
Error for property. key: 'logging.encoder.list.zoneId' from PROPERTIES_STRING[logging.encoder.list.zoneId], java.time.zone.ZoneRulesException Unknown time-zone ID: Not/AZone
Tried: 'logging.encoder.list.zoneId' from PROPERTIES_STRING[logging.encoder.list.zoneId], [logging.appender.list.encoder]->URI(logstash:///)[zoneId]
  ↳ Error converting property. key: 'logging.appender.list.encoder' from PROPERTIES_STRING[logging.appender.list.encoder], value: 'logstash'
  ↳ Failure providing Appender: 'list' from property: Property[logging.appenders]=[list].
  ↳ Failure providing Appenders for route: 'default'.
```

Every line is a real cause, each naming the exact component and property key
responsible - not a stack trace to be filtered through. The actual root cause (the
malformed `zoneId`) is the *first* line, not buried under three levels of generic
"failure providing X" wrapping context; that context is still there, just listed below
in the order it wrapped the failure, for whoever wants to trace it back up.

## Scenario 3: an unrecognized charset name

**Rainbow Gum** (`logging.encoder.list.charset=not-a-charset`):

```
Validation failed for io.jstach.rainbowgum.pattern.format.PatternEncoderBuilder:
Error for property. key: 'logging.encoder.list.charset' from PROPERTIES_STRING[logging.encoder.list.charset], java.nio.charset.UnsupportedCharsetException not-a-charset
```

Logback and Log4j2 equivalents not yet captured here - a good next addition to this
document.

## Takeaway

This isn't "Rainbow Gum's error messages are prettier." It's that Rainbow Gum has
exactly *one* code path for "a property failed to convert" - the same
`Validator`/`ValidationException` machinery every builder generated from
`@LogConfigurable` goes through - so the answer to "what happens if I typo this
property" is always the same shape of answer. Logback and Log4j2 each have many
independent, ad hoc conversion code paths (Joran's `PropertySetter`, Log4j2's per-plugin
attribute binding, pattern converters that validate lazily at `start()` time vs. never at
all), so the honest answer for either of them is "it depends which property."
