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

### A real anomaly worth flagging, not smoothing over: Logback's own JSON beats its own TTLL

Spotted after the fact, worth being upfront about: the 106,353 req/s JSON number above
was measured in a separate session from this file's earlier TTLL number for Logback
(71,939 req/s) - exactly the kind of cross-session absolute-number comparison this
file's own methodology note says not to trust. Re-measured both **interleaved, in one
session** (TTLL, JSON, TTLL, JSON, TTLL, JSON - not grouped, to rule out one-directional
drift) to check the finding survives a controlled comparison:

| | TTLL | JSON |
|---|---:|---:|
| throughput (avg of 3, interleaved) | 71,601 req/s | 104,962 req/s |

**Confirmed real, not a session artifact**: +46.6% for JSON over Logback's own TTLL,
same session, same binary, same everything except which encoder. This is genuinely odd
- every other framework in this file shows a small or negligible TTLL-vs-JSON/GELF delta
(Rainbow Gum: -0.5% to +1.6% either way; Log4j2: -3.6%), several using the identical
`%d{HH:mm:ss.SSS}` timestamp pattern Logback's own TTLL config uses. Logback being the
one outlier, and by a wide margin, deserved a real look rather than just being cited as
a good JSON number and moved on from.

**First hypothesis, tested directly, refuted**: suspected `%d{HH:mm:ss.SSS}` (real
calendar-aware date formatting) was expensive relative to `JsonEncoder`'s own
`timestamp`/`nanoseconds` fields (plain epoch millis/nanos, no calendar math at all).
Rebuilt the TTLL config with the date converter removed entirely
(`[%thread] %-5level %logger - %msg%n`) and re-ran (3 runs): **72,354 req/s - no
difference** from the with-date number above, within this file's own noise floor. Date
formatting is not the cause.

**The real clue was already sitting in this file**: on HotSpot, Logback's JSON number
(39,352 req/s) is *slower* than its own TTLL number (41,698 req/s) - the ordinary,
expected direction, matching every other framework. **The inflated JSON-beats-TTLL gap
is native-image-specific** - it does not reproduce on HotSpot at all. That points at a
GraalVM ahead-of-time-compilation quality difference, not a fundamental architectural
one: `PatternLayoutEncoder`'s converter-chain design (a linked list of `Converter`
objects, each one a virtual `write(...)` call, traversed per event) is exactly the
shape of code AOT compilation without runtime profile data tends to optimize more
conservatively than a JIT does (less aggressive devirtualization/inlining without
having seen the actual call-site behavior first) - while `JsonEncoder`'s `encode(...)`
is one monolithic method, all direct `StringBuilder` calls, no virtual dispatch chain,
exactly the shape that inlines well regardless of compiler. HotSpot's adaptive JIT
handles the converter-chain shape fine (or even better, per the HotSpot numbers) since
it profiles and devirtualizes at runtime; GraalVM's static native-image compiler,
working from static analysis alone here (no PGO used in this file), does not get the
same chance.

**Not confirmed with a profiler - this is an informed hypothesis from the
date-converter test and the native-vs-HotSpot reversal, not a diagnosed root cause.**
The date-converter test at least rules out the most obvious alternative explanation
cleanly.

### Confirmed directly: it's the converter chain, not JSON-vs-text

The hypothesis above made a testable prediction: a non-pattern-based TTLL
implementation - same timestamp/thread/level/logger/message content, same
`CachingDateFormatter`-based date formatting, but one monolithic method instead of a
converter chain - should perform like `JsonEncoder`, not like `PatternLayoutEncoder`.
Logback ships exactly this: `ch.qos.logback.classic.layout.TTLLLayout`, wrapped in
`ch.qos.logback.core.encoder.LayoutWrappingEncoder` (`TTLL_ENCODER=layout`, new
`logback-ttll-layout.xml`) - a single `doLayout(ILoggingEvent)` method building one
`StringBuilder` directly, no `Converter` chain at all.

Interleaved, one session, 3 runs each (pattern, layout, json, repeated):

| | pattern TTLL (`PatternLayoutEncoder`) | layout TTLL (`TTLLLayout`) | JSON (`JsonEncoder`) |
|---|---:|---:|---:|
| throughput | 70,188 req/s | **104,438 req/s** | 105,172 req/s |

