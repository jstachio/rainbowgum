# Checker Framework bugs encountered

Bugs hit while running this project's `checkerframework` analysis profile, kept here so
they can be filed against the Checker Framework issue tracker
(https://github.com/typetools/checker-framework/issues). Each entry should end up with a
minimal repro so it is easy to turn into a standalone bug report.

## `BugInCF` crash in `getElementValueArray` (resourceleak)

Status: **not yet filed**, and no minimal repro yet.

- **Checker Framework version seen:** 4.2.3.
- **Command:** `_run_modules=rainbowgum-file bin/analyze.sh checkerframework` (the
  module is normally excluded from that profile, see below).
- **Symptom:** the compiler run aborts with:
  ```
  error: getElementValueArray(@org.checkerframework.framework.qual.DoesNotUnrefineReceiver({"resourceleak"}), value(), class java.lang.String)
    ; The Checker Framework crashed.  Please report the crash.  Version: Checker Framework 4.2.3.
  ```
- **Trigger, as described in `bin/analyze.sh`:** reproducible when analyzing a `close()`
  call against a freshly compiled declaring class (not one coming from a stub file or
  from bytecode). Not narrowed down further yet.
- **Affected modules:** `rainbowgum-file`, `rainbowgum-tomcat`, and
  `:rainbowgum-spring-boot4`. `bin/analyze.sh` excludes them from the
  `checkerframework` profile only (`_modules_no_checkerframework`); errorprone and
  nullaway still analyze them.
- **To do before filing:** reduce to a small standalone project, confirm it on the
  latest Checker Framework release, and attach the full stack trace (rerun with
  `-AprintErrorStack` to get it).
