[![Maven Central](https://maven-badges.herokuapp.com/maven-central/io.jstach.rainbowgum/rainbowgum/badge.svg)](https://maven-badges.herokuapp.com/maven-central/io.jstach.rainbowgum/rainbowgum)
[![Github](https://github.com/jstachio/rainbowgum/actions/workflows/maven.yml/badge.svg)](https://github.com/jstachio/rainbowgum/actions)

# Rainbow Gum

An opinionated small JDK 21+ logging framework that offers implementations for multiple facades
like SLF4J and does mostly what you want out of the box.

It is called **rainbow gum** after 
[Eucalyptus Deglupta](https://en.wikipedia.org/wiki/Eucalyptus_deglupta) a beautiful tree native
to Hawaii that has colorful bark (*colorful logs*).

In Rainbow gum you do all your logging configuration in Java and can choose to leverage your existing configuration framework (like [avaje-config](https://avaje.io/config/)). You write a single service provider using a discoverable API of builders and then package it up in a Jar. It will be graal vm native friendly, initialize hella fast and no configuration drift.


## Documentation

* **[Latest SNAPSHOT RainbowGum doc](https://jstach.io/rainbowgum/)**
* **[Current released RainbowGum doc](https://jstach.io/doc/rainbowgum/current/apidocs)**

The doc is also on javadoc.io but is not aggregated like the above.
The aggregated javadoc is the preferred documentation and the rest of this readme
is mainly for ~~propaganda~~ marketing purposes.

For previous releases:

    https://jstach.io/doc/rainbowgum/VERSION/apidocs

Where `VERSION` is the version you want.

## Comparison to other frameworks

In terms of features and complexity Rainbow Gum aims to be slightly more complicated than
`slf4j-simple` but massively more simple than Log4J 2. 

For example in ascending order of complexity/features:

1. slf4j-simple
1. penna
1. rainbowgum
1. reload4j (log4j 1)
1. logback
1. log4j2

| Library         | Version  | Jar Size(s) KBs  | Sum     | Notes                                    |
| --------------- | -------- | ---------------- | ------- | ---------------------------------------- |
| slf4j-simple    | 2.0.9    |  15              | 15  KB  | Uses System.out PrintStream              |
| penna           | 0.6      |  41 + 8 + 9      | 58  KB  | Only does JSON currently                 |
| **rainbowgum**  | 0.1.0    |  200 + 20        | 220 KB  | Requires JDK 21 and preview (optional)   |
| reload4j        | 1.2.25   |  325 + 10        | 335 KB  | JDK 1.5 uses synchronized on IO          |
| logback         | 1.4.11   |  583 + 276       | 859 KB  | Slightly complicated to configure        |
| log4j2          | 2.20.0   |  305 + 1847 + 23 | 2.1 MB! | Kitchen sink of features. log4jshell     | 

## Primary Goals

1. Inititalization speed
1. Configuration is programmatic instead of joran/xml/yaml/properties etc
1. Ability to test newer JDK offerings because of newer design
1. Additional configuration like other outputs can be done by simply adding a runtime dependency.
1. Determine best pratices and provide them for OOB
1. Developer experience is more important than other things (colorful logs)
   * Themes color console output is in the works so you just add a jar get a unique styling. 
1. Keep jar size down
1. Graal VM Native works with no special configuration
1. Designed for microservices/cloud/native/lambda instead of monolithic beasts of past.
1. Builtin Fast no dependency JSON support 
1. Add a single dependency that works for both development and production for most usages

Consequently:

* It does the right thing OOB like using JAnsi to test for color output and not using `synchronized` for any IO.
* It starts much faster than logback and log4j (1 and 2)
* It focuses on being programmatically configurable.
* It has zero dependencies (unless using the slf4j bridge)
* It is modularized
* It will use newer features like Virtual threads and Scoped values for MDC

However:

* No external configuration file for out of the box
* It will unlikely do reloading of config. The ability to reload (loggers) adds a lot of bloat and hurts perf.
* It will probably have less configuration and tuning options.
* It will probably not have rolling of files for now. Rolling of files can be handled with other tools and does not make sense for cloud deployments.
  * However reopening files for things like logrotate or retrying / resilience will eventually be supported
* API current will be less stable

## Questions

### Why not just build ontop of Log4j 2.0 or Logback?

Logback and Log4j 2.0 have complex APIs and offer very little opinion on what should be done OOB.

Both offer complex non-java (xml/properties/yaml) configuration that slows initialization down while increasing security surface area as indictive of log4j 2 shell. 

Both are in the realms of megabytes in jar size which may not matter much for traditional java deployments but does matter
for Graal VM Native or even plain Hotspot as well as CI pipelines that build uber jars.

Both **may** not be ready for Loom, Panama, Valhalla, SIMD. Both cannot change API easily as there are numerous
libraries that depend on legacy API.

Also Rainbow Gum may provide adapters to use Logback or Log4j core components. 

Finally ideas and features of Rainbow Gum could eventually make their way into logback and log4j 2.0.

Someone needs to keep experimenting and pushing forward. The current author of rainbowgum [made it so Logback was programmatically configurable many moons ago](https://github.com/qos-ch/logback/commit/d93e5eaaeb04699f69006c2be326d74586845876). We plan on continuing that tradition of bringing what we learn
back to the established frameworks.

### Why is there no configuration file support?

Because I think this is a fundemental problem with most logging frameworks. It is one of the reasons why they are so bloated and complicated. Let other libraries do configuration and have them run first.

### Initialization issues (why no config file cont.)

When logging tries to handle configuration there are initialization issues.
Because so many logging frameworks are the first to execute they provide their own configuration framework.

What really should happen is the configuration framework should load first, capture events like warnings or info, and then provide or replay them when the logging framework starts. Libraries that participate in configuration really should not use logging and while SLF4J does provide some form of trampolining (ie queue while initializing) that is not the case for JUL or System logger.

Because many poorly designed configuration libraries needing logging and logging needs configuration in some cases I have seen log4j2 initialize more than 5 times during boot (spring boot) and for logback 3 times (dropwizard). 

For configuration library that understands these bootstrapping problems I recommend [avaje config](https://avaje.io/config/) or writing your own to load simple properties files. It is a shame there are not more bootstrapping configuration libraries as most configuration really is just `Map<String,String>`.

### Configuration Drift (why no config file cont.)

Configuration stored as files in projects is just asking for different configuration spread across an organization.
While it is possible to pack up the files as resources in a shared jar this  does not always work in a `module-info` world
as well as GraalVM native as resources files need special considerations.

### Profiles and different environments (why no config file cont.)

Because different logging needs are needed for different environments many logging frameworks offer complicated solutions like expression languages and whatever the fuck is "Arbiter" (log4j 2) that could easily be managed
with a tiny bit of user Java code.

### Solution 

In Rainbow gum you do all your configuration in Java and can choose to leverage your existing configuration framework.
You write a single service provider using a discoverable API of builders and then package it up in a Jar. It will be graal vm native friendly, initialize hella fast and no configuration drift.