**Prediction confirmed, cleanly**: the non-pattern TTLL layout (+48.8% over pattern
TTLL) lands within 0.7% of the JSON encoder - functionally identical, both roughly
+49% over the pattern-based encoder. This was never a "JSON is fast" or "TTLL is slow"
finding - it is specifically a **`PatternLayoutEncoder`/converter-chain-under-native-image**
finding. Any Logback output built on `PatternLayout` (which is most of them - most
real-world Logback configs use pattern-based encoders) pays this cost under
native-image; anything built as a single direct method (like `TTLLLayout` or
`JsonEncoder`) does not.

This still doesn't pin down *why* GraalVM's AOT compiler handles the converter-chain
shape worse than HotSpot's JIT does (the profiler/PGO follow-ups below are still
open), but the shape of the problem is no longer a hypothesis - it's confirmed by a
direct, targeted test that predicted its own result correctly before running it.

**A tempting alternative explanation, checked and ruled out: `String.getBytes()`
overload choice.** Given this benchmark's own earlier finding that `getBytes` vs
`getChars`-based copy-encoding mattered a lot for Rainbow Gum's own encoder under
native-image, it's reasonable to suspect the same class of cause here. It isn't.
Decompiling `LayoutWrappingEncoder` (`logback-core`) shows `convertToBytes(String)` is:

```
charset == null ? s.getBytes() : s.getBytes(charset)
```

Neither `logback.xml` (pattern TTLL) nor `logback-ttll-layout.xml` (layout TTLL) sets
`<charset>`, and this method is inherited unmodified by both, so **both take the
identical no-arg `s.getBytes()` call** on a string of comparable length. `JsonEncoder`,
decompiled separately, uses the *other* overload -
`s.getBytes(CoreConstants.UTF_8_CHARSET)` - and yet measures the same as layout TTLL,
not the same as pattern TTLL. If the overload were the driver, JSON and layout TTLL
would have to diverge and pattern/layout TTLL would have to match; the data does the
opposite. This isolates the cause to `layout.doLayout(event)` itself, not to anything
downstream of it.

## Testing `String.getBytes()` on Rainbow Gum's own encoder, not just Logback's

Adam's counter-read on the getBytes finding above: Rainbow Gum and Log4j2 both convert
chars to bytes via a `CharBuffer`/`CharsetEncoder` pair, not `String.getBytes()`, and
his suspicion is that `getBytes()` is actually well-optimized by both HotSpot and
GraalVM (including under native-image), so Logback's pattern-TTLL problem is that
GraalVM can't pre-encode/optimize the converter chain's output *at all*, not that
`getBytes()` itself is expensive. The direct test: build Rainbow Gum's own encoder with
`useGetBytes(true)` (an experimental flag that exists on `feature/log-encoder-copy-chars`,
never merged to `main`) and see what happens to Rainbow Gum's own native-image numbers.

**Methodology note - this required merging two independent, unmerged lines of work that
don't otherwise coexist anywhere.** `useGetBytes`/`StringBuilderBufferBytes` only exists
on `feature/log-encoder-copy-chars` (based on an old pre-v0.11.2 core, no
`AppenderType.LOCK_NEW_BUFFER`); `LOCK_NEW_BUFFER` only exists on current `origin/main`
(no `useGetBytes`). Hand-ported `useGetBytes`/`StringBuilderBufferBytes` onto a throwaway
worktree off `origin/main` (not a real branch, not pushed) and installed that core build
locally - same shape as the earlier `ENCODER_TYPE=GET_BYTES` experiment mentioned in
`project_graalvm_native_benchmark.md`, which was previously dropped from this branch for
the identical reason. Added `GetBytesEncoderConfigurator`
(`get-bytes-ttll:///` encoder scheme) and `ENCODER_TYPE=GET_BYTES` to `App.java`, same
pattern as `APPENDER_TYPE`. **This means the current `ENCODER_TYPE=GET_BYTES` code in
this module will not build against a stock `origin/main` checkout** without that same
local core merge - flagging honestly rather than pretending it's a clean, reproducible
config.

Interleaved, one session, 3 runs each, across Rainbow Gum's three `AppenderType`s x
default/`GET_BYTES` encoding:

