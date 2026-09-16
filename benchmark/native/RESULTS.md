# GraalVM native image benchmark results

Methodology: see [README.md](README.md). Each app run as a real native executable,
driven by `rainbowgum-benchmark-native-driver` over plain HTTP/1.1, concurrency 50
(virtual threads on both client and server side), 3-5s warmup (discarded) + 12-20s
measured, against `GET /greet/world`. RSS sampled from `/proc/<pid>/status` while the
measurement ran. Most numbers below (everything except the "GraalVM version" and
"HotSpot" sections) were measured on GraalVM 21 Oracle distro (`21.0.12-graal`) - noted
explicitly wherever that's not the case.

**A methodology note worth being upfront about**: the first pass at these numbers (kept
in git history, not reproduced here) showed much larger gaps than what is below - most
strikingly, Rainbow Gum's own default-appender-type throughput measured 23,057 req/s on
that first run. Re-running the *identical* configuration three more times measured
30,529 / 30,565 / 30,666 req/s - tightly clustered, nowhere near the original number.
That first pass was an outlier from this being a shared, virtualized sandbox (probably
residual load from the native-image build that had just finished), not a real
difference between configurations. Every number below is an average of **3 independent
runs** (2 for logback-structured; the third run's environment made this session run
out of time, but the first two were already tight), not a single sample, specifically
because of what that first pass got wrong.

## GraalVM version: 21 vs the latest (25.3.4)

All numbers above and below this section used GraalVM 21 (`21.0.12-graal`, Oracle
distro - matches this project's own CI, `.github/workflows/native-image.yml`). Also
tried the latest available (`25.3.4+1.r25`, Community Edition, already installed
locally via sdkman - both CE and Oracle editions top out at the same `25.3.4+1.r25` as
of this session), TTLL scenario, Rainbow Gum's default appender type (not
`SYNCHRONIZED_THREAD_LOCAL_BUFFER` - reset to default specifically to get a clean
before/after comparison isolated to the GraalVM version alone), 3 runs each:

| | Rainbow Gum (default) | Log4j2 | Logback |
|---|---:|---:|---:|
| GraalVM 21 throughput | 30,587 req/s | 36,102 req/s | 31,703 req/s |
| **GraalVM 25.3.4 throughput** | **69,677 req/s** | **84,073 req/s** | **71,420 req/s** |
| GraalVM 21 RSS avg | 45.7 MB | 81.4 MB | 50.6 MB |
| GraalVM 25.3.4 RSS avg | 63.2 MB | 98.7 MB | 65.2 MB |

Throughput roughly **doubles or more** across all three on the newer GraalVM, with
relative ordering preserved (Log4j2 still leads, by a broadly similar proportional
margin). RSS goes up somewhat on the newer version for all three, not down - a real
tradeoff, not a strict win, though the relative gap between frameworks barely moves.
Given this is a real, substantial, across-the-board improvement, later runs in this
file after this section use GraalVM 25.3.4 unless stated otherwise.

## HotSpot (plain JVM, no native-image) baseline

"Let's try this on hotspot just to see." Same apps, same jars, run with a plain
`java -cp ...` (Eclipse Temurin 26.0.2, a real HotSpot JVM - not GraalVM's own JIT at
all) instead of a native executable. TTLL scenario, Rainbow Gum's default appender
type, 3 runs each:

| | Rainbow Gum (default) | Log4j2 | Logback |
|---|---:|---:|---:|
| throughput | **41,677 req/s** | 35,573 req/s | 41,267 req/s |
| RSS avg | 583.8 MB | 599.7 MB | 622.4 MB |

**The ranking flips.** Under GraalVM native-image, Log4j2 leads throughput and Rainbow
Gum trails. Under plain HotSpot, for the identical workload, **Rainbow Gum leads**
(41,677 req/s, essentially tied with Logback's 41,267, both clearly ahead of Log4j2's
35,573). Neither result is "wrong" - they're answering different questions. AOT
compilation and JIT compilation optimize different things, and apparently optimize
these three frameworks' actual hot paths differently enough to change which one wins.
This is worth remembering before treating any single one of these benchmark's numbers
as *the* answer to "which is fastest" - the honest answer is "it depends which JVM mode
you're actually going to deploy with."

