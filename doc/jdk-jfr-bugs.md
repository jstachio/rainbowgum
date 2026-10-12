# JDK Flight Recorder (JFR) bugs encountered

Bugs hit while building `rainbowgum-jfr`, kept here so they can be filed against the JDK
bug tracker (https://bugreport.java.com / https://bugs.java.com). Each entry has a
minimal repro so it is easy to turn into a standalone bug report.

There are currently no open entries.

## Retracted: `LinkageError` "attempted duplicate class definition" for Rainbow Gum's event classes

**Do not file.** This was recorded as a JDK bug, but it was caused by Rainbow Gum's own
code, and it also broke the documented `-XX:StartFlightRecording` setup on every JDK from
21 to 27 (found by black-box QA for 0.15.0).

- **Symptom:** with a recording already running when the event classes load, JFR logs
  `Unexpected error when adding instrumentation for event type io.jstach.rainbowgum.Info.
  null` and then `LinkageError: loader 'app' attempted duplicate class definition for
  io.jstach.rainbowgum.jfr.RainbowGumLogEvent$InfoEvent`. Log events crash the
  application and alert events are silently not recorded. Tests only hit it when one test
  ran on its own, so it had been worked around with a test-only warmup commit.
- **Cause:** `RainbowGumLogEvent` and `RainbowGumAlertEvent` each had a static factory,
  `of(Level)`, returning their own subclasses. Verifying that method loads the subclasses
  while the base event class is still being linked, and JFR's eager instrumentation of an
  event class fails when it is loaded at that point. That is also why the error could
  name a sibling of the event actually committed.
- **Fix:** the factories moved to the callers (`JfrLogOutput`, `JfrAlertListener`), the
  warmup was removed, and `JfrStartupRecordingTest` runs a fresh JVM with
  `-XX:StartFlightRecording` to cover the documented setup.
- **Lesson:** a JFR event class should not have code that refers to its own subclasses.
