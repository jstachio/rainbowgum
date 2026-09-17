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

## The same controlled comparison, on HotSpot

Same 8 configs (4 frameworks x TTLL/GELF), same fixed thread model (virtual), same methodology, but
HotSpot (Temurin 26.0.2) instead of native-image this time - the other half of the platform-detection
question: does `SYNCHRONIZED_THREAD_LOCAL_BUFFER` still win outside native-image, or does the
already-established HotSpot finding (`LOCK_THREAD_LOCAL_BUFFER` is the better default there, which is
*why* it's the default in the first place) still hold once GELF is added to the picture too, not just
TTLL?

**One run in this batch (`logback`+GELF) hit a real, reproducible memory anomaly serious enough that
it appears to have gotten the whole background batch killed by something outside this benchmark's own
control** (host/sandbox-level, not a JVM crash) partway through - see its own section below. The other
21 of 24 runs completed cleanly and are reported normally:

| framework | TTLL throughput | TTLL p50 | TTLL RSS | GELF throughput | GELF p50 | GELF RSS |
|---|---:|---:|---:|---:|---:|---:|
| rg-sync | 37,196 req/s | 1.36 ms | 582.0 MB | 34,022 req/s | 1.36 ms | 588.7 MB |
| **rg-lock (default)** | **43,253 req/s** | 1.13 ms | 576.7 MB | **42,243 req/s** | 1.19 ms | 591.0 MB |
| log4j2 | 35,167 req/s | 1.25 ms | 637.6 MB | 33,586 req/s | 1.23 ms | 609.1 MB |
| logback | 41,698 req/s | 1.22 ms | 609.2 MB | see below | - | see below |

### The sync-vs-lock question, confirmed in reverse on HotSpot

**`LOCK_THREAD_LOCAL_BUFFER` (the current default) wins here, in both formats** - +16.3% throughput
over `SYNCHRONIZED_THREAD_LOCAL_BUFFER` in TTLL (43,253 vs 37,196 req/s), +24.2% in GELF (42,243 vs
34,022 req/s). This is a genuine cross-over, not just "the native-image win shrinks" - the ranking
fully flips, and it now holds under GELF as well as TTLL (previously this HotSpot-favors-lock finding
had only been confirmed for TTLL). Combined with the native-image section above
(`SYNCHRONIZED_THREAD_LOCAL_BUFFER` +29-32%), **this is exactly the pattern that justifies gating by
platform, not picking one appender type as a new global default**: whichever one you'd pick,
something is leaving 16-32% throughput on the table on the other platform.

`rg-lock` also clearly leads both other frameworks here in both formats (log4j2 by +23.0%/+25.8%,
logback's own TTLL row by +3.7%) - consistent with this file's original, very first "HotSpot" section
finding that Rainbow Gum leads on HotSpot generally.

### `logback` + GELF: a real, reproducible, unexplained memory blow-up

The first sign was `logback-gelf-run1` in this batch showing RSS avg 5,407 MB / max 6,403 MB (every
other row in this entire file, across both native-image and HotSpot, sits under 650 MB) and a max
latency of 116 ms (every other row's max sits in single digits). The batch was killed partway through
`run2`.

Re-probed by hand, foreground, with live per-second RSS sampling, twice:

| variant | t=1s | t=8s | t=15-18s (still climbing) |
|---|---:|---:|---:|
| virtual threads | 466 MB | 8,384 MB | 8,511 MB |
| platform threads (`THREAD_TYPE=PLATFORM`) | 429 MB | 7,295 MB | 7,663 MB |

**Growth is monotonic and never plateaus within the run** - this is not a transient GC spike, it
sustains and keeps climbing for the entire measured window regardless of thread model. Ruling out
virtual-thread-identity churn specifically (a plausible first guess - a new virtual thread per request,
`~35,000/s`, could leak via a `ThreadLocal`-cached buffer never reclaimed) since it reproduces
identically with a small, fixed, reused 200-thread platform pool - 200 threads cannot explain
unbounded multi-gigabyte growth via a per-thread cache. A capped-heap sanity check
(`-Xmx1g`, shorter/lighter run) showed no such growth (topped out at 793 MB, stable) - suggesting this
is specifically about what happens when HotSpot's default heap ergonomics (no `-Xmx` anywhere in this
benchmark's own methodology, and this host has 121 GB of RAM to grow into) meet
`logstash-logback-encoder`'s Jackson-based allocation profile under sustained load: garbage
accumulates faster than GC reclaims it, and with a huge default max heap available, HotSpot lets
committed memory keep growing rather than collecting aggressively. **Not confirmed with a heap
histogram/profiler - this is an informed hypothesis from the differential tests above, not a diagnosed
root cause.** Notably this never showed up in the native-image pass above (`logback`+GELF RSS there
was a normal 105.1 MB) - whatever this is, it looks specific to HotSpot's heap ergonomics on this
particular host, not to Logback/`logstash-logback-encoder` architecturally.

Throughput itself stayed roughly in line with the other rows during the growth (34,000-38,000 req/s
across the three affected samples) - the leak (if that's what it is) doesn't appear to choke request
handling within this benchmark's short window, only memory, plus that one 116-139ms latency-tail
spike. Given the risk of triggering another external kill, this was not run to completion for a
clean 3-sample average the way every other row in this file is - the three samples collected (run1,
and the two hand-probed reruns) are reported above individually rather than averaged into the main
table.

## Giving Logback a fair shot at JSON: its own built-in `JsonEncoder`

Every JSON/GELF number for Logback so far in this file used the third-party
`logstash-logback-encoder` (Jackson-based) - the de-facto standard choice, but not
Logback's own architecture, and this file already found it doing real, comparatively
expensive per-event work, plus the serious unbounded-HotSpot-memory issue documented
above. That's a "how expensive is the popular third-party JSON encoder" result, not
"how good is Logback itself at JSON" - conflating the two isn't a fair shot.

Logback-classic ships its own `ch.qos.logback.classic.encoder.JsonEncoder` (since
1.5.x, no third-party dependency - confirmed present in the `logback-classic:1.6.3`
jar this benchmark already pins). Not GELF, not Logstash format either - its own
generic RFC-8259 JSON-Lines representation of the event
(`{"sequenceNumber":...,"timestamp":...,"level":...,"loggerName":...,"mdc":{...},
"formattedMessage":...,"throwable":...}`). Wired in as `STRUCTURED_FORMAT=json`
(`logback-json-builtin.xml`), alongside the existing `STRUCTURED_FORMAT=gelf`
(`logback-json.xml`, unchanged, still the Logstash encoder).

**Obvious caveat, stated up front**: this is not a GELF-format comparison - Logback's
own JSON schema is a different shape/size than the GELF payloads Rainbow Gum and
Log4j2 produce for their own `gelf` rows elsewhere in this file. Read the numbers
below as "how does Logback's own best JSON path perform" first, and only loosely,
directionally, against the other frameworks' GELF numbers second.

Same methodology as the controlled comparisons above (virtual threads, concurrency
50, 3s warmup + 15s measured, 3 runs each):

| | native-image | HotSpot |
|---|---:|---:|
| throughput | **106,353 req/s** | 39,352 req/s |
| p50 | 0.42 ms | 1.27 ms |
| RSS avg | 56.5 MB | 616.7 MB |

**Compare against this same file's Logstash-encoder numbers for Logback+GELF**:
native-image was 56,772 req/s / 105.1 MB RSS; HotSpot never produced a valid
steady-state number at all (unbounded memory growth, see above). Logback's own
encoder is **+87.3% faster and uses 46% less memory on native-image**, and shows
**zero sign of the HotSpot memory issue** - RSS sits at 616.7 MB, right in line with
every other normal HotSpot row in this file, confirming that leak was specific to
`logstash-logback-encoder`'s allocation profile, not something inherent to Logback's
own architecture.

Put differently: **Logback's own native-image JSON number (106,353 req/s) is the
highest structured-logging throughput measured anywhere in this file** - higher than
Rainbow Gum's own `rg-sync` GELF row (92,264 req/s) and Log4j2's GELF row
(82,037 req/s), despite the different JSON schema and Logback trailing badly in every
earlier structured-logging comparison in this file. Every prior "Logback is
comparatively slow/expensive at structured logging" finding in this file was really a
finding about `logstash-logback-encoder` specifically, not about Logback itself.

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
* **Root-cause the `logback`+GELF (Logstash encoder) HotSpot memory blow-up properly** - a heap
  histogram or profiler run (not just the differential RSS probes done here) is needed before calling
  the "GC ergonomics meeting a huge default heap" hypothesis confirmed rather than just plausible.
  Narrowed since first written: Logback's own built-in `JsonEncoder` (see below) shows zero such issue
  on the identical HotSpot JVM/host/workload, so this is specifically about
  `logstash-logback-encoder`'s Jackson-based allocation profile, not something generic about "JSON on
  HotSpot" or this host's heap ergonomics alone. Still worth checking whether an explicit `-Xmx` makes
  it disappear entirely, and whether it reproduces on a smaller-RAM host at all.
* This file's cross-platform pair (native-image: `SYNCHRONIZED_THREAD_LOCAL_BUFFER` +29-32%; HotSpot:
  `LOCK_THREAD_LOCAL_BUFFER` +16-24%) is the concrete data behind an `AppenderType.AUTO_DETECT` design
  under discussion - opt-in (not the new default), sniffing platform only (not GraalVM version - this
  file's own native-image numbers came from GraalVM 21 and 25.3.4 with no sign the effect depends on
  version) via `org.graalvm.nativeimage.imagecode`. Not yet implemented.