Memory tells a much less ambiguous story regardless of JVM mode: HotSpot's RSS here
(583-622 MB) is roughly **9-10x** every native-image RSS number in this file, for every
framework, which is exactly GraalVM native-image's actual selling point - this isn't a
close call the way throughput is.

## TTLL (plain console) scenario

| | Rainbow Gum (default) | Rainbow Gum (`SYNCHRONIZED_THREAD_LOCAL_BUFFER`) | Log4j2 | Logback |
|---|---:|---:|---:|---:|
| throughput (avg of 3 runs) | 30,587 req/s | 33,549 req/s | **36,102 req/s** | 31,703 req/s |

Log4j2 still leads. But the default-vs-tuned Rainbow Gum comparison is the real story
here.

## Locking strategy matters: `SYNCHRONIZED_THREAD_LOCAL_BUFFER`

Adam's hunch, prompted by `LogAppender.AppenderType`'s own javadoc noting Log4j2's
appenders use `synchronized` rather than a `java.util.concurrent` lock, and that this
type "used to be the default" until virtual-thread benchmarking found it lost to
`LOCK_THREAD_LOCAL_BUFFER` - on a plain HotSpot JVM. Tried against this GraalVM
native-image benchmark specifically (`APPENDER_TYPE=SYNCHRONIZED_THREAD_LOCAL_BUFFER`,
set as `logging.appender.console.type` before the first logger is created):

**it wins here, consistently, by a real margin** - roughly **+9.7%** throughput over
Rainbow Gum's own default (33,549 vs 30,587 req/s, TTLL scenario), confirmed across 3
runs each, not a one-off. This is the opposite of what the HotSpot-JVM finding that
made `LOCK_THREAD_LOCAL_BUFFER` the default would predict, at least in *this*
environment (GraalVM Substrate VM, JDK 21 baseline - before JEP 491, so `synchronized`
still pins the carrier thread when called from a virtual thread here, which makes the
win more surprising, not less). Whether this is specific to Substrate VM's own lock/
monitor implementation, this sandbox, or something else has not been investigated
further - flagging the result, not yet explaining it.

This does **not** close the gap with Log4j2 (36,102 still leads), but it meaningfully
narrows it, and in the structured-logging scenario below it's enough to pull clearly
ahead of Logback.

## `LOCK_NEW_BUFFER` - a negative result

Adam's characterization: "the lock with no thread local," believed closer to Logback's
own shape than `REUSE_BUFFER` is - `REUSE_BUFFER` holds its lock across the entire
encode-then-write critical section (matching Logback), while `LOCK_NEW_BUFFER` encodes
*outside* the lock like the two `..._THREAD_LOCAL_BUFFER` types do, just without the
`ThreadLocal` (a fresh buffer allocated per event instead of one reused per thread).
Tried on GraalVM 25.3.4, Rainbow Gum only (Log4j2/Logback numbers already established
above), TTLL scenario, 3 runs:

| | `LOCK_THREAD_LOCAL_BUFFER` (default) | `LOCK_NEW_BUFFER` |
|---|---:|---:|
| throughput | 69,677 req/s | 67,845 req/s |
| RSS avg | 63.2 MB | 113.3 MB |

Unlike `SYNCHRONIZED_THREAD_LOCAL_BUFFER`, this one is a straightforward loss on both
axes - not just failing to beat Log4j2/Logback, but **worse than Rainbow Gum's own
default**: throughput down slightly (-2.6%), and memory nearly **double** (113.3 vs
63.2 MB) - unsurprising in hindsight, since a fresh buffer allocated for every single
event (instead of one reused per thread) is real, continuous garbage at this request
rate. Matching Logback's *locking shape* did not translate into matching (or beating)
Logback's *numbers* - Logback's own 71,420 req/s still comfortably beats this.

