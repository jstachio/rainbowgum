A smoke test proving Rainbow Gum works under the JDK's own AOT cache (JEP 483, Ahead-of-Time
Class Loading & Linking, JDK 24+): real logging, through the normal `rainbowgum-simple`
zero-config bootstrap, plus a custom, ServiceLoader-discovered
`RainbowGumServiceProvider.RainbowGumProvider` (`AotCacheTestProvider`), so this also proves
a user's own SPI extension is still correctly discovered and selected both while the cache
is being trained and afterward, when a run actually loads it. Same idea as
`test/rainbowgum-test-native` (the sibling GraalVM native-image smoke test), adapted for a
completely different mechanism.

## Why this module is not part of the normal build

Deliberately not listed in `test/pom.xml`'s `<modules>`. It needs a JDK 24+ runtime to do
anything meaningful, spawns real `java` subprocesses directly from Maven, and produces a
cache file nothing else in the reactor needs, so running it on every `mvn install`/`mvn
verify` would slow down the normal build for no benefit. It is built on its own, in CI
(`.github/workflows/aot-cache.yml`, a matrix job running in parallel across operating
systems) and locally on demand:

```
./mvnw install                                                    # from the repo root, once
./mvnw -f test/rainbowgum-test-aot-cache/pom.xml -Paot verify      # needs a JDK 24+ on PATH
```

Without `-Paot`, this module just compiles; its one test class is named `AotCacheRunIT`
(an integration test, run by `maven-failsafe-plugin`, not `maven-surefire-plugin`), so a
plain `mvn test` never touches it and never needs the AOT machinery at all.

## How this actually works

Two real `java` invocations, both against a jar-only classpath (the JDK's AOT cache
rejects any classpath entry that is a non-empty directory, confirmed by hand against a real
build of this module: exec-maven-plugin's own computed classpath would otherwise include
`target/classes`, so this module's own packaged jar plus dependency jars are used instead,
computed via `maven-dependency-plugin`'s `build-classpath` goal):

1. **`aot-cache-create`** (bound to the `package` phase): `java -XX:AOTCacheOutput=app.aot
   -cp ... Main`. One command both trains (runs `Main` once, recording which classes and
   methods actually get touched) and assembles the cache from that training run, per JEP
   483's shortcut for what is otherwise two separate `-XX:AOTMode=record`/`create` steps.
2. **`aot-cache-run`** (bound to `pre-integration-test`, so it runs before the IT below):
   `java -XX:AOTMode=on -XX:AOTCache=app.aot -cp ... Main`, with its stdout captured to a
   file. `AOTMode=on`, not the default `auto`, is deliberate: it fails the JVM outright if
   the cache cannot be mapped, instead of silently falling back to a normal, non-cached run,
   which would let a broken cache pass this module's test unnoticed. Verified this really
   fails by hand: deleting the cache file and rerunning this exact command exits 1 with
   "Unable to use AOT cache."

`AotCacheRunIT` (`maven-failsafe-plugin`, `integration-test`/`verify` goals, so it runs after
both `java` invocations above) only reads that captured output file back and asserts on it.
It deliberately plays no part in the cache itself: JEP 483 explicitly warns against including
a rich test framework in the classes an AOT cache actually trains against, so all of the
actual training and cache-loading happens in a plain `Main` class with no JUnit on its
classpath, and JUnit only ever runs afterward, in a separate JVM, to check a text file.

If a future JDK release changes the required flags or their behavior, that is useful signal
from this module, not noise.
