# Agent build issues

Agents should report build difficulties here, including cache problems, flaky
tests, stale artifacts, and unnecessary waits. The goal is to keep the time
agents spend waiting for build tools as short as possible while preserving
reliable results. Read [build.md](../build.md) before choosing build commands.

For each entry, record the command, observed symptoms, why a workaround was
needed, and what remains uncertain. Distinguish observations from suspected
causes. Explain how to return to a faster build rather than turning a temporary
diagnostic workaround into a requirement for every change. Identify yourself
at the end of each entry (agent name and model, if known).

## 2026-09-30: Golden string failures and stale runtime behavior

While fixing the startup diagnostics tests, module tests and builds involving
cached artifacts produced inconsistent output that appeared to come from older
code. An uncached full `clean verify` produced consistent results across the
reactor. This did not establish that the build cache itself was defective:
stale packaged dependency jars and stopping at the `test` phase were also
possible contributors. A downstream module can resolve a packaged dependency
jar that has not been refreshed by a test-only build.

The diagnostic build used:

```sh
./mvnw -T1 --fail-at-end \
  -Dmaven.build.cache.enabled=false \
  -Dmaven.test.failure.ignore=true clean verify
```

The choices served different purposes:

* `-Dmaven.build.cache.enabled=false` removed cache restoration as a variable.
  The cache is normally off by default, but can be enabled by `-Pcache` or
  `RAINBOWGUM_CACHE=true`. The explicit override made this run independent of
  those settings.
* `clean verify` rebuilt and packaged dependencies before their consumers ran,
  avoiding reliance on old jars while checking the whole reactor.
* `-T1` replaced the repository's default `-T2C` with a serial reactor build to
  simplify diagnosis and the ordering of output. No parallel-build defect was
  demonstrated. This was a diagnostic precaution, not a fix for golden string
  assertions or a reason to disable parallel builds routinely.
* `--fail-at-end` let independent modules continue after a failure. Maven can
  still skip modules that depend on a failed module.
* `-Dmaven.test.failure.ignore=true` let modules with assertion failures finish
  so downstream tests could run too. This collected golden string failures
  across the build in one pass. A successful Maven exit with this option does
  not prove the tests passed; all Surefire and Failsafe XML reports were checked.

Final verification used the same command with
`-Dmaven.test.failure.ignore=false`. The most recent full run completed in about
55 seconds with 20,107 tests and no failures or errors.

For routine changes, prefer the smallest build that verifies the behavior and
retain parallelism or caching when results are reliable. When changing a module
used by other modules, make sure its packaged artifact is current. If stale
behavior recurs, capture the cache settings, resolved dependency artifacts, and
cached versus uncached results before concluding that caching or parallelism is
the cause. The cache's role in the earlier inconsistent output remains
unconfirmed.

Agent: Codex (GPT-6).

## 2026-09-30: Cached packaging run skips tests in a later verify

While changing missing-property error formatting, I compiled core using
`./mvnw -pl core -am -DskipTests package` to generate an example before changing
golden strings. A subsequent full
`./mvnw --fail-at-end -Dmaven.test.failure.ignore=true verify` restored that core
build from cache and logged `Skipping plugin execution (cached): surefire:test`.
Core tests did not run in that pass, although downstream assertion failures were
collected. A cached packaging run with tests skipped therefore did not provide
the test coverage needed for verification in this session.

For final verification I used
`./mvnw --fail-at-end -Dmaven.build.cache.enabled=false clean verify`, retaining
the default parallel reactor. This forced the tests to execute. The cache log
establishes that test execution was skipped; the reason the extension reused
this build across the differing test settings has not been investigated.

Agent: Codex (GPT-6).

## 2026-09-30: Checker Framework rejects test property cleanup

The default parallel `./mvnw --fail-at-end verify` completed in about 14 seconds
with caching enabled while adding the optional simple-props default profile.
There was no need for the serial, uncached workaround above.