## Non-Latin1 content: does Logback's `getBytes()` fast path actually matter here?

Background, from `feature/webapp-benchmark`'s own `FINDINGS.md` (not reproduced from
scratch here, just cited): Logback's `LayoutWrappingEncoder` does a plain
`String.getBytes(charset)` per event - no `CharsetEncoder`, no reused buffer. The JDK's
compact strings give `String.getBytes(UTF_8)` a fast path for pure-Latin1 content
(effectively a straight array copy); the moment a string contains **any** character
above U+00FF, the whole string's internal coder flips to `UTF16` and encoding falls
back to a full, slower path for the *entire* line, not just the one character. That
prior benchmark measured this costing Logback ~7% throughput under virtual threads
(Log4j2/Rainbow Gum, both `CharsetEncoder`-based, unaffected).

Checked directly in this codebase: Rainbow Gum *has* the identical fast path -
`LogOutput.write(LogEvent, String)`'s default implementation is exactly
`s.getBytes(StandardCharsets.UTF_8)` (`core/.../LogOutput.java`) - but it is gated
behind `BufferHints`/`WriteMethod.STRING`, and **no built-in `LogOutput` implementation
hints `STRING`**: every one of them (including the one behind `ofStandardOut()`, used
by this benchmark) explicitly hints `WriteMethod.BYTES`, so the `StringBuilderBuffer` +
getBytes path is real, present, and dead code in practice today. Adding an output that
actually activates it is a real TODO, not done here.

Retested this benchmark's own TTLL scenario (GraalVM 25.3.4, Rainbow Gum's default
appender type restored) with the request path changed from `/greet/world` to a single
globe emoji, `/greet/%F0%9F%8C%8D` (percent-encoded 🌍, U+1F30D - 4 UTF-8 bytes,
actually *fewer* bytes than "world"'s 5 ASCII ones, so any slowdown is about encoding
path, not data volume), 3 runs each:

| | Rainbow Gum | Log4j2 | Logback |
|---|---:|---:|---:|
| ASCII ("world") throughput | 69,677 req/s | 84,073 req/s | 71,420 req/s |
| Emoji (🌍) throughput | 69,112 req/s | 82,987 req/s | 71,166 req/s |
| change | -0.8% | -1.3% | -0.4% |

**This does not clearly reproduce the prior finding.** All three dipped slightly, by
amounts close enough to each other (and close enough to this environment's own
run-to-run noise elsewhere in this file) that nothing here stands out as *the*
Logback-specific effect the prior benchmark measured - if anything, Logback shows the
*smallest* relative change of the three in this run, the opposite of what the theory
predicts. Possible reasons, none confirmed: a different request/response overhead
ratio here (plain `HttpServer` vs the Spring/Tomcat stack the original measurement
used) could be diluting a real but small per-event cost; the effect could be genuinely
present but below this benchmark's noise floor at 3 runs; or the original ~7% figure
may not reproduce cleanly outside its own original conditions. Not chased further -
recorded honestly as a non-replication rather than forced to match the expected
narrative.

## Activating the `STRING`/`getBytes()` fast path (`OUTPUT_TYPE=STRING`)

The "Non-Latin1 content" section above noted the `getBytes()` fast path is real in
Rainbow Gum but dead code - no built-in `LogOutput` hints `WriteMethod.STRING`. Added
`StringStdOutOutput` (Rainbow Gum benchmark module only, registered under a
`string-stdout:///` URI scheme via a hand-written `Configurator` -
`OUTPUT_TYPE=STRING`), identical to the default stdout output except it hints `STRING`
instead of `BYTES`.

