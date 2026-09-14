[![Maven Central](https://img.shields.io/maven-central/v/io.jstach.rainbowgum/rainbowgum)](https://central.sonatype.com/search?q=g:io.jstach.rainbowgum)
[![Github](https://github.com/jstachio/rainbowgum/actions/workflows/maven.yml/badge.svg)](https://github.com/jstachio/rainbowgum/actions)
[![Code Coverage](https://codecov.io/gh/jstachio/rainbowgum/branch/main/graph/badge.svg)](https://app.codecov.io/gh/jstachio/rainbowgum)

<img src="etc/logo/rainbowgum-logo.svg" alt="Rainbow Gum Logo">

# Rainbow Gum

A modern modular JDK 21+ logging framework that offers implementations for multiple facades
like SLF4J / System.Logger and does mostly what you want out of the box.

It is called **rainbow gum** after 
[Eucalyptus Deglupta](https://en.wikipedia.org/wiki/Eucalyptus_deglupta) a beautiful tree native
to Hawaii that has colorful bark (*colorful logs*).

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
> more likely to be up to date and correct! The rest of this readme is mainly for ~~propaganda~~
> marketing purposes.

The doc is also on javadoc.io but is not aggregated like the above.

For previous releases:

    https://jstach.io/doc/rainbowgum/VERSION/apidocs

Where `VERSION` is the version you want.


## Why choose Rainbow Gum

Covered in [why_rainbowgum_is_better.md](why_rainbowgum_is_better.md).