The targeted analysis command
`_run_modules=rainbowgum-simple-props bin/analyze.sh` then failed during test
compilation. Checker Framework reported `[clear.system.property]` for
`System.clearProperty(SimpleProperties.PROFILES_PROPERTY)`, warning that it
might clear a predefined system property. The key is the application's
`logging.profiles` property. Using `System.getProperties().remove(...)`, as the
existing tests already do, avoids this diagnostic and restores the prior absent
value without disabling analysis.

Agent: Codex (GPT-6).

## 2026-09-30: Module-only verification uses an older core dependency

After analysis passed, `./mvnw -pl rainbowgum-simple-props verify` failed four
provider alert assertions. Each actual result lacked the initial
`Loading properties from ...` alert emitted by the current core implementation.
The full reactor build had passed these assertions earlier. Building a module
alone can use an older installed core snapshot instead of the current reactor
dependency; `verify` does not update installed artifacts.

Use `./mvnw -pl rainbowgum-simple-props -am verify` to include the required
reactor dependencies when testing this module. This keeps the build targeted
without requiring a full rebuild, serial execution, or disabling the cache.

Agent: Codex (GPT-6).

## 2026-10-01: Analysis with reactor dependencies includes the processor module

While adding rolling failure metrics, this targeted analysis failed:

```sh
_run_modules=core,rainbowgum-file MAVEN_CLI_OPTS=-am \
  bin/analyze.sh 'errorprone nullaway'
```

Core passed Error Prone, but `-am` also selected `rainbowgum-apt`, which is not
in the script's supported analysis module list. Its compilation reported
`Annotation processor 'io.jstach.prism.apt.PrismGenerator' not found`, followed
by missing generated classes. The normal full reactor build had passed.
The suspected cause is the analysis profile's processor path configuration;
this was not investigated as part of the rolling output change.

Build and install current dependencies under the normal profile first, then
analyze only the supported modules without `-am`:

```sh
./mvnw -pl core,rainbowgum-apt -am -DskipTests \
  -Dmaven.build.cache.enabled=false install
_run_modules=core,rainbowgum-file bin/analyze.sh 'errorprone nullaway'
```

This preserves the current core dependency without applying analysis profiles
to unsupported modules. It does not require a serial build.

Agent: Codex (GPT-6).

## 2026-10-02: Intermittent snippet compile failure in the doc build

Command:

```sh
bin/doc.sh
```

`bin/doc.sh` runs `./mvnw -Pdoc clean install -DskipTests` (no `-T`). It failed
in two of four runs this session: once with the build cache on and once with
`-Dmaven.build.cache.enabled=false`. Both failures were at the `rainbowgum`
aggregate module's test compile:

```
rainbowgum/src/test/java/snippets/FullConfigurationExample.java:[11,33]
package io.jstach.rainbowgum.file does not exist
```

The cache-enabled failure also had `rainbowgum-test-kitchensink` failing to
find `org.junit.jupiter.api`. Each failure was followed by a passing rerun with
the cache disabled. The changes being documented
(a new Helidon module, a javadoc comment in core) did not touch either module.

Observed: `rainbowgum/pom.xml` does declare `rainbowgum-file` as a test
dependency, so the package is not simply missing from the pom. Suspected but
unconfirmed: a reactor ordering or module path issue specific to the `-Pdoc`
profile, made more frequent (but not caused solely) by the build cache.
Not investigated further.

Workaround: rerun `bin/doc.sh -Dmaven.build.cache.enabled=false`. Treat a
failure in these two modules as unrelated to a doc-only change unless it
repeats.

Agent: Claude Code (Claude Opus 5.5).

## 2026-10-02: Another intermittent documentation test compile failure

While adding retained metric gauges, `./mvnw --fail-at-end verify` passed in
16.7 seconds and all three core analysis profiles passed. The subsequent
`bin/doc.sh` run with caching enabled failed after 25.6 seconds: test compilation
in `rainbowgum-test-jcl`, `rainbowgum-test-jboss-logging-bridge`, and
`rainbowgum-scopedkeyvalues` could not resolve `org.junit.jupiter.api`.
These modules' sources and dependency declarations were unchanged.

The retry used:

```sh
bin/doc.sh -Dmaven.build.cache.enabled=false
```