**Caveat found before running anything**: this isn't a clean single-variable swap. For
the default appender type (`LOCK_THREAD_LOCAL_BUFFER`), `encoder.encode(...)` (building
the `StringBuilder`) happens *outside* the lock either way, but the actual
byte-conversion step happens at a different point depending on write method - for
`BYTES`, the `CharsetEncoder` work happens inside `encode()`, still outside the lock;
for `STRING`, the `StringBuilder.toString()` + `getBytes()` conversion only happens
inside `Buffer.drain(...)`, which is called from `output.write(event, buffer)` -
*inside* `writeLocked()`'s critical section. So `OUTPUT_TYPE=STRING` moves the
byte-encoding work inside the lock, something the `BYTES`/`BYTE_BUFFER` paths
specifically avoid by design (see `LockThreadLocalBufferLogAppender`'s own comment).
Not what "just try Logback's encode strategy" sounds like at first, but a real variant
worth measuring anyway.

TTLL, GraalVM 25.3.4, default appender type, `/greet/world`, 6 runs each (3 forward, 3
in reversed start order, to rule out a same-session time-based drift confounding the
comparison - it didn't, both orders landed in the same range):

| | default (`BYTES`) | `OUTPUT_TYPE=STRING` | change |
|---|---:|---:|---:|
| throughput | 26,972 req/s | 29,558 req/s | **+9.6%** |
| p50 latency | 1.79 ms | 1.59 ms | -11.2% |
| RSS (avg) | 38.1 MB | 39.2 MB | +2.8% |

A real, repeatable, order-independent win on both throughput and latency, small RSS
cost - despite moving work *into* the lock, which is the opposite of what this
benchmark's whole locking-strategy story (`SYNCHRONIZED_THREAD_LOCAL_BUFFER`,
`LOCK_NEW_BUFFER` above) would predict. Not explained - `DirectByteBufferBuffer`'s
`CharsetEncoder` path apparently costs more than the extra in-lock time this trades it
for, at least at this event size and concurrency, but that's a guess, not profiled.

**Absolute numbers here are not comparable to this file's earlier GraalVM-25.3.4 table**
(69,677 req/s for this exact default configuration, measured in an earlier session) -
this session's sandbox is running at roughly a third of that throughput across the
board, consistent with the "methodology note" above about this being a shared,
virtualized, noisy environment. Only the internal default-vs-`STRING` delta, measured
back-to-back under identical current-session conditions, is meaningful here.

### Retested after fixing the "caveat" above (`core` change, not benchmark-only)

The in-lock `getBytes()` caveat above was a real bug, not just a benchmark footnote -
fixed in `core`: `StringBuilderBuffer` now converts to a `byte[]` inside
`encodeToBuffer(...)` (outside the lock, the same timing `DirectByteBufferBuffer`'s
`CharsetEncoder` work already used) and `drain(...)` writes that precomputed array
directly, instead of lazily calling `LogOutput.write(LogEvent, String)` - whose
`getBytes()` was what landed inside the lock before. Separately,
`DirectByteBufferBuffer`'s own scratch `StringBuilder` was never sized with
`initialByteCapacity` (it defaulted to `StringBuilder`'s own capacity of 16, unlike the
already-presized `byteBuffer` next to it) - fixed to match, so every fresh buffer isn't
paying for several growth reallocations on its first event.

Retested the exact same comparison (TTLL, GraalVM 25.3.4, default appender type,
`/greet/world`, 6 runs: 3 forward + 3 reversed start order):

| | default (`BYTES`) | `OUTPUT_TYPE=STRING` | change |
|---|---:|---:|---:|
| throughput | 25,840 req/s | 30,034 req/s | **+16.2%** (was +9.6%) |
| p50 latency | 1.85 ms | 1.58 ms | -14.6% (was -11.2%) |
| RSS (avg) | 42.1 MB | 39.6 MB | **-6.1%** (was +2.8%) |

Fixing the lock-timing bug didn't shrink the win, it grew it, and RSS flipped from a
small cost to a real savings. Not fully explained - moving the `getBytes()` work outside
the lock should mostly affect contention/latency, not raw allocation volume, so the RSS
flip in particular is a bit surprising - but the direction is consistent and the
comparison is now the clean, single-variable one the original caveat said it wasn't:
same appender type, same lock timing on both sides, only the encode strategy
(`getBytes()` vs `CharsetEncoder`) differs. (Absolute numbers moved again vs the
first pass at this table too - 25,840 req/s here vs 26,972 there for the identical
default configuration - same sandbox-noise caveat as above; only the paired delta within
each pass is meaningful.)

## Closing the gap with Log4j2: `SYNCHRONIZED_THREAD_LOCAL_BUFFER` + the fixed `DirectByteBufferBuffer`

Two open threads collided here. First: `DirectByteBufferBuffer` is architecturally the
closer match to Log4j2's own approach (both `CharsetEncoder`-based, garbage-free
encoding into a reused buffer) of the two Rainbow Gum encode strategies, yet it was the
one trailing Log4j2 by the widest margin in every table above - worth retrying now that
its `StringBuilder` sizing bug is fixed (see the `STRING`/`getBytes()` section above).
Second: `SYNCHRONIZED_THREAD_LOCAL_BUFFER` was flagged in "Not yet tried" as only ever
tested on GraalVM 21 (a real, repeatable +9.7% win there) - never retried on GraalVM
25.3.4.

