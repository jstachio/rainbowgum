[![Maven Central](https://img.shields.io/maven-central/v/io.jstach.rainbowgum/rainbowgum)](https://central.sonatype.com/search?q=g:io.jstach.rainbowgum)
[![Github](https://github.com/jstachio/rainbowgum/actions/workflows/maven.yml/badge.svg)](https://github.com/jstachio/rainbowgum/actions)
[![Code Coverage](https://jstach.io/rainbowgum/coverage/badge.svg)](https://jstach.io/rainbowgum/coverage/)

<img src="etc/logo/rainbowgum-logo.svg" alt="Rainbow Gum Logo">

# Rainbow Gum

A modern modular JDK 21+ logging framework that offers implementations for multiple facades
like SLF4J / System.Logger and does mostly what you want out of the box.

It is called **rainbow gum** after 
[Eucalyptus Deglupta](https://en.wikipedia.org/wiki/Eucalyptus_deglupta) a beautiful tree native
to Hawaii that has colorful bark ([*colorful logs*](#colorful-logs)).

<img src="etc/screenshots/ttll-rainbowgum.png" alt="Rainbow Gum console output: colored timestamp, thread, level, logger, and key values">

In Rainbow Gum you can: 

* Leverage your existing configuration/application framework (like [avaje-config](https://avaje.io/config/) or Spring Boot `Environment`). 
* Do all your logging configuration in Java with a discoverable API of builders
* Only use System Properties and no loading of resources for a ultimate minimal experience
* Or various combinations of the above

Because Rainbow Gum is very modular it can provide a more "choose your own" experience where you can scale down and only use the JDK `System.Logger`
with minimal dependencies or scale up with fully loaded ANSI SLF4J experience. 
In some cases just adding modules will provide automatic configuration.

Rainbow Gum's default property configuration follows Spring Boot's patterns so if you are familiar with `logging.level...`
than you will feel at home with Rainbow Gum whether you use Spring or not.

Regardless of which configuration option you choose it is GraalVM native friendly, Jlinkable, and initializes hella fast.

The long term goal of Rainbow Gum is to be a logging framework **for all** with the only requirement being a modern JDK.

## Documentation

* **[Latest SNAPSHOT RainbowGum doc](https://jstach.io/rainbowgum/)**
* **[Current released RainbowGum doc](https://jstach.io/doc/rainbowgum/current/apidocs)**

> [!WARNING]
> While this readme does contain some documentation the above is the preferred documentation and
> more likely to be up to date and correct!

The doc is also on javadoc.io but is not aggregated like the above.

For previous releases:

    https://jstach.io/doc/rainbowgum/VERSION/apidocs

Where `VERSION` is the version you want.


## Why choose Rainbow Gum

Covered in [why_rainbowgum_is_better.md](why_rainbowgum_is_better.md).

## Colorful logs

Rainbow Gum core comes with themes that work on four different formats also included in core: [TTLL](https://jstach.io/doc/rainbowgum/current/apidocs/index.html#ttll), [JSON5](https://jstach.io/doc/rainbowgum/current/apidocs/index.html#core_json), [logfmt](https://jstach.io/doc/rainbowgum/current/apidocs/index.html#logfmt), and [LTSV](https://jstach.io/doc/rainbowgum/current/apidocs/index.html#ltsv).

The console format below is [TTLL](https://jstach.io/doc/rainbowgum/current/apidocs/index.html#ttll) (time, thread, level, logger), colored with the "One Dark" theme
(note no pattern module or pattern string is needed and only one line of configuration is required):

```properties
logging.appender.console.encoder.ttll.theme=one_dark
```

<img src="etc/screenshots/ttll-one_dark.png" alt="TTLL output with the one_dark theme">

`darcula` uses IntelliJ's colors:

<img src="etc/screenshots/ttll-darcula.png" alt="TTLL output with the darcula theme">

`spring` matches Spring Boot's console colors and, like `rainbowgum`, uses the terminal's
own palette, so it works on light backgrounds too:

<img src="etc/screenshots/ttll-spring.png" alt="TTLL output with the spring theme on a light terminal">

The same themes color the structured encoders in core. [`logfmt`](https://jstach.io/doc/rainbowgum/current/apidocs/index.html#logfmt) writes one `key=value` line
per event for Loki, Vector and Go tooling:

```properties
logging.appender.console.encoder.uri=logfmt
logging.appender.console.encoder.logfmt.theme=one_dark
```

<img src="etc/screenshots/logfmt-one_dark.png" alt="logfmt output with the one_dark theme">

[`json5`](https://jstach.io/doc/rainbowgum/current/apidocs/index.html#core_json) switches to structured output while you hunt a bug, here pretty printed:

```properties
logging.appender.console.encoder.uri=json5
logging.appender.console.encoder.json5.prettyPrint=full
```

<img src="etc/screenshots/json5-rainbowgum.png" alt="Pretty printed json5 output" width="420">

Color is only written when the console supports ANSI, so piping to a file stays plain. The
screenshots are Rainbow Gum's real output rendered with xterm.js; see
[etc/screenshots/generate](etc/screenshots/generate/generate.sh).


## Developing Rainbow Gum

Covered in [development.md](development.md)