| config | throughput | vs default | RSS avg |
|---|---:|---:|---:|
| default (`LOCK_THREAD_LOCAL_BUFFER`, `CharBuffer`/`CharsetEncoder`) | 70,940 req/s | - | 103.7 MB |
| default + `GET_BYTES` | 74,545 req/s | +5.1% | 67.1 MB (-35%) |
| `LOCK_NEW_BUFFER` (no ThreadLocal, fresh buffer/event) | 61,388 req/s | -13.5% | 272.4 MB (+163%) |
| `LOCK_NEW_BUFFER` + `GET_BYTES` | 68,539 req/s | -3.4% | 112.5 MB (+8%) |
| `SYNCHRONIZED_THREAD_LOCAL_BUFFER` | 90,142 req/s | +27.1% | 118.9 MB (+15%) |
| `SYNCHRONIZED_THREAD_LOCAL_BUFFER` + `GET_BYTES` | **93,557 req/s** | **+31.9%** | **72.7 MB (-30%)** |

**Adam's core claim is confirmed: `getBytes()` is not expensive under native-image, it
measurably helps, everywhere it was tried.** Every single `AppenderType` got faster and
used noticeably less memory with `GET_BYTES` than without it - this isn't a Logback
quirk or a no-ThreadLocal-specific effect, it's a general native-image win for Rainbow
Gum's own encode path too. The RSS drop is the more dramatic number in every row:
-35% (default), -59% (`LOCK_NEW_BUFFER`, 272→113 MB), -30% (sync) - consistent with a
`CharBuffer`+`CharsetEncoder` pair allocating and bookkeeping more per event than one
direct `String.getBytes(charset)` call.

**The specific "no ThreadLocal + lock + getBytes" combination Adam asked about
(`LOCK_NEW_BUFFER` + `GET_BYTES`) is not the overall winner, though `GET_BYTES` closes
most of its own gap.** `LOCK_NEW_BUFFER` alone is the worst config measured here (-13.5%,
and a real memory problem at 272 MB - consistent with the fresh-`ByteBuffer`-per-event
finding this benchmark's own `LOCK_NEW_BUFFER` history already flagged); adding
`GET_BYTES` nearly closes the throughput gap to default (-3.4%, from -13.5%) and cuts
RSS by more than half (272→113 MB), but does not overtake default, let alone sync.
**The actual best configuration found anywhere in this whole benchmark session is
`SYNCHRONIZED_THREAD_LOCAL_BUFFER` + `GET_BYTES`** - both axes win independently and the
gains stack (+27.1% from sync alone, +31.9% with `GET_BYTES` added on top), directly
relevant to `AppenderType.AUTO_DETECT`'s still-open design: if `useGetBytes` ever lands
for real, native-image's `AUTO_DETECT` resolution should probably prefer
`SYNCHRONIZED_THREAD_LOCAL_BUFFER` + `GET_BYTES`-style encoding together, not either
alone.

For context against Logback: 93,557 req/s is Rainbow Gum's best native-image TTLL
number in this file by a wide margin, but still trails Logback's own best (layout-TTLL/
JSON, ~105,000 req/s, see above) - closer than any prior Rainbow Gum config, not a full
close of that gap.

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
  version) via `org.graalvm.nativeimage.imagecode`. Not yet implemented. Per the `useGetBytes` section
  above, `AUTO_DETECT`'s native-image resolution should probably pair `SYNCHRONIZED_THREAD_LOCAL_BUFFER`
  with `GET_BYTES`-style encoding, not appender type alone - but `useGetBytes` itself isn't merged to
  `main` yet, so this is a "if/when it lands" note, not an immediate design change.
* **Land `useGetBytes`/`StringBuilderBufferBytes` for real** (currently only exists on the unmerged
  `feature/log-encoder-copy-chars`, hand-ported onto a throwaway `main`-based worktree just to run the
  experiment above) - it measurably helps every `AppenderType` under native-image, not a narrow win,
  and the `// TODO this code needs to be tested.` marker on that branch still needs real test coverage
  before it could ship.
* **Profile `PatternLayoutEncoder`'s converter chain under native-image directly** (JFR or
  async-profiler against the real native executable) to see *why* GraalVM's AOT compiler
  handles it worse than HotSpot's JIT does. The `TTLLLayout` test upgraded the converter-chain
  explanation from informed guesswork to a directly confirmed structural cause (non-pattern TTLL
  matches JSON, both far ahead of pattern TTLL; the `getBytes`-overload alternative was checked
  and ruled out too), so this is no longer needed to establish *that* the converter chain is the
  cause - only to explain the AOT-compiler mechanism behind it. Also worth trying `--pgo`/
  profile-guided native-image builds specifically for the pattern-TTLL scenario, to see if that
  alone closes the gap.