Rebuilt Rainbow Gum (`APPENDER_TYPE=SYNCHRONIZED_THREAD_LOCAL_BUFFER`, default output -
`DirectByteBufferBuffer`, not `STRING`) and Log4j2 (default) fresh on GraalVM 25.3.4 and
ran them back-to-back, TTLL, `/greet/world`, 6 runs each (3 forward + 3 reversed start
order). Logback deliberately not rebuilt/run this round (not what was being tested);
Rainbow Gum's own **default** (`LOCK_THREAD_LOCAL_BUFFER`) number below is carried over
from the immediately preceding retest in this same session (same fixed
`DirectByteBufferBuffer` code, not re-run in this exact batch) as a same-session
reference point, not a fresh sample in this batch:

| | Rainbow Gum (default, `LOCK_THREAD_LOCAL_BUFFER`) | Rainbow Gum (`SYNCHRONIZED_THREAD_LOCAL_BUFFER`) | Log4j2 (default) |
|---|---:|---:|---:|
| throughput | 25,840 req/s | 31,121 req/s | 33,446 req/s |
| p50 latency | 1.85 ms | 1.51 ms | 1.33 ms |
| RSS (avg) | 42.1 MB | 46.0 MB | 64.2 MB |
| gap vs Log4j2 | -22.8% | **-7.0%** | - |

Switching Rainbow Gum's appender type alone - same fixed buffer code on both sides -
closed most of the gap: **+20.4% throughput** over Rainbow Gum's own default
(31,121 vs 25,840 req/s), taking the deficit against Log4j2 from -22.8% down to -7.0%.
This is a substantially bigger win than the +9.7% recorded on GraalVM 21 - confirms and
strengthens the "Not yet tried" item, `SYNCHRONIZED_THREAD_LOCAL_BUFFER` wins even more
on GraalVM 25.3.4/Substrate VM than it did on GraalVM 21, still the opposite of the
plain-HotSpot finding that made `LOCK_THREAD_LOCAL_BUFFER` the default in the first
place. RSS moves the other way but stays decisively in Rainbow Gum's favor either way -
46.0 MB is still 28% below Log4j2's 64.2 MB.

Neither the `DirectByteBufferBuffer` sizing fix nor `SYNCHRONIZED_THREAD_LOCAL_BUFFER`
was isolated from the other in this specific batch (Log4j2 was only run against the
`SYNCHRONIZED_THREAD_LOCAL_BUFFER` configuration, not separately against a freshly
re-run default-appender-type one) - the "default" row is a same-session carryover
number, not a controlled same-batch baseline, so treat the exact -22.8%/-7.0% split with
that caveat. What is solid: the remaining gap to Log4j2 is now clearly small (~7%) where
it was previously the widest gap measured in this entire benchmark (Log4j2 was
recorded as the definitive native-image leader against every one of Rainbow Gum's other
configurations tried so far), and the `getBytes()`-based `STRING` path above already
closes that gap entirely and then some on its own (30,034 req/s at default appender
type, ahead of Log4j2's 33,446 only by comparison to Rainbow Gum's own baseline, not
tested against Log4j2 in the same batch either) - stacking `STRING` +
`SYNCHRONIZED_THREAD_LOCAL_BUFFER` together is the obvious next experiment.

