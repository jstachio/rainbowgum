# Log4j2: `immediateFlush=true` does not mean the logging thread does the flush

Referenced (briefly) in `why_rainbowgum_is_better.md`'s 12 Factor section ("Log4J2
actually buffers events and cannot be turned off... even on synchronous and immediate
flush turned on it does buffer events waiting for a flush winner and the thread that
produced the event may not be the one that finally writes it... we could only get this
buffer to hold a maximum of 3 events"). This is the actual investigation behind that
claim, kept here (same spirit as `doc/logback-json-encoder-bugs.md`) since the summary
in the marketing doc compresses away the evidence. Full detail lives in
`feature/graalvm-native-benchmark`'s `benchmark/native/PROFILING_RESULTS.md` (Findings
1, 4, 5, 6, 7); this is a focused extract of that, not a duplicate of the whole file.

## Versions and environment

- `org.apache.logging.log4j:log4j-core:2.26.1`, default `ConsoleAppender` config
  (`immediateFlush=true`, the default; `Constants.ENABLE_DIRECT_ENCODERS=true`, also the
  default).
- Temurin 26.0.2, plain HotSpot (JIT), not native-image. See "Why plain JVM" below for
  why this specific investigation couldn't also cover Substrate VM directly.
- Isolated profiling harness: same 5 SLF4J log calls as the real HTTP benchmark
  (`benchmark/native/`), run by a fixed pool of platform threads in a tight loop, no HTTP
  server involved. See `PROFILING_RESULTS.md`'s own "Setup" section for the full
  methodology and why (isolating the logging call itself from HTTP/virtual-thread
  variables).

## The mechanism

Log4j2's default `ConsoleAppender` path (`Constants.ENABLE_DIRECT_ENCODERS=true`)
acquires **two separate `synchronized` locks per event**, both on the same
`OutputStreamManager` instance, not one wide critical section:

1. `OutputStreamManager.writeBytes(ByteBuffer)` (`synchronized`, lock acquired,
   released): appends the event's already-encoded bytes into the manager's own
   **shared** internal buffer (`ByteBufferDestinationHelper.writeToUnsynchronized`,
   confirmed by decompiling the real jar: `destBuff.put(source)`, literally the one
   shared buffer, not a per-thread one).
2. A separate call, `OutputStreamManager.flush()` (`synchronized`, a **different**
   lock acquisition on the same monitor): does the real `write()` syscall and the
   actual flush.

The lock is released between steps 1 and 2. That gap is real: a second thread's own
`writeBytes()` call can land in it, append its own event into the same shared buffer,
and then whichever thread's `flush()` gets there first drains **both** events in one
`write()` syscall, not just its own.

Every call to the encode path unconditionally calls its own `manager.flush()` before
returning, so the *originating* thread never returns before its own bytes are
guaranteed to have been drained, either by itself or by whichever thread beat it to the
punch: the two `writeBytes()` calls happens-before any later `flush()`, per the JVM
memory model, since both are `synchronized` on the same monitor. So this is not a
durability or correctness bug: no event is ever lost or corrupted by it. It only changes
**which thread physically issues the `write()` syscall for a given event's bytes**, not
whether or when (relative to the logging call returning) that happens.

## Measured, not inferred

