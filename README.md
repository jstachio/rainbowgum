
# Rainbow Gum

An opinionated small logging framework that offers multiple bridge implementations and does mostly what you want OOB.

It is called **rainbow gum** after https://en.wikipedia.org/wiki/Eucalyptus\_deglupta .


## Primary Goals

1. Inititalization speed
1. Configuration is programmatic instead of joran/xml/yaml/properties etc
1. Add a single dependency that works for both development and production for most usages
1. Determine best pratices and provide them for OOB
1. Developer experience is more important than other things
1. Ability to test newer JDK offerings because of newer design
1. Keep jar size down
1. Graal VM Native works with not special configuration
1. Designed for microservices/cloud/native/lambda instead of monolithic beasts of past.

Consequently:

* It does the right thing OOB like using JAnsi for colors and not blocking on console output
* It starts much faster than logback and log4j (1 and 2)
* It focuses on being programmatically configurable.
* It has zero dependencies (unless using the slf4j bridge)
* It is modularized

However:

* It will unlikely do reloading of config. The ability to reload add a lot of bloat.
* It will probably have less configuration and tuning options.
* It will probably not have rolling of files for now. Rolling of files can be handled with other tools.
* API will be less stable

## Questions

### Why not just build ontop of Log4j 2.0 or Logback?

Logback and Log4j 2.0 have complex APIs and offer very little opinion on what should be done OOB.

Both offer complex non-java (xml/properties/yaml) configuration that slows initialization down while increasing security surface area
as indictive of log4j 2 shell. 

Both are in the realms of megabytes in jar size which may not matter much for traditional java deployments but does matter
for Graal VM Native or even plain Hotspot as well as CI pipelines that build uber jars.

Both **may** not be ready for Loom, Panama, Valhalla, SIMD. Both cannot change API easily as there are numerous
libraries that depend on legacy API.

Also Rainbow Gum may provide adapters to use Logback or Log4j core components. 

Finally ideas and features of Rainbow Gum could eventually make their way back into logback and log4j 2.0.
Someone needs to keep experimenting and pushing forward.