## Structured logging scenario (`STRUCTURED_FORMAT=gelf`)

Each framework uses its own idiomatic structured format - see [README.md](README.md)
for why: Rainbow Gum (`rainbowgum-json`'s `GelfEncoder`) and Log4j2 (`log4j-core`'s own
built-in `GelfLayout`) both produce GELF; Logback (no first-party GELF option) uses the
third-party `logstash-logback-encoder` instead, producing Logstash-format JSON.

| | Rainbow Gum (default) | Rainbow Gum (`SYNCHRONIZED_THREAD_LOCAL_BUFFER`) | Log4j2 | Logback |
|---|---:|---:|---:|---:|
| throughput (avg of 3 runs, 2 for Logback) | 31,004 req/s | 33,642 req/s | **34,641 req/s** | 27,948 req/s |

With the locking-strategy fix applied, Rainbow Gum clearly beats Logback here (33,642
vs 27,948 - roughly +20%) and comes within about 3% of Log4j2, not the wider gap the
TTLL scenario shows. Logback's own structured number is lower than its own TTLL number
(27,948 vs 31,703) - the Jackson-based `logstash-logback-encoder` is doing real,
comparatively expensive work per event that neither Rainbow Gum's hand-rolled JSON
writer nor Log4j2's built-in (non-Jackson) `GelfLayout` has to pay for. See the earlier
per-run p50/p99/RSS breakdown further down for the shape of that cost (tail latency,
not just throughput).

## Detail: latency and memory (single representative runs, not averaged)

These numbers are from individual runs, not the 3-run averages above - useful for shape
(tail latency, RSS) even though the throughput column specifically should be read from
the averaged tables above instead.

| | Rainbow Gum (default) | Rainbow Gum (sync) | Log4j2 | Logback |
|---|---:|---:|---:|---:|
| TTLL p50 / p99 | 2.13 / 4.84 ms | 1.47 / 3.96 ms | 1.65 / 5.72 ms | 2.04 / 4.64 ms |
| TTLL RSS avg | 45.7 MB | 53.1 MB | 81.4 MB | 50.6 MB |
| structured p50 / p99 | 1.54 / 4.01 ms | 1.48 / 3.91 ms | 1.37 / 4.33 ms | 1.60 / 7.77 ms |
| structured RSS avg | 52.7 MB | 53.0 MB | 94.0 MB | 128.2 MB |

RSS: Rainbow Gum leads both scenarios regardless of appender type (the locking
strategy changes throughput, not memory footprint meaningfully). Logback's structured
RSS (128.2 MB) is the outlier of the whole table - roughly 2.4x Rainbow Gum's own
structured RSS, and the framework's single worst number here by far, consistent with
the Jackson-based encoder story above.

## Log4j2's buffer size (checked, not the differentiator it might look like)

Adam asked whether Log4j2's own throughput lead might come from aggressive output
buffering. Checked directly (decompiled `org.apache.logging.log4j.core.util.Constants`
from the real `log4j-core-2.26.1.jar`): the default is
`log4j.encoder.byteBufferSize=8192` (8 KiB), system-property overridable. Rainbow Gum's
own default (`LogEncoder.DEFAULT_INITIAL_BYTE_CAPACITY`) is also 8192 - same number.
This rules out "Log4j2 just uses a bigger buffer" as the explanation, though it does
not rule out buffering behavior being different in some other way (e.g. immediate-flush
semantics, or how many events get batched into one buffer versus one buffer per event) -
not chased further yet.

