# Rainbow Gum: triage of blind-review findings

We are going through every Rainbow Gum finding from the blind reviews (the full review write-up, with the method and the reports, is kept with the review VM in `~/vms/review-lab/REPORT.md`) one at a time: the finding as reported, a check against current `main`, Adam's answer, and the outcome.

- **Sources:** runs 1–3 (`reports/run*/corvane.md`), with duplicates merged. Run 2's High shadowing finding is excluded: it was created by the scrub.
- **Checked against:** `origin/main` at `9d1f7fb2` (2026-11-07).

## Already fixed

| Finding | Where it came up | Fixed by |
| --- | --- | --- |
| Reentrant logging during encoding corrupts the per-thread buffer (H1) | Runs 2 and 3 | `2de81354`, `1a8e5df7` |
| The disruptor publisher never started or drained its appenders and blocked after close (H2) | Run 3 | `95a10d31`, `fe7124d9` |
| The disruptor publisher silently took over the `async` scheme | Run 3 | `3cb84c96` |
| `RawJsonWriter.writeDouble` wrote `Infinity`/`NaN` double-quoted and `0.0` as a string | Run 2 | `a064b2ff` |
| A rollover that keeps failing drops every event | Run 2 | `a209863b`, `97d9a162` |

## To triage

| # | Finding | Severity (as reported) | Status |
| --- | --- | --- | --- |
| 1 | A failed `ReopenableFileOutput.reopen()` leaves a closed output installed, and later events are dropped silently | Medium | **fix (A)** |
| 2 | `QueueEventsRouter` (before binding and after close) is unbounded and holds unfrozen events; an event can also be stranded if logged while the queue is being drained | Medium/Low | **fix (A, B, C)**, plus a credential audit |
| 3 | Non-changeable SLF4J loggers stay bound to the closed router after a rebind | Medium | **C (document) for 0.17.2**; **B (closed routes forward to global)** after a lifecycle spec |
| 4 | Every failed append raises an alert printed with its stack trace to stderr, with no rate limit | Medium | **fix (A)** |
| 5 | Text formats do not escape CR/LF or ANSI by default (`crlf` is opt-in, and its output is ambiguous because backslashes are not escaped) | Medium/Low | **docs + fix (A)** |
| 6 | OTLP behind the default sync publisher blocks application threads | Medium | **A (warn) for 0.17.2**; **B (async by default for network outputs)** for the next minor |
| 7 | Rollover gzips the old file on the logging thread while holding the appender lock | Low | **B (document)** plus a startup warning to use the async publisher; A is maybe later |
| 8 | Log files follow the process umask, with no permissions option, so archives (0600) and the active file differ | Low | **fix (A) + docs (C)**; not B |
| 9 | Per-name caches have no bound (`LevelResolver`, `LogConfig`, `Abbreviator`, packaging data) | Low | **code comments + overview docs**; reminder branch `draft/cap-packaging-data-cache` |
| 10 | Public static "constants" are not final | Low | **not a bug** (interface fields); **B (write `final` anyway)** |
| 11 | Legacy `BlockingQueueAsyncLogPublisher`: throws after close and has no drop policy | Low | **deprecated** (`ed0a0890`); remove in the next minor |
| 12 | `RainbowGum.set` unbinds the previous instance without closing it, and the instance-ID check is a "hack" with a known race | Low | **fix (A) + docs (C)**; B goes into the lifecycle spec |
| 13 | `ReplaceableLogger.setLevel` and `setEventHandler` update their state non-atomically | Low | **fix (A): remove the unused methods** |
| 14 | `ThrottleFilter` uses the wall clock, so a clock step back stalls it | Low | **fix (B): detect a backward step and reset** |
| 15 | `ArrayMDCAdapter.pushByKey` and `popByKey` do nothing | Low | **D: keep the no-op, warn once through alerts**; document it as an SLF4J limitation |
| 16 | `catch (Throwable)` in `safeObjectAppend` also swallows VM errors | Low | **no change (A)** |
| 17 | A JVM shutdown hook is always registered, with no opt-out | Low | **fix (A): opt-out property + docs (B)**; include in the lifecycle spec |
| 18 | `RollingPolicy` `Integer.parseInt` on an archive index can overflow (suspected) | Low | **fix (A): skip indexes that do not parse, and raise an alert** |
| 19 | Maintainability: very large files, duplicated appender variants, near-duplicate Spring Boot 3 and 4 modules, TODOs and "hack" comments, placeholder API (`LoggerAPI`, `LogEventLogger`, `LogAppenderRegistry`) | — | **d and e before 1.0**; a–c rejected |
| 20 | `LOCK_NEW_BUFFER` cannot detect reentry during encoding, so `REENTRY_DROP` does not apply to it (noted during the H1 fix) | Note | **document (A)** |