It passed in 32.5 seconds, retaining the repository's default parallelism.
No `-T1` override was needed. This repeats the earlier documentation compile
symptoms in different modules; a passing uncached retry does not establish
that the cache caused the failure. The dependency resolution or module path
interaction with the documentation profile remains uninvestigated. Keep the
normal cached build for routine work and use this override if the documented
failure recurs, rather than disabling caching or parallelism permanently.

Agent: Codex (GPT-6).

## 2026-10-02: Misspelled analyze.sh profile ran no analysis

Command:

```sh
_run_modules=rainbowgum-file bin/analyze.sh checker
```

The profile is named `checkerframework`, not `checker`. Maven 3.9.16 only
warns about an unknown `-P` profile:

```
[WARNING] The requested profile "checker" could not be activated because it does not exist.
```

It then ran a plain `verify` with no analysis and the script exited 0. I was
filtering the output for `[ERROR]` lines in `src/main`, so the empty result
looked like a clean Checker Framework run of `rainbowgum-file`. Only a rerun
with the real name showed what the checker reports.

A related misreading in the same session: I concluded `rainbowgum-file` was
not in `analyze.sh`'s module list, so CI had never analyzed it. That is wrong.
It is analyzed by errorprone and nullaway, and only excluded from the
`checkerframework` profile on purpose, because the Checker Framework itself
crashes on it (`BugInCF` on `getElementValueArray`/`resourceleak`, see the
comment in `bin/analyze.sh`). That crash message appeared in my output and I
moved past it. The message of commit `e3a96370` ("Fix Checker Framework
nullness errors in RollingPolicy") repeats the wrong claim. Note also that
`analyze.sh` points to `develop.md` for the crash, but no `develop.md` exists,
and there is no write-up of the crash ready to file upstream yet (now tracked
in [checker-framework-bugs.md](checker-framework-bugs.md)).

Fix: `bin/analyze.sh` now rejects unknown profile names before running Maven
(exit code 2, listing the valid names).

When filtering analysis output, also look for `could not be activated` and
`crashed`, not just `[ERROR]` lines.

Agent: Claude Code (Claude Opus 5.5).

## 2026-10-03: Documentation test compilation failed with caching disabled

While renaming the metrics snapshot API, the full `./mvnw --fail-at-end verify`
passed in 16.6 seconds with the normal cache settings. The documentation check
used the earlier workaround:

```sh
bin/doc.sh -Dmaven.build.cache.enabled=false
```

It failed after 12.5 seconds because core test compilation could not resolve
`org.junit.jupiter.api`. This repeats the earlier documentation symptoms even
with the cache disabled, so disabling the cache alone is not a reliable fix.
The renamed API documentation had already been generated successfully.

The retry used:

```sh
bin/doc.sh -Dmaven.build.cache.enabled=false -Dmaven.test.skip=true
```

It passed in 31.8 seconds with the default parallelism. `maven.test.skip` skips
test compilation as well as execution, avoiding the failing documentation
step. The full build had already compiled and run tests before this retry;
the retry itself is only documentation validation. Snippets are read from
their source files, and the generated `Metric.value()` and `snapshot()` links
were checked. The documentation profile's test dependency resolution remains
uninvestigated. This does not justify changing routine build cache settings.

Agent: Codex (GPT-6).

## 2026-10-03: Cached build-classpath does not create the requested output file

While benchmarking publishers from separate branches, the runner invoked
`./mvnw -pl core dependency:build-classpath -Dmdep.outputFile=...` for a new result
directory. Maven returned success but logged
`Skipping plugin execution (cached): dependency:build-classpath`; the requested
classpath file did not exist. An earlier invocation with a different output path
had succeeded. Compiling the separately loaded publisher then failed because the
JSpecify dependency was missing from its classpath.

The runner now disables the build cache only for this classpath generation goal
and reads the generated file in a separate shell assignment so a missing file
stops the script immediately. Normal verification retains caching and parallelism.
The cache's treatment of this goal's output path has not been investigated further.

Agent: Codex (GPT-6).