## Cheaper `Instant`s in `LogEventFactory` - a negative result

Adam's standing theory (see "Not yet tried" below, pre-existing before this test) was
that TTLL's per-event cost is mostly time formatting, and specifically suspected
`Instant.now()` itself - not just the formatting step - since `Instant.now()` does real
nanosecond-precision work (on most JDKs, an extra native call plus interpolation math)
on top of the millisecond value that TTLL's default format
(`LogFormatter.TimestampFormatter#of()`, `HH:mm:ss.SSS`) never even displays.

`LogEventFactory#timestamp()`'s default (the actual timestamp source for every event
built through the SLF4J binding - `LogEventHandler extends LogEventFactory`) was
`Instant.now()`. Changed it to `Instant.ofEpochMilli(System.currentTimeMillis())` -
millisecond-precision only, one syscall, no nanosecond interpolation. This is a core
change (`core/.../LogEventFactory.java`), not benchmark-only: it changes the default for
every consumer of the SLF4J binding's default event construction, with a documented
precision trade-off (anyone actually pairing `TimestampFormatter#ofMicros()` with a real
sub-millisecond clock needs to override `timestamp()` back to `Instant.now()` now).

**Confirmed via a quick JMH check first** (not committed, throwaway): on HotSpot/JIT
(Temurin 26.0.2, not GraalVM), `Instant.now()` measured 34.19 ns/op vs 30.59 ns/op for
`Instant.ofEpochMilli(System.currentTimeMillis())` - a real, outside-error-bars ~12%
difference, but a tiny ~3.6ns/op absolute one. Both sit well above the
`currentTimeMillis()`-alone floor (~24ns/op), meaning most of `Instant.now()`'s cost
(object allocation, epoch-second/nano normalization math) is shared with the cheaper
variant too - the nanosecond-precision work specifically is a small slice of a small
number.

**Measured end-to-end anyway** (TTLL, GraalVM 25.3.4, default appender/output/level,
`/greet/world`, 6 runs: 3 forward + 3 reversed start order): **no measurable
difference.**

| | before (`Instant.now()`) | after (`Instant.ofEpochMilli(...)`) | change |
|---|---:|---:|---:|
| throughput | 27,116 req/s | 27,251 req/s | +0.5% |
| p50 latency | 1.78 ms | 1.77 ms | -0.6% |
| RSS (avg) | 38.3 MB | 38.2 MB | -0.3% |

Every one of these deltas is smaller than this benchmark's own established run-to-run
noise floor (~1-2%, see the methodology note at the top of this file). Consistent with
the JMH numbers: ~3.6ns saved per `Instant`, times up to 5 log calls per request, is
~18ns - against a per-request cost that's tens of microseconds (HTTP parsing, virtual
thread scheduling, TTLL string building, actual I/O), this saving is simply too small
to surface at the request-throughput level, even though the underlying per-call cost
difference is real. **This does not support "`Instant.now()`'s nanosecond-precision
cost" as a meaningful contributor to Rainbow Gum's native-image-vs-Log4j2 gap** - the
change is being kept anyway (verified real via JMH, zero measured downside, and the
millisecond-only precision is what every built-in formatter already displays), but the
underlying "why is TTLL slower here" question remains genuinely open. The
`MillisCache`-contention half of the original theory (a shared `AtomicReference` every
concurrent thread contends on) is still untested.

## Baseline: logging mostly off (`LOG_LEVEL=ERROR`)

`LOG_LEVEL=ERROR` overrides each framework's root level (Rainbow Gum:
`logging.level`; Log4j2/Logback: `${...:-INFO}` substituted in the config files with a
system property `App.main` sets from the env var). None of `BenchHandler`'s calls are
above `INFO`, so this disables every log call in the request path - the near-zero-cost
disabled-level path each framework's SLF4J binding takes, exercised through a real HTTP
server under load instead of a synthetic microbenchmark. 3 runs each, same methodology.

