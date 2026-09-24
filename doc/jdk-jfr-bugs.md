# JDK Flight Recorder (JFR) bugs encountered

Bugs hit while building `rainbowgum-jfr`, kept here so they can be filed against the JDK
bug tracker (https://bugreport.java.com / https://bugs.java.com). Each entry has a
minimal repro so it is easy to turn into a standalone bug report.

## First `Event.commit()` for a custom event class throws `LinkageError` if a `Recording` enabling it is already active

- **JDK version confirmed on:** OpenJDK/Temurin `26.0.2+10` (`java -version`:
  `openjdk version "26.0.2" 2026-07-21`). Not yet checked against 21 or other 25/26
  builds.
- **Symptom:**
  ```
  java.lang.LinkageError: loader 'app' attempted duplicate class definition for
  <SomeEvent>. (<SomeEvent> is in module <module>@<version> of loader 'app')
      at java.base/java.lang.ClassLoader.defineClass1(Native Method)
      at java.base/java.lang.ClassLoader.defineClass(ClassLoader.java:974)
      at java.base/java.lang.ClassLoader.defineClass(ClassLoader.java:1051)
      at java.base/java.security.SecureClassLoader.defineClass(SecureClassLoader.java:177)
      at java.base/jdk.internal.loader.BuiltinClassLoader.defineClass(BuiltinClassLoader.java:735)
      at java.base/jdk.internal.loader.BuiltinClassLoader.findClassInModuleOrNull(BuiltinClassLoader.java:678)
      at java.base/jdk.internal.loader.BuiltinClassLoader.loadClassOrNull(BuiltinClassLoader.java:604)
      at java.base/jdk.internal.loader.BuiltinClassLoader.loadClass(BuiltinClassLoader.java:578)
      at java.base/java.lang.ClassLoader.loadClass(ClassLoader.java:502)
      at <caller>.commit(...)   (i.e. inside jdk.jfr.Event#commit)
  ```
  Note the class named in the error is not necessarily the one actually committed - see
  "sealed hierarchy" note below.
- **Trigger:** In a fresh JVM, the *very first* `commit()` ever executed for a custom
  `jdk.jfr.Event` subclass throws this `LinkageError` **if and only if** a
  `jdk.jfr.Recording` that `enable(...)`s that event class is already constructed and
  started at the time of that first commit. The exact same commit, on the exact same
  class, in the exact same fresh JVM, does **not** throw if:
  - no `Recording` is active at all (a bare `new SomeEvent(); event.commit();` with no
    recording ever created is a normal, silent no-op), or
  - a prior, unrelated commit (of the same or a different event class, with no active
    recording) has already happened once in that JVM - after any one successful commit,
    subsequent `Recording.enable(...)` + commit cycles for the same class work reliably,
    repeatedly, for the rest of the JVM's life.
  So the fix is not "always start the recording after committing" (recording an event
  before any recording is active would just drop it) - the actual workaround is a cheap,
  recording-free "warm up" commit issued once, as early as possible, before any real
  `Recording` is created.
- **Confirmed independent of:**
  - Whether the event class hierarchy is `sealed`. Removing `sealed` from the abstract
    base class did not change the behavior.
  - Which concrete event subclass is committed - reproduces the same way regardless of
    which one is chosen.
  - Whether the event class is shared between multiple callers. Adam asked whether this
    project's two JFR-committing classes (`JfrLogOutput`, `JfrAlertListener`) sharing one
    event class hierarchy (`RainbowGumLogEvent`) was the trigger. It is not: giving
    `JfrAlertListener` its own, completely separate, never-before-touched event hierarchy
    (`RainbowGumAlertEvent`, no shared code with `RainbowGumLogEvent` beyond both
    extending `jdk.jfr.Event`) still hits the identical `LinkageError` on *that*
    hierarchy's own first cold-start commit. The bug is purely per-class ("has this
    exact class ever been committed before in this JVM"), not about reuse across
    callers.
  - Whether the JVM is running on the classpath or the module path... **partially**: a
    classpath-only repro (plain `java -cp`, no `module-info.java` anywhere) has not
    reproduced it in limited testing; every confirmed reproduction so far has been Maven
    Surefire running JUnit 5 tests on the **module path** (`javac ... module-path`,
    `module-info.java` present). Not yet confirmed whether this is a hard requirement or
    just how every attempt happened to be run.
- **Curious detail:** if the committed event class's declaring class is `sealed` with
  several permitted subclasses, the class named in the `LinkageError` is sometimes a
  *different* permitted subclass than the one actually committed (e.g. committing
  `ErrorEvent` produces an error naming a sibling `InfoEvent` or `WarnEvent`) - consistent
  with the JVM eagerly resolving all of a sealed class's permitted subtypes when the
  sealed superclass is loaded (per JVMS), and whatever internal JFR bookkeeping is racing
  here operating on the whole permitted-subtype set together rather than per-class.
- **Not reliably reproducible via test ordering alone:** running a project's *entire*
  test suite together, this was never observed - some test, by chance, always committed
  a "warm up" event before the one that would have hit it. It only surfaced once a new
  test method became - by virtue of Maven Surefire's alphabetical-ish class ordering -
  the very first thing in the whole module to combine an active-`Recording` `enable(...)`
  with a first-ever commit. Running that one class/method in isolation
  (`-Dtest=TheClass` or `-Dtest=TheClass#theMethod`) reproduces it every time, including
  for test code that had already been merged and passing for a while under normal
  (non-isolated) `mvn test` runs.
- **Minimal repro shape** (see `rainbowgum-jfr/src/test/java/io/jstach/rainbowgum/jfr/JfrTestSupport.java`
  in this repo for the actual workaround in context):
  ```java
  import jdk.jfr.Event;
  import jdk.jfr.Recording;

  public class SomeEvent extends Event {
  }

  class Repro {
      public static void main(String[] args) throws Exception {
          try (Recording recording = new Recording()) {
              recording.enable(SomeEvent.class);
              recording.start();
              new SomeEvent().commit(); // <-- throws LinkageError here, first run only
          }
      }
  }
  ```
  Run on the module path (`--module-path`, with a trivial `module-info.java` requiring
  `jdk.jfr`) in a fresh JVM. Has not yet been reduced to a single-file, no-Maven,
  no-JUnit repro - the workaround was found and verified sufficient before further
  minimization was attempted.
- **Workaround used in this repo:** each JUnit 5 test class in `rainbowgum-jfr` that
  starts a `Recording` calls a shared `JfrTestSupport.warmup()` from a `@BeforeAll`
  method, which does a bare, recording-free commit of every concrete event subclass once.
  Cheap, idempotent per class (JUnit 5 calls `@BeforeAll` once per class, not once
  globally), and does not depend on any assumption about which test class or method
  happens to run first.
