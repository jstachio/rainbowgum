# Post-v0.11.2 quick pass: four frameworks, four different corners

A quick, deliberately short exploratory pass on top of the rebuilt `feature/graalvm-native-benchmark`
branch (now built on `main` at `v0.11.2`, which already includes the `DirectByteBufferBuffer`
`getChars()` encoding fix - see [RESULTS.md](RESULTS.md)'s "The gap closes" section). Not a rigorous,
isolated, single-variable comparison like the rest of this benchmark - this is "just to see what we're
working with" before deciding what's worth a real (longer, repeated, single-variable) run.

## Methodology

Treats Rainbow Gum's two thread-local appender types as two effectively different "frameworks" for
this pass, for four total:

* **rg-sync** - Rainbow Gum, `APPENDER_TYPE=SYNCHRONIZED_THREAD_LOCAL_BUFFER`
* **rg-lock** - Rainbow Gum, default appender type (`LOCK_THREAD_LOCAL_BUFFER`)
* **log4j2** - Log4j2 (default config)
* **logback** - Logback (default config)

Each of the three independent axes this benchmark supports - JVM mode (native-image vs HotSpot),
thread model (virtual vs platform, both client and server side via the driver's new `--threads
platform` flag and each app's new `THREAD_TYPE=PLATFORM`), and log format (TTLL vs GELF) - has two
possible values, for 8 possible corners of the cube. Rather than run all 8 for all 4 frameworks (32
tests), each framework was pinned to **one** different corner, spread across the cube so each axis
value appears exactly twice across the four:

| framework | JVM mode | threads | format |
|---|---|---|---|
| rg-sync | native-image | virtual | TTLL |
| rg-lock | HotSpot | platform | GELF |
| log4j2 | native-image | platform | GELF |
| logback | HotSpot | virtual | TTLL |

**This means no single axis is isolated in this table** - rg-sync vs rg-lock, for instance, differs
in JVM mode *and* threads *and* format, not just appender type. Read this pass as "does anything here
look surprising enough to chase with a real, controlled run", not as a ranking.

All four write to plain stdout (the console appender/handler in each app's default config - no file
output involved anywhere in this pass).

GraalVM 25.3.4 CE (native-image), Temurin 26.0.2 (HotSpot) - same JDKs as the rest of this benchmark.
Driven by `rainbowgum-benchmark-native-driver`, concurrency 50, 3s warmup (discarded) + 15s measured,
against `GET /greet/world`, **3 runs each** (short by this file's own usual standard - this is
explicitly a first pass, not the final word).

**Known harness quirk, not a data-validity issue**: the `rg-lock` and `log4j2` runs (the two using
`--threads platform` on the driver's own client side) had the driver process itself hang past its
15s+3s window before this script's own 40s watchdog `kill -9`'d it - but in every case the result row
was already computed and printed (and written to the CSV) before that happened, so the numbers below
are real. Looks like `ExecutorService.close()` on a platform-thread pool behaves differently under
shutdown than the virtual-thread default; not investigated further this pass.

## Results

Averaged over 3 runs each:

| framework | corner | throughput | p50 | RSS avg |
|---|---|---:|---:|---:|
| rg-sync | native, virtual, TTLL | **89,961 req/s** | 0.46 ms | 118 MB |
| log4j2 | native, platform, GELF | 64,884 req/s | 0.66 ms | 84 MB |
| logback | HotSpot, virtual, TTLL | 41,396 req/s | 1.23 ms | 605 MB |
| rg-lock | HotSpot, platform, GELF | 36,491 req/s | 1.33 ms | 586 MB |

## What actually jumps out

* **JVM mode dominates everything else in this pass.** The two native-image rows (rg-sync, log4j2)
  both beat both HotSpot rows (logback, rg-lock) by a wide margin on both throughput and RSS, despite
  rg-sync/log4j2 also carrying the (presumably harder) GELF format in log4j2's case. This isn't new -
  every native-vs-HotSpot comparison elsewhere in this benchmark shows the same gap - but it's a good
  sanity check that this pass's numbers aren't nonsense: the biggest, most-expected effect is exactly
  the one that shows up biggest here too.
* **rg-sync's 89,961 req/s is the highest number recorded anywhere in this benchmark to date** (see
  [RESULTS.md](RESULTS.md) for comparison), though it isn't a clean comparison against those earlier
  numbers - different sandbox session (this file's own methodology note about sandbox noise applies
  here too), and this row is TTLL/virtual/native specifically, the "easy" corner of the cube.
* **RSS is dominated by JVM mode, not framework**: the two HotSpot rows (logback 605 MB, rg-lock
  586 MB) are both roughly 5-7x either native-image row (84-118 MB), regardless of which framework or
  format. Consistent with every other RSS finding in this benchmark - native-image's memory win is
  the least ambiguous result this whole benchmark produces, and this pass is no exception.
* **rg-lock (586 MB, HotSpot) actually edges out logback (605 MB, HotSpot) on RSS** despite carrying
  GELF instead of TTLL - the opposite of what "more work per event" would predict if format dominated.
  Consistent with the RSS story above: JVM mode is doing essentially all the work here, format and
  framework are second-order.
* **log4j2 (native, platform, GELF) beating rg-lock (HotSpot, platform, GELF) by ~78% throughput**
  is almost certainly the JVM-mode gap again, not a framework difference - there's no same-JVM-mode
  pair in this table to actually check that.

## What this pass does *not* tell us

Every comparison above is confounded by at least one other axis - this pass cannot say whether
Rainbow Gum's appender-type choice, GELF vs TTLL, or virtual vs platform threads individually matters
here, only that JVM mode clearly does. If any of these four numbers looks worth chasing, the next step
is a real controlled run (single axis varied, other two held constant, longer duration, more runs) -
exactly this benchmark's usual methodology, not this pass's.

## Not yet done

* Isolate JVM mode from the other two axes directly: same framework, same format, same threads,
  native vs HotSpot only.
* Isolate thread model directly: same framework, same format, same JVM mode, virtual vs platform only
  - this pass never holds threads as the only varying axis for any pair.
* Isolate format directly: same framework, same JVM mode, same threads, TTLL vs GELF only.
* Investigate the platform-thread client-side hang-on-shutdown quirk noted above - cosmetic for this
  pass's data, but worth understanding before leaning on `--threads platform` for a longer run.
* The remaining 4 unpicked corners of the cube, for all four frameworks - this pass deliberately only
  sampled 4 of the 8x4=32 possible (framework, corner) pairs.
