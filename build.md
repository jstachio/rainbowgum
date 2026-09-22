# Build

## Always use ./mvnw

Always build with `./mvnw`, not a system installed `mvn`. The wrapper pins the
exact Maven version (`.mvn/wrapper/maven-wrapper.properties`) that matches
`maven.core.version` in the root `pom.xml`, and the enforcer plugin will
actually fail the build if the two drift apart.

## Maven Build Cache

There is a Maven build cache extension wired in (`.mvn/extensions.xml`) but it
is off by default. Turn it on with `-Pcache` or by setting `RAINBOWGUM_CACHE=true`
in your environment. It hashes source content, not timestamps, so an unchanged
rebuild restores jars/test results straight from `~/.m2/build-cache` instead of
recompiling, which makes the "nothing changed, just rebuilding" loop a lot
faster. It gets forced off automatically for snapshot and release deploys
(`-Ddeploy=snapshot`/`-Ddeploy=release`), so it will never end up shipping a
stale cached artifact.

## bin/analyze.sh

Runs the null safety analyzers, checkerframework, errorprone, and nullaway,
against the modules that have already been verified clean. Modules get added
to that list one at a time as they pass, not all at once, see `develop.md`.

```
bin/analyze.sh
```

The `eclipse` profile is not in the default set since it is known broken
(unrelated ECJ warnings, not something a normal code change fixes), pass it
explicitly if you actually want to run it:

```
bin/analyze.sh eclipse
```

Set `_run_modules` in your environment to run a specific profile against an
ad hoc module list instead of the default one.

## Code coverage

```
./mvnw clean install -Pcodecover
```

or `bin/codecover-merge.sh`. The aggregated HTML report lands at
`rainbowgum-coverage-aggregate/target/site/jacoco-aggregate/index.html`.

## When in doubt

If any of the above feels off or out of date, check `.github/workflows/` for
how CI actually builds this project, that is the source of truth.