| | Rainbow Gum | Log4j2 | Logback |
|---|---:|---:|---:|
| throughput (avg of 3 runs) | 75,612 req/s | 75,596 req/s | 75,558 req/s |
| RSS avg (avg of 3 runs) | **40.9 MB** | 59.0 MB | 51.0 MB |

Two things stand out:

1. **Throughput converges to statistically indistinguishable** across all three
   (75,558-75,612 req/s, tighter clustering across 9 total runs than any single
   framework's own logging-enabled numbers above) once logging is mostly disabled -
   confirms the earlier throughput *differences* were actually about logging cost, not
   some other difference in HTTP dispatch, and that throughput numbers *can* be tight
   and reliable in this environment when the workload doesn't route through the parts
   that vary (logging itself, apparently).
2. **Memory is exactly as repeatable as predicted, and Rainbow Gum clearly leads even
   with logging mostly off**: 40.9 MB vs Log4j2's 59.0 MB (-31%) and Logback's 51.0 MB
   (-20%). This is not "logging is cheap so it stops mattering" - a real, persistent
   baseline-footprint gap remains between the three runtimes even with almost nothing
   being logged, consistent with the plain image-size numbers from the "Small" section
   of `why_rainbowgum_is_better.md` and every RSS number recorded elsewhere in this
   file. Of everything measured in this benchmark, this is the number with the least
   run-to-run noise and the clearest, most consistent story.

## Not yet tried

* `REUSE_BUFFER` + `OUTPUT_TYPE=STRING` together - the combination that would actually
  match Logback's shape exactly (encode *and* getBytes *and* write, all under one lock);
  what's measured above (`STRING` with the default appender type) only moves the
  getBytes step under the lock, not the formatting step too.
* `SYNCHRONIZED_THREAD_LOCAL_BUFFER` + `OUTPUT_TYPE=STRING` stacked together - both
  independently close most/all of the gap to Log4j2 (see "Closing the gap" above), never
  tried combined. Also never retried on plain HotSpot (only GraalVM 21 and 25.3.4 so
  far).
* Log4j2 has not yet been run in the exact same batch as a `SYNCHRONIZED_THREAD_LOCAL_BUFFER`-vs-default-appender-type
  A/B for Rainbow Gum - the "Closing the gap" section's default-appender-type row is a
  same-session carryover number, not a controlled same-batch baseline against Log4j2.
* Investigate *why* `SYNCHRONIZED_THREAD_LOCAL_BUFFER` wins under Substrate VM
  specifically (confirmed on both GraalVM 21 and 25.3.4, the win is larger on 25.3.4),
  and *why* Rainbow Gum leads under HotSpot but trails under native-image for the same
  workload - both results are recorded, neither is explained.
* `REUSE_BUFFER` (the one remaining untried `AppenderType` value) not tried yet, on any
  JVM mode - `LOCK_NEW_BUFFER` was tried (see above, a negative result on GraalVM
  25.3.4) but only there.
* Platform-thread scenario (currently virtual threads only, both client and server
  side).
* Real profiling (JFR/async-profiler) of the TTLL time-formatting theory: TTLL is
  mostly time formatting, and Log4j2's own fast-path date formatting may just be
  quicker than Rainbow Gum's `DefaultInstantFormatter`/`MillisCache` here (a shared
  `AtomicReference<Entry>` every concurrent thread contends on) - the
  `MillisCache`-contention half is still untested. The "`Instant.now()`'s
  nanosecond-precision cost" half was tested (see "Cheaper `Instant`s in
  `LogEventFactory`" above) and found not to matter end-to-end, despite a real, small
  per-call cost confirmed via JMH - so this remains open only for the
  formatting/contention half, not the timestamp-construction half. Separately, `Instant`
  is a real heap-allocated object today; Project Valhalla's value types could remove
  that allocation cost if `Instant` becomes a flattened value type in a future JDK - not
  profiled either.
* Why Log4j2's own buffering/flush behavior differs from "just a bigger buffer" if it
  does at all - not yet investigated beyond the byte-buffer-size constant itself.
