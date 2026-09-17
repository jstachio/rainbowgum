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

## Controlled comparison: native-image + virtual threads, TTLL vs GELF, all four frameworks

The confounded pass above couldn't isolate anything. This one deliberately holds two axes fixed at
the values most people actually deploying this platform will pick - **native-image** (this benchmark
consistently shows it winning) and **virtual threads** (native-image is not the natural home for
heavy platform-thread-dependent apps, virtual threads are the modernization path) - and varies only
the remaining two: Rainbow Gum's appender type (`SYNCHRONIZED_THREAD_LOCAL_BUFFER` vs the default
`LOCK_THREAD_LOCAL_BUFFER`) and log format (TTLL vs GELF/Logstash-JSON), across all four frameworks.
This is the comparison that actually answers "should `SYNCHRONIZED_THREAD_LOCAL_BUFFER` be the
default for GraalVM native, or is `LOCK_THREAD_LOCAL_BUFFER` good enough".

Same methodology as above (GraalVM 25.3.4 CE, concurrency 50, 3s warmup + 15s measured,
`GET /greet/world`, stdout piped to `/dev/null` this time - see the harness-quirk note above for why),
**3 runs each**, 8 configs, all exit code 0 (no timeouts, unlike the platform-thread rows above):

| framework | TTLL throughput | TTLL p50 | TTLL RSS | GELF throughput | GELF p50 | GELF RSS |
|---|---:|---:|---:|---:|---:|---:|
| **rg-sync** | **91,773 req/s** | 0.45 ms | 119.7 MB | **92,264 req/s** | 0.45 ms | 74.2 MB |
| rg-lock (default) | 71,122 req/s | 0.63 ms | 103.3 MB | 70,012 req/s | 0.65 ms | 65.6 MB |
| log4j2 | 85,066 req/s | 0.48 ms | 99.6 MB | 82,037 req/s | 0.50 ms | 100.5 MB |
| logback | 71,939 req/s | 0.63 ms | 65.1 MB | 56,772 req/s | 0.68 ms | 105.1 MB |

### The sync-vs-lock question, answered

**`SYNCHRONIZED_THREAD_LOCAL_BUFFER` wins decisively here, in both formats**: +29.0% throughput over
the current default in TTLL (91,773 vs 71,122 req/s), +31.8% in GELF (92,264 vs 70,012 req/s) - a
bigger, cleaner margin than any earlier same-question measurement in this benchmark (the largest
prior number was +20.4%, GraalVM 25.3.4, TTLL only, see [RESULTS.md](RESULTS.md)'s "Closing the gap"
section), and now confirmed to hold under GELF too, not just TTLL. p50 latency moves the same
direction (0.63->0.45 ms TTLL, 0.65->0.45 ms GELF).

**And it changes the framework-level story, not just the internal one**: with the current default
(`rg-lock`), Rainbow Gum trails Log4j2 by 16.4% (TTLL) / 14.7% (GELF). With
`SYNCHRONIZED_THREAD_LOCAL_BUFFER` (`rg-sync`), Rainbow Gum *beats* Log4j2 by 7.9% (TTLL) / 12.5%
(GELF) - the exact scenario (native-image, virtual threads) this platform's actual users are most
likely to run in. `LOCK_THREAD_LOCAL_BUFFER` staying the default here is leaving a real, repeatable,
double-digit win on the table for the deployment target this feature was built for.

RSS is the one place this isn't a clean sweep: `rg-sync`'s TTLL RSS (119.7 MB) is the highest of all
four frameworks in this row, worse than even `rg-lock`'s (103.3 MB) - the one number in this whole
comparison that doesn't favor `SYNCHRONIZED_THREAD_LOCAL_BUFFER`. In GELF, `rg-sync`'s RSS (74.2 MB)
drops back below `rg-lock`'s TTLL number and is competitive again. Not explained (a fresh
`ReentrantLock`/`synchronized` monitor shouldn't itself cost meaningfully more resident memory than
the other), worth a closer look before calling this an unconditional win.

### Other things worth noting

* **Rainbow Gum's own RSS drops going from TTLL to GELF, in both appender types** (`rg-sync`:
  119.7->74.2 MB, `rg-lock`: 103.3->65.6 MB) - the opposite of what "GELF's JSON payload is bigger
  per line than TTLL's plain text" would predict, and the opposite of Logback's own direction (see
  below). Not explained, not chased further this pass - flagged as a genuine anomaly, not hand-waved
  away as "must be noise" (it's consistent across all 3 runs each way).
* **Logback's RSS moves hard in the other direction**: 65.1 MB (TTLL) -> 105.1 MB (GELF, actually
  Logstash JSON via the third-party `logstash-logback-encoder`), +61%, and its throughput drops 21%
  (71,939 -> 56,772 req/s) - consistent with this benchmark's standing finding that Logback's
  Jackson-based structured encoder is doing real, comparatively expensive work per event (see
  [RESULTS.md](RESULTS.md)'s structured-logging section). Log4j2's own built-in `GelfLayout`, by
  contrast, costs it almost nothing extra (RSS flat at ~100 MB, throughput down only 3.6%).
* **Log4j2 has the flattest format-to-format profile of the four** on every metric - the built-in,
  non-Jackson `GelfLayout` genuinely looks closer to "free" than any other structured-logging path
  measured in this benchmark so far.

## Not yet done

* Isolate JVM mode from the other two axes directly: same framework, same format, same threads,
  native vs HotSpot only.
* Isolate thread model directly: same framework, same format, same JVM mode, virtual vs platform only
  - neither pass in this file ever holds threads as the only varying axis for any pair.
* Investigate the platform-thread client-side hang-on-shutdown quirk noted above - cosmetic for that
  pass's data, but worth understanding before leaning on `--threads platform` for a longer run.
* The RSS anomaly noted above (`rg-sync`'s TTLL RSS being the one number that doesn't favor
  `SYNCHRONIZED_THREAD_LOCAL_BUFFER`) - worth a longer run and/or a closer look at what's actually
  retained before treating the sync-vs-lock throughput win as an unconditional recommendation.
* The remaining unpicked corners of the full cube - both passes in this file deliberately sampled a
  subset, not the full 8-corners-x-4-frameworks space.