## Discussion

<!-- One section per finding: the finding, the check against main, Adam's answer, the outcome. -->

### 1. A failed reopen leaves the file output silently dropping events

- **Reported** (run 3, M1): `ReopenableFileOutput.reopen()` closes the current output and then opens a new one. If opening fails, the closed output stays installed. Closed outputs ignore writes, so every later event is dropped with no error or metric. The rolling output recovers from the same situation; the plain file output does not.
- **Checked on main:** confirmed. `rainbowgum-file/.../FileOutput.java:260` runs `close()`, then `supplier.get()`, then `start()`, and `AbstractOutputStreamOutput.write` returns early once the output is closed. Reopen is triggered by `LogAction.REOPEN` (logrotate followed by a signal), so if it fails the service keeps running while its file log goes quiet.
- **Options:**
  - **A.** Open the new output before closing the old one. If that fails, keep the old one, raise an alert, and retry on the next reopen.
  - **B.** Close first, and install an output that retries opening on the next write.
  - **C.** Only count and alert on the dropped writes.
- **Decision:** fix with **A**.

### 2. The global queue router: unbounded, unfrozen, and racy

- **Reported** (run 3 M2, run 2 #7 and #8): before a Rainbow Gum is bound, and again after it is closed, the global router is a `QueueEventsRouter`. Its `ConcurrentLinkedQueue` has no bound and accepts INFO and above. Events go in unfrozen, so their arguments stay alive and are formatted later on another thread. And `GlobalLogRouter.log` reads the delegate and then adds the event, so a concurrent drain can leave the event in an orphaned queue.
- **Checked on main:** all three confirmed in `LogRouter.java`:
  - `QueueEventsRouter.log` calls `events.add(event)` with no freeze and no cap;
  - `GlobalLogRouter.log` reads the delegate, then adds, with no re-check;
  - after close (line 1055) the delegate becomes a fresh queue router.

  A service that closes Rainbow Gum but keeps running (a redeploy, tests, Spring's clean-up and re-initialisation) keeps filling it.
- **Options:**
  - **A.** Freeze each event as it is queued.
  - **B.** Cap the queue with a property such as `logging.global.queue.size` (default around 1,000), and report the drop count as one alert when the queue is drained.
  - **C.** Close the race, by draining the old queue again after the swap or re-checking the delegate after adding.
  - **D.** Drop events after close instead of queueing them.
- **Decision:** **A, B and C**, not D. Keeping a queue after close is useful when something re-initialises, and with a cap it is safe.
- **Follow-up (Adam):** we have fixed credential leaks in diagnostics, but this path, before binding and after close, including the echo of ERROR events to stderr through `MetaLog`, is the kind of path where one could still happen. Audit it while fixing.

### 3. SLF4J loggers stay bound to a closed Rainbow Gum after a rebind

- **Reported** (run 3, M3): with change mode off (the default), the SLF4J factory binds a logger directly to its route. `loggerMap` is not cleared on a rebind; only `unboundLoggerMap` is. After a close and a new bind, existing loggers, including `static final` ones, keep writing to the closed router, and the closed outputs drop those writes silently.
- **Checked on main:** confirmed in `rainbowgum-slf4j/.../RainbowGumLoggerFactory.java`: lines 141–150 bind the logger directly, and line 91 clears only the unbound map. A normal app binds once; tests, Spring's clean-up and re-initialisation, redeploys and `RainbowGum.set` all hit this.
- **Options:**
  - **A.** Make every logger replaceable (a volatile read on every call).
  - **B.** Closed routes forward to `LogRouter.global()`, in core, for every facade.
  - **C.** Document the binding.
  - **D.** Also clear `loggerMap` on a rebind.
- **Decision:**
  - **Not A.**
  - **C** as its own branch for **0.17.2**: documentation only, no behaviour change.
  - **B** later, after careful design. First write a Markdown spec of the exact lifecycle of SLF4J and Rainbow Gum, covering every variant: unbound queue, bind, change modes, close, rebind, `set`/`unset`, and the other facades.
  - **E (added by Adam, to explore).** Some form of alerting when loggers are stale: a warning that loggers created from a closed Rainbow Gum are still being used after a rebind. The exact mechanism is still open. Weigh it against B in the lifecycle spec.
- **Adam's reasoning:**
  - C is not as bad as it looks. Most other logging frameworks carry a lot of global static state, and reloading the logging system only happens while certain frameworks bootstrap.
  - He doubts Spring Boot will go the distance with LLMs: agents do not need easy convenience, they need security and stability.
  - Not changing loggers at runtime is the better default.

### 4. Append failures flood stderr with stack traces

- **Reported** (run 3 M4, and run 2's weakness list): every failed append raises an ERROR alert, and `LogAlerts` prints every WARNING or ERROR to stderr through `MetaLog` with a full stack trace and no rate limit. A full disk or a broken output prints one stack trace per log call. The default async publisher already alerts only once per reason.
- **Checked on main:** confirmed. `LogAppender.java` raises the alert at six sites, and `LogAlerts.java` (about line 708) calls `MetaLog.INSTANCE.log(frozen)` for each one. The alert ring and the metrics are bounded or exact; stderr is not.
- **Options:**
  - **A.** Once per failure episode, per appender: print the first failure with its stack trace, count the rest, and print one "recovered after N failed appends" line on the next success.
  - **B.** Time-based rate limiting in `LogAlerts`.
  - **C.** Stack trace only the first time.
- **Decision:** **A**.
- **Adam's reasoning:** a system that is failing is unhealthy and needs a restart or redeploy. More error output only increases the blast radius: it turns one failure into a cascading one, because the logging makes things worse.

### 5. Text formats do not escape CR/LF or terminal escapes by default

- **Reported** (all three runs): with the default TTLL and pattern encoders, untrusted text can forge lines with CR/LF or send ANSI escape codes to a terminal. `crlf` is opt-in, handles only `\r` and `\n`, and does not escape backslashes, so its output is ambiguous.
- **Checked on main:** confirmed. `StandardPostProcessor.CRLF` (`LogFormatter.java:1020`) is opt-in, and `escapeLineBreaks` leaves backslashes, ESC and the other C0 controls, NEL and U+2028/2029 alone. The structured formats (JSON, logfmt, LTSV, OTLP) already escape correctly.
- **Compared:**
  - Neither Logback nor Log4j escapes by default. Log4j's `%m` is raw unless you use `%enc{%m}{CRLF}`; Logback's `%msg` is raw unless you hand-write a `%replace`.
  - The blind reviewers flagged the same thing for both of them. It is a standard checklist item (CWE-117, OWASP).
- **Options:**
  - **A.** Make the opt-in `crlf` complete and unambiguous: backslash becomes `\\`; ESC and other C0 controls become `\u001b` and so on; NEL and U+2028/2029 are escaped.
  - **B.** Escape control characters in logged content by default.
  - **C.** Docs only.
- **Decision:** **docs, plus A.** Default output does not change.
- **Docs:**
  - Say plainly that TTLL and pattern output are for humans and not a parse target. Anything that is parsed or contains untrusted data should use a structured format.
  - In `doc/overview`, add a **note callout** that the lead developer vastly prefers **LTSV**: it is parseable and human-readable, and it does not have these problems. For historical reasons it is not Rainbow Gum's default. Match the existing callout markup.
- **Adam's view:** TTLL is not meant for parsing, so the reviewers' focus on this is mostly a checklist effect.

### 6. OTLP behind the default sync publisher blocks application threads

- **Reported** (run 2, #5): with the default synchronous publisher and immediate flush, every log call to an OTLP route makes its own blocking HTTP request (a 10 s timeout, plus retries), stalling application threads. It is documented but not enforced.
- **Checked on main:** confirmed. The `OtlpOutput` Javadoc says to use an async publisher. `OutputType.NETWORK` exists, but core never uses it to choose a publisher. A collector that stalls stalls the service: the blast radius again.
- **Options:**
  - **A.** Warn at startup when a network output sits behind a synchronous publisher.
  - **B.** Default to the async publisher when a route does not name one and has a `NETWORK` output; an explicit `sync` is still honoured.
  - **C.** Fail startup.
  - **D.** Leave it documented.
- **Decision:** two branches, like finding 3. **A** (the warning) goes in the next patch, 0.17.2. **B** (async by default for network outputs) changes behaviour, so it waits for the next minor.

### 7. Rollover gzips the old file on the logging thread, under the appender lock

- **Reported** (runs 2 and 3): size-based rolling runs inside `write()`, and with a `.gz` pattern it compresses the whole old file under the appender lock. With the default sync publisher, every logging thread stalls for the duration.
- **Checked on main:** confirmed. `DefaultRollingFileOutput.maybeRoll()` (line 140) calls `RollingPolicy.roll`, which runs `gzip(activeFile, target)` inline (line 92). A 100 MB file takes about 1–3 s.
- **Compared:**
  - Logback compresses on a background executor, which the reviewer found shared with socket appenders, so compression can be starved.
  - Log4j compresses asynchronously, but blocks the next rollover on a semaphore.
- **Options:**
  - **A.** Rename under the lock, then compress on a background thread.
  - **B.** Keep it synchronous, and document using the async publisher or skipping `.gz`.
  - **C.** Compress the previous archive on the next roll.
- **Decision:** **B**, plus the same kind of startup warning as finding 6: a rolling output behind the sync publisher gets a suggestion to use the async publisher. **A** might become another branch later.
- **Adam's reasoning:**
  - Someone logging to disk with rolling, rather than to stdout, usually cares about performance, and the async publisher gives more of it.
  - The docs already stress using external systems for rotation (k8s, journald, logrotate), so in-process compression is a low priority.

### 8. Log file permissions follow the umask, and archives end up different

- **Reported** (all three runs): files are created with the process umask and there is no permissions option. Gzipped archives come from `Files.createTempFile` (0600) and are then moved, so they are 0600 while the active file is typically 0644 or 0664. A shipper or operator running as another user can read the current log but not the archives.
- **Checked on main:** confirmed:
  - `FileOutput.java:148` uses `new FileOutputStream`, which follows the umask;
  - `RollingPolicy.java:199` calls `Files.createTempFile` (0600), then `Files.move` at line 205;
  - plain archives keep the original file's mode, so only the `.gz` ones differ.
- **Compared:**
  - Log4j has `filePermissions`, `fileOwner` and `fileGroup`, applied after creation, which leaves a window the reviewer flagged.
  - Logback uses the umask.
- **Options:**
  - **A.** Copy the source file's POSIX permissions onto the gzip archive before moving it.
  - **B.** A `filePermissions` option, applied atomically at creation.
  - **C.** Document that permissions follow the umask.
- **Decision:** **A + C.** Not B: Adam considers it an easy footgun to get wrong.

### 9. Per-name caches have no bound

- **Reported** (run 2, #9): the `LogConfig` change and caller caches, the level resolver caches, the `Abbreviator` cache and `PackagingDataResolver` are all `ConcurrentHashMap`s with no eviction. They leak if logger names come from request data, or if packaging data keeps meeting new generated classes.
- **Checked on main:** confirmed. The context matters, though:
  - Every framework caches loggers by name forever.
  - Rainbow Gum is the only one that makes that visible. `LogMetrics.LOGGER_NAMES_METRIC` (`logger.names`) counts distinct logger names through `DefaultLoggerRegistry.registerLoggerName`, and every facade registers (SLF4J, System.Logger, the JUL LogManager, JBoss Logging, the Log4j 2 API, avaje AppLog), so a count that keeps climbing shows the misuse. The LOGGERS report section lists the names.
  - `doc/overview.html` does not mention any of this yet.
- **Packaging data:**
  - It is in core (`LogFormatter.java`, `PackagingDataResolver`) and strictly opt-in: `ThrowableFormatter.Builder.packagingData(boolean)` defaults to `false`, and each formatter gets its own cache.
  - Its keys are stack-trace class names, which `logger.names` does not cover.
  - It can grow without bound while the JVM stays healthy, because classes are unloaded but the cached names stay. The example is per-request Groovy evaluation: `GroovyShell.evaluate(...)` compiles `Script1`, `Script2`, … on new class loaders, and each logged failure adds an entry.
  - Similar: per-request JSR-223 or Janino compilation, and ByteBuddy or CGLIB proxies generated per instance with random suffixes. OSGi and app-server redeploys mostly repeat names.
- **Decision:**
  - Code comments on every logger-name cache, pointing to `logger.names` and the LOGGERS report.
  - A comment on the packaging-data cache: keys are class names, not covered by the metric; it is opt-in; it only grows with generated classes.
  - A short paragraph in `doc/overview.html`: logger names should come from a bounded set, and `logger.names` and the LOGGERS section make growth visible.
  - No cap for now. A **reminder branch** `draft/cap-packaging-data-cache` will hold a draft cap, plus the option of filtering classes that cannot be loaded and hidden or lambda classes. Filtering alone would not cover Groovy `ScriptN`.

### 10. Public static "constants" are not final

- **Reported** (run 2, #10): `LogPublisherRegistry.ASYNC_SCHEME` and the other schemes, `LogOutputRegistry.LIST_OUTPUT_SCHEME`, `RainbowGumServiceProvider.PASSES`, `Configurator.CONFIGURATOR_PASSES` and `LogFormatter.TTLL_TIME_FORMAT` are `public static` without `final`.
- **Checked on main:** **not a bug.** Every one of them is declared in an interface (`LogPublisherRegistry`, `LogOutputRegistry`, `RainbowGumServiceProvider`, `Configurator`, `TimestampFormatter`), and interface fields are implicitly `public static final`. The reviewer misread them.
- **Options:**
  - **A.** No change.
  - **B.** Write `final` explicitly. It is redundant to the compiler but stops readers, human or LLM, from making the same mistake.
- **Checked:** with `final` added to `ASYNC_SCHEME` in a throwaway worktree, Error Prone (`_run_modules=core bin/analyze.sh errorprone`) passes cleanly. ECJ (`eclipse`) fails only on its known, unrelated generated-source and module errors, with nothing about the field.
- **Decision:** **B**, since Error Prone accepts it. Adam had considered `public static /* it is final */ String` as an alternative but rejected it.

### 11. The legacy `BlockingQueueAsyncLogPublisher`

- **Reported** (all three runs): this public publisher throws `IllegalStateException` to the application thread after close, or after its worker has died, and blocks callers when its queue is full, with no drop policy.
- **Checked on main:** nothing in production code uses it, and no scheme is registered for it; only its own four test classes use it.
- **Decision:** already handled. Adam had deprecated it on a branch, and merged it during this triage as `ed0a0890` ("Deprecate the example blocking queue async publisher"), which marks it `@Deprecated(forRemoval = true)` and points to `BatchSwapAsyncLogPublisher`. Remove it, with its tests, in the next minor.

### 12. `RainbowGum.set` replaces the bound instance without closing it; binding has a known race

- **Reported** (run 3 L7, and run 2's design notes):
  - `set(...)` unbinds the previous instance without closing it.
  - `set(RainbowGum)` and `Builder.set()` bind first, then call `RainbowGum.of()` separately, and compare instance IDs afterwards. Another thread can bind in between, which the code itself calls "TODO this is a hack" and reports as a "rare reace condition".
- **Checked on main:** confirmed. `RainbowGumHolder.set` (`RainbowGum.java:691`) clears the bound instance and installs the new supplier, but never closes the original. The "hack" sites are at lines 285 and 549, each with the "reace" typo. Mitigation: started instances are registered with `ShutdownManager`, so a replaced one still closes at JVM exit.
- **Options:**
  - **A.** A holder method that binds and starts under the write lock and returns that exact instance, which removes the ID check, both TODOs and the typo.
  - **B.** Close the replaced instance, or warn about it.
  - **C.** Document that `set` does not close the previous instance.
- **Decision:** **A + C** now. **B** goes into the lifecycle spec from finding 3, together with the stale-logger alerting idea (E).

### 13. `ReplaceableLogger.setLevel` and `setEventHandler` are not atomic

- **Reported** (run 3, L4): each method reads the `volatile State` and writes a new one, so a concurrent level change and router change can lose an update.
- **Checked on main:** the code is as described, but production never calls these methods. The factory's change subscription (`RainbowGumLoggerFactory.java:212`) uses the atomic `set(level, handler)`. `setLevel` and `setEventHandler` only implement the package-private, unexported interfaces `LevelChangeable` and `LogEventHandler.EventHandlerChangeable`, which tests use only in `instanceof` checks. No update can be lost today; the methods are a trap for future callers.
- **Options:**
  - **A.** Remove them and keep only `set(level, handler)`, simplifying the two interfaces and the tests.
  - **B.** Make them atomic with a CAS loop.
  - **C.** No change.
- **Decision:** **A.** Adam thinks they were added by accident.

### 14. `ThrottleFilter` uses the wall clock

- **Reported** (run 3, L1): the token bucket uses each event's wall-clock timestamp. A clock step backwards leaves `allowedAt` in the future, and every throttled-level event is dropped until the clock catches up.
- **Checked on main:** confirmed. In `core/.../filter/ThrottleFilter.java`, `filterOrNull` uses `now = nanos(event.timestamp())`, and `allowedAt` only ever moves forward. Daylight saving does not affect it, since timestamps are UTC; real clock steps do. Using the event timestamp keeps the tests deterministic.
- **Options:**
  - **A.** Use the monotonic clock (`System.nanoTime()`), with an injectable source for tests.
  - **B.** If `allowedAt` is more than `burst + interval` ahead of `now`, the clock went back, so reset to `now`. At worst one extra burst gets through.
  - **C.** No change.
- **Decision:** **B.**

### 15. `ArrayMDCAdapter`'s deque-by-key methods do nothing

- **Reported** (run 3, L8): SLF4J 2.0's per-key deque API is implemented as no-ops. `pushByKey` and `clearDequeByKey` do nothing, and `popByKey` and `getCopyOfDequeByKey` return `null`.
- **Checked on main:** confirmed (`rainbowgum-slf4j/.../ArrayMDCAdapter.java:202–218`). Logback and SLF4J's `BasicMDCAdapter` implement it; only `NOPMDCAdapter` is a no-op. Deque values are not part of the MDC map, so no layout prints them; the API only stores values and gives them back.
- **Options:**
  - **A.** Delegate to SLF4J's `org.slf4j.helpers.ThreadLocalMapOfStacks`.
  - **B.** Throw `UnsupportedOperationException`.
  - **C.** Document the no-op.
- **Decision:** **D (Adam).** Keep the no-op, and raise **one and only one** warning through the alert system the first time any of the four methods is used.
- **Adam's reasoning:**
  - Implementing it (A) would be disingenuous: the values would not be read downstream, since nothing prints them.
  - The API has a history of bug reports and problems.
- **Docs (Adam):** list it as a limitation in the SLF4J section of `doc/overview.html`. The deque-by-key MDC API is not supported, its methods do nothing, and using it raises a single alert. Libraries really should not be calling that API.
- **Implementation note:** the adapter can exist before a Rainbow Gum is bound, so work out how it reaches the alerts. One option is a static once-only flag, with the alert raised through the bound configuration, or through `MetaLog` when nothing is bound.

### 16. `catch (Throwable)` in `safeObjectAppend`

- **Reported** (run 2 #14, run 3 L9): when an argument's `toString()` throws, `LogMessageFormatter.safeObjectAppend` catches `Throwable`, so OOM and `StackOverflowError` are swallowed too.
- **Checked on main:** confirmed (`LogMessageFormatter.java:475–483`). This is ported SLF4J code: SLF4J's `MessageFormatter`, and therefore Logback, and Log4j's `ParameterFormatter` all catch `Throwable`. The main real-world trigger is a recursive `toString()` that overflows the stack, which we want contained.
- **Options:**
  - **A.** No change.
  - **B.** Rethrow `VirtualMachineError` except `StackOverflowError`.
  - **C.** Append the exception class name to the output.
- **Decision:** **A.** If the JVM really is in trouble, something else further up the stack will raise it.

### 17. A JVM shutdown hook is always registered, with no opt-out

- **Reported** (run 2, #12): starting the global Rainbow Gum always registers a shutdown hook that closes it. JVM hooks run concurrently in no set order, so logging from other hooks after Rainbow Gum's has run goes to the post-close queue and is lost; only ERROR events still reach stderr.
- **Checked on main:** confirmed. `RainbowGum.java:724` calls `ShutdownManager.addShutdownHook(gum)` on every start, and `ShutdownManager` (`LogLifecycle.java`) has no property or API to skip it. Both Spring integrations' `getShutdownHandler()` return `super`.
- **Compared:**
  - Log4j registers a hook by default, with an opt-out (`shutdownHook="disable"`, `log4j2.shutdownHookEnabled=false`), and Spring Boot turns it off.
  - Logback registers none by default (`<shutdownHook/>` opts in).
- **Options:**
  - **A.** An opt-out property such as `logging.global.shutdownHook=false`, default `true`.
  - **B.** Document the ordering.
  - **C.** No change.
- **Decision:** **A + B.** Adam had meant to add the opt-out already and forgot. It is also part of the lifecycle spec from finding 3, since shutdown is the last step of that lifecycle.

### 18. An oversized archive index in the log directory breaks rolling

- **Reported** (run 2, #15, suspected): `RollingPolicy` matches archive names with `prefix(\d+)suffix` and uses `Integer.parseInt`, which overflows on a large number.
- **Checked on main:** confirmed. The regex at line 75 has no length limit, and line 189 calls `Integer.parseInt(matcher.group(1))`. The `NumberFormatException` escapes the scan, so every rollover fails while such a file exists; after the recent rolling fixes, events are kept but the active file keeps growing. The trigger is anything with write access to the log directory creating, for example, `app.log.99999999999.gz`.
- **Options:**
  - **A.** Skip names whose index does not parse, catching per file.
  - **B.** Limit the regex to `\d{1,9}`.
  - **C.** No change.
- **Decision:** **A**, and a skipped file raises an alert (a warning or an error), so it is visible rather than silently ignored.
- **Correction found while fixing:** the archive scan only runs in `cleanHistory` (with `cleanHistoryOnStart`), not on every rollover; `roll()` walks indexes up to `maxHistory` directly. So the real effect was that such a file made the rolling output fail to **start**. The fix is the same.

### 19. Maintainability

- **Checked on main:**
  - (a) Multi-class files: `LogProperties.java` 2,208 lines, `LogFormatter.java` 1,764, `LogProperty.java` 1,752 (including `LogRedactor`), `LogAppender.java` 1,661, `LogConfig.java` 1,498.
  - (b) Four near-identical appender variants in `LogAppender.java`.
  - (c) Spring Boot 3 and 4 `RainbowGumLoggingSystemFactory` differ by about 31 lines after renaming packages.
  - (d) 46 TODO/FIXME markers in main code.
  - (e) Placeholder public API:
    - `LoggerAPI` ("currently informational only");
    - `LogEventLogger` ("TODO rename this to LogEventSink");
    - `LogAppenderRegistry` ("TODO probably can remove this").
- **Decision:**
  - **19d, yes.** Go through the TODO/FIXME markers once before 1.0: delete the stale ones and turn the real ones into issues. The two "hack" TODOs are covered by finding 12.
  - **19e, yes.** Decide `LoggerAPI`, the `LogEventLogger` rename and `LogAppenderRegistry`'s removal before 1.0, as public API that will be frozen.
  - **19a, 19b, 19c rejected.**
- **Adam's reasoning:** "large files" and "large flat package" are not defects. The JDK has massive packages and massive classes, and that does not make its code bad. The explicit appender variants and the Spring modules stay as they are.

### 20. `LOCK_NEW_BUFFER` cannot detect reentry during encoding

- **Source:** noted while fixing H1 (`2de81354`), not reported by the reviewers.
- **Detail:** the three appender types that reuse buffers now treat reentry during encoding as reentry, so `REENTRY_DROP` and `REENTRY_LOG` apply. `LOCK_NEW_BUFFER` keeps no per-thread state by design ("a hard guarantee of no ThreadLocal") and encodes outside the lock, so the flags only apply to reentry during its write. Nothing is corrupted, since every event gets a fresh buffer. `AppenderReentrantEncodeTest` pins this down.
- **Options:**
  - **A.** Document it in the `REENTRY_DROP`/`REENTRY_LOG` Javadoc.
  - **B.** Add a thread-local mark, which breaks its guarantee.
  - **C.** No change.
- **Decision:** **A.**

## Work plan

### Patch (0.17.2): bug fixes, warnings and docs, no default behaviour changes

| Finding | Work |
| --- | --- |
| 1 | Reopen opens the new output before closing the old one; keep the old one on failure, alert, and retry on the next reopen |
| 2 | Queue router: freeze on enqueue, cap with a property and a drop count, close the drain race; audit the path for credential leaks |
| 3 C | Document that loggers stay bound to the instance they were created from when change mode is off (its own branch) |
| 4 | Append failures alert once per episode per appender, then print one "recovered after N" line |
| 5 | `crlf` also escapes backslash, the other control characters, NEL and U+2028/2029; document that TTLL and pattern output are for humans; add the LTSV note callout to `doc/overview` |
| 6 A | Startup warning when a network output is behind a synchronous publisher |
| 7 | Document the async publisher for rolling output; startup warning when rolling output is behind a synchronous publisher |
| 8 | Gzip archives get the source file's permissions; document the umask |
| 9 | Comments on the logger-name caches (`logger.names`) and the packaging-data cache; a paragraph in `doc/overview`; reminder branch `draft/cap-packaging-data-cache` |
| 10 | Write `final` explicitly on the interface constants |
| 12 | Atomic bind-and-start in the holder, removing the ID check, both "hack" TODOs and the typo; document that `set` does not close the previous instance |
| 13 | Remove `ReplaceableLogger.setLevel` and `setEventHandler`, keeping only the atomic `set` |
| 14 | `ThrottleFilter` resets the bucket after a backward clock step |
| 15 | Deque-by-key MDC methods raise one alert on first use; listed as an SLF4J limitation in `doc/overview` |
| 17 | `logging.global.shutdownHook` opt-out (default on); document the shutdown hook ordering |
| 18 | Skip archive names whose index does not parse, with an alert |
| 20 | Javadoc on `REENTRY_DROP`/`REENTRY_LOG` for `LOCK_NEW_BUFFER` |

### Next minor

| Finding | Work |
| --- | --- |
| 6 B | Async by default for routes with a `NETWORK` output and no named publisher |
| 11 | Remove `BlockingQueueAsyncLogPublisher` and its tests (deprecated in `ed0a0890`) |

### Before 1.0

| Finding | Work |
| --- | --- |
| 19 d | Go through the TODO/FIXME markers once |
| 19 e | Decide `LoggerAPI`, the `LogEventLogger` → `LogEventSink` rename, and removing `LogAppenderRegistry` |

### Lifecycle spec first (design before code)

A Markdown spec of the exact lifecycle of SLF4J and Rainbow Gum and all their variants, covering the unbound queue, bind, change modes, close, rebind, `set`/`unset`, shutdown and the other facades. It then decides:

- **3 B:** closed routes forward to the global router;
- **3 E:** an alert when loggers are stale;
- **12 B:** close or warn when an instance is replaced;
- **17:** how the shutdown hook fits in.

### Maybe later

- **7 A:** background compression on rollover.

## Patch implementation status

Each finding is on its own branch off `main` (`ed0a0890`), except the docs, which share one branch. All are pushed, and none is merged yet. Merged together on a throwaway integration branch, the full `clean install` and `bin/analyze.sh` both pass.

| Finding | Branch | Commit |
| --- | --- | --- |
| 1 | `fix/reopen-keeps-old-output` | `802f043a` |
| 2 | `fix/bounded-frozen-global-queue` | `dc628910` |
| 4 | `fix/append-failure-alert-once` | `efe4fead` |
| 5 A | `fix/crlf-complete-escaping` | `fab94249` |
| 6 A | `fix/warn-slow-outputs-behind-sync` | `db34d0a9` |
| 8 A | `fix/gzip-archive-permissions` | `c742cf98` |
| 10 | `chore/explicit-final-interface-constants` | `dbe9d245` |
| 12 A | `fix/atomic-global-bind` | `8e7b2117` |
| 13 | `fix/remove-replaceable-logger-setters` | `c35a4cf0` |
| 14 | `fix/throttle-clock-step` | `75ecd454` |
| 15 | `fix/mdc-deque-warn-once` | `7c0cdb3a` |
| 17 A | `fix/shutdown-hook-opt-out` | `80d8ef3d` |
| 18 | `fix/rolling-skip-foreign-archives` | `604e0885` |
| 3 C, docs for 5, 7, 8 C, 9, 12 C, 15, 17 B, 20 | `docs/review-triage-docs` | `fa2b3650` |
| 9 (reminder, not for merging) | `draft/cap-packaging-data-cache` | `658dff99` |

**Merge conflicts to expect:**

- Findings 2 and 17 both add a property after `logging.global.queue.error` (`LogProperties`, `LogConfig.reportProperties`) and a line to the same golden reports (`LogConfigDebugTest`, `LogReporterTest`, `LogReporterKitchenSinkTest`). Keep both, with `queue.size` before `shutdownHook`. In `LogProperties` the conflict boundary falls inside the Javadoc, so check that the shutdown hook constant's comment still starts with `/**` (the integration merge needed that fixed).
- Findings 8 and 18 both add imports to `RollingPolicyTest`. Keep both.

**Open item, finding 7's warning:** core cannot recognise a rolling output (to core it is a `FILE`), so warning when one is behind a synchronous publisher needs a new public hint on `LogOutput` or a new `OutputType`. That is an API decision, so only finding 7's docs are done.

**Credential audit, finding 2's follow-up:** before binding and after close, ERROR events are echoed to stderr through `MetaLog` in full: the level, logger, message and throwable, including exception messages throughout the stack trace, under a `RAINBOW_GUM` label. Key values are not printed. This is the application's own content, not Rainbow Gum's diagnostics, but it can send text meant for a protected log file to stderr, which may be collected differently. Not changed; a decision for the lifecycle spec.

**Correction to finding 18:** see that section. The archive scan only runs when history is cleaned at start.

