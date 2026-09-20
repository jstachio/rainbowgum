# Developing Rainbow Gum

Notes for contributors working on Rainbow Gum itself: build tooling and test suite
decisions that were tried and didn't pan out, so nobody re-discovers them the hard way.

## Build speed experiments that were tried and reverted

### JUnit 5 parallel test execution

Enabling JUnit 5's own test parallelism (`junit.jupiter.execution.parallel.*`) was tried
and reverted - it did not actually make the build faster, even with every knob turned to
the most conservative setting.

It also had a real correctness cost while it was in place: JUnit 5 dispatches every test
through a `ForkJoinPool`-managed executor the moment parallelism is enabled at all, even
in `same_thread` mode, so any test asserting on the literal name of the executing thread
breaks. Rainbow Gum is a logging framework, and a fair number of tests golden-string
assert formatted output that includes the thread name (`FileOutputTest` in particular),
so this was not a small edge case.

The actual fix for slow modules was simpler and is still in place: move slow,
I/O-heavy tests (real file writes/rotation, not just in-memory assertions) into their
own Maven module instead of running them alongside the fast ones. See
`test/rainbowgum-test-file` (`RollingFileOutputTest` and friends) for the pattern.

### A `fast` Maven profile for skipping javadoc

A `fast` profile that set `<maven.javadoc.skip>true</maven.javadoc.skip>` in its
`<properties>` was tried and removed from `pom.xml` - a profile-scoped javadoc-skip
property did not reliably skip javadoc generation across this multi-module reactor.

`bin/build-fast.sh` skips javadoc the way that actually works instead:
`-Dmaven.javadoc.skip=true` passed on the command line (highest precedence, no
profile-activation-timing ambiguity to worry about), not through a profile property.
`bin/doc.sh` remains the real, comprehensive javadoc build - `build-fast.sh` is only ever
a local dev-loop shortcut, never a substitute for it.