A single-threaded `strace` check first (naively) reported exactly 5 syscalls per
iteration for both Log4j2 and Rainbow Gum, and was initially read as "no buffering
happening." That conclusion was wrong: a single thread can't exhibit a race that only
exists because of concurrent threads interleaving into the gap between `writeBytes()`
and `flush()`. Rerun with real concurrency (`strace -f`, counting `write(1,...)`
syscalls against the harness's own reported iteration count, 5 log calls/iteration):

| | `write()` syscalls | iterations | syscalls/iteration |
|---|---:|---:|---:|
| Log4j2, 8 threads | 42,278 | 8,487 | 4.9815 |
| Log4j2, 32 threads | 74,301 | 14,887 | 4.9910 |
| Log4j2, 64 threads | 62,431 | 12,514 | 4.9889 |
| Rainbow Gum (default, `LOCK_THREAD_LOCAL_BUFFER`), 32 threads (control) | 67,165 | 13,433 | **5.0000** |
| Rainbow Gum (`SYNCHRONIZED_THREAD_LOCAL_BUFFER`), 32 threads (control) | 63,135 | 12,627 | **5.0000** |

Log4j2 consistently lands a small amount below 5.0 (roughly 0.1-0.4% of events riding
along in another thread's flush) at every concurrency level tried. Both Rainbow Gum
appender types hit exactly 5.0000, zero deviation, at the same concurrency: a clean
control ruling out shutdown-timing noise or some other shared explanation. Rainbow Gum
can't exhibit this by construction: the default appender never shares a buffer across
threads at all (thread-local), and the `synchronized` variant's single lock/unlock pair
covers write and flush together, leaving no gap for another thread to land in.

**How many events actually share one syscall?** Checked by capturing full `write()`
payloads (`strace -s 4000`) and counting newlines per syscall, not inferred from the
aggregate deficit alone:

| threads | 1 event/syscall | 2 events/syscall | 3 events/syscall |
|---|---:|---:|---:|
| 32 | 69,865 | 150 | 0 |
| 64 | 64,298 | 149 | 1 |

Almost always exactly 1 (the normal case); 2 is the common form of the race; 3 happened
once, at 64 threads (3.2x oversubscribed relative to the 20-core test machine). The
shared buffer is 8192 bytes and these TTLL lines run 120-150 bytes each, so there is
room for dozens before a forced early drain; nothing hard-caps this at 2 or 3, they are
just increasingly unlikely at the same rate a 3-way race is already rare given a 2-way
one already is.

**Checked for actual data loss or corruption, not assumed safe:** counted lines in a
32-thread run's output file against the expected count (`iterations × 5`): exact match,
74,435 written vs 74,435 expected. Every one of those 74,435 lines matched the expected
well-formed pattern (timestamp, thread, level, logger, message) via regex: zero
truncated or interleaved lines. Coalescing happens at whole-event granularity (each
`writeBytes()` call appends one already-fully-encoded event as an atomic chunk under its
own lock), never mid-line.

## Rainbow Gum tried to copy this and rejected it, on principle, backed by numbers

Two experimental `LogAppender.AppenderType` variants were built specifically to test
whether the same trick would help Rainbow Gum, both kept in the tree as documented
negative results (`SYNCHRONIZED_SHARED_BUFFER`, `SYNCHRONIZED_DEFERRED_FLUSH`, not
recommended configurations):

- **`SYNCHRONIZED_SHARED_BUFFER`** (two genuinely independent monitors: one for
  appending into a shared buffer, one for draining it): **-32.9%** vs Rainbow Gum's own
  default, **-41.8%** vs Log4j2, the worst-performing configuration measured in that
  whole investigation. Root cause, confirmed in the flame graph: because the two locks
  are truly independent, draining can never safely reuse the live shared buffer (a
  concurrent append could still be racing in), so it allocates a fresh backing array on
  *every single drain*, a real allocation on the hot path of every logged event, which
  Log4j2's own single-shared-monitor design never needs.
- **`SYNCHRONIZED_DEFERRED_FLUSH`** (Log4j2's actual shape: the *same* lock, acquired
  twice, write, release, re-acquire, flush): **+3.3%** over Rainbow Gum's own default,
  but still **-10.2%** behind Log4j2 and **-6.9%** behind Rainbow Gum's own
  single-lock `SYNCHRONIZED_THREAD_LOCAL_BUFFER` variant. A real, measured, if modest,
  gain over the plain default, but two lock acquisitions per event still cost more in
  aggregate than one combined acquisition, even when each one individually shows less
  contention.

Beyond the throughput numbers, this line of investigation was closed for a design
reason, not a performance one. Quoting the actual call: *"I don't like how one thread
can dump more events or events it doesn't even own."* Every other `AppenderType` in this
project (on `main`, not just this benchmark branch) keeps one deliberate property:
whichever thread produced an event is the thread that writes and flushes it, full stop.
Both experimental types above (neither merged to `main`, kept only on
`feature/graalvm-native-benchmark` as documented negative results) give that property up
for a throughput benefit that, measured honestly, turned out to be either negative or
too small to justify the tradeoff.

There is also a second, separate cost specific to `SYNCHRONIZED_DEFERRED_FLUSH`, only
when it is paired with an output that genuinely buffers (not the default
`System.out`/`PrintStream`, which still flushes synchronously on every write
regardless): a `kill -9` mid-run test showed the default, unbuffered output's last
written line always matches the kill instant to the millisecond, while a genuinely
buffered output loses whatever is still sitting in the OS-level buffer, unbounded in
the worst case (a standalone demo with a large `BufferedOutputStream` and no `flush()`
call lost tens of megabytes, thousands of events, permanently and undetectably). Log4j2's
own 8192-byte manager buffer carries this same risk at a smaller scale by construction,
`immediateFlush=true` or not: the policy narrows the crash window, it does not remove
it the way never buffering at all does. This is the concrete mechanism behind
`why_rainbowgum_is_better.md`'s [12 Factor](https://12factor.net/logs) ("unbuffered, to
stdout") argument, not just an assertion.

## What this does and doesn't claim

- **Does not claim** Log4j2 loses or corrupts events under normal operation: checked
  directly (exact line counts, well-formed-line regex match) and confirmed it does not.
- **Does not** fully explain Log4j2's overall throughput lead over Rainbow Gum on
  HotSpot. The measured syscall-coalescing benefit (0.1-0.4% fewer syscalls) is far too
  small on its own to account for a 4-15% throughput gap; that gap's remaining causes
  are a `synchronized`-vs-`ReentrantLock` contention story (see `PROFILING_RESULTS.md`
  Finding 1/4) plus other unexplained per-call cost, not this attribution quirk.
- **Does claim**, with direct evidence rather than a decompile-only guess: Log4j2's
  `immediateFlush=true` guarantees an event's bytes are on their way to the OS by the
  time the logging call returns, but not that the calling thread is the one that
  physically issued that `write()` syscall: a real, measured, if narrow (a few tenths
  of a percent of events, capped at a handful per syscall in these runs, not hard-capped
  architecturally), departure from "the thread that logs an event is the thread that
  writes it."
