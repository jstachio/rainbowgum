# RainbowGum TTLL-to-stdout(file) vs JFR LogOutput

Same jar, same `SLF4JBenchmark` workload (see `rainbowgum-benchmark-share`), same JDK
(Temurin 26.0.2), same machine, run back to back and interleaved (order swapped between
batches, made no difference) via `run-ttll.sh`/`run-jfr.sh`. Only the `console` appender's
`output` property differs:

- **TTLL**: default (`output=stdout`, default TTLL encoder), stdout redirected to a plain
  text file - the common "just log to stdout, let the platform capture it" pattern.
- **JFR**: `output=jfr:///<path>` - `JfrLogOutput` starts and owns its own Flight Recorder
  session writing straight to that file for the process lifetime (see
  `doc/overview.html`'s JFR section).

`DURATION` is `SLF4JBenchmark`'s own in-process `System.nanoTime()` measurement of just
the logging workload (excludes JVM startup). "Wall clock" and "Max RSS" are the whole
`java` process, from `/usr/bin/time -v`. `N` virtual threads x 10 inner iterations x 7
log calls each (4 of the 7 carry an exception - see `SLF4JBenchmark.runSingleThread`).

## N = 1000 (5 interleaved trials each, order alternated)

| | DURATION (ms) | Wall clock (s) | Max RSS (MB) | Output file |
|---|---|---|---|---|
| TTLL (trial 1-5) | 569, 542, 562, 509, 593 | 0.70, 0.70, 0.69, 0.69, 0.73 | 230, 226, 241, 247, 243 | 19.6 MB (text) |
| JFR (trial 1-5) | 326, 327, 359, 362, 361 | 0.57, 0.55, 0.62, 0.60, 0.60 | 255, 259, 248, 253, 265 | 2.6 MB (binary) |

Reversed-order check (JFR first, TTLL second), 3 more trials - same pattern:

| | DURATION (ms) | Wall clock (s) | Max RSS (MB) |
|---|---|---|---|
| JFR | 355, 325, 359 | 0.61, 0.61, 0.64 | 252, 251, 261 |
| TTLL | 556, 548, 606 | 0.69, 0.72, 0.75 | 251, 234, 254 |

## N = 5000 (1 trial each, sanity-checking scale)

| | DURATION (ms) | Wall clock (s) | Max RSS (MB) | Output file |
|---|---|---|---|---|
| TTLL | 2246 | 2.44 | 609 | 98.2 MB (text) |
| JFR | 1291 | 1.61 | 721 | 12.6 MB (binary) |

## Takeaways

- **Speed**: JFR is consistently faster for the actual logging workload, by roughly
  35-45% (e.g. at N=1000, ~555ms average for TTLL vs ~345ms for JFR; ratio holds at
  N=5000 too: 2246ms vs 1291ms). Whole-process wall clock shows the same ordering.
  Not investigated further here *why* - plausibly JFR's native commit path avoiding
  per-write text formatting/locking that TTLL-to-a-single-stdout-stream pays under
  concurrent virtual threads, but that's a hypothesis, not verified.
- **Memory**: JFR's max RSS runs a bit higher than TTLL's at N=1000 (roughly 5-10%
  higher), and the gap widens somewhat at N=5000 (721MB vs 609MB, ~18% higher) -
  consistent with JFR's own chunk-buffer/metadata infrastructure carrying some
  overhead that scales with recording volume, on top of whatever the JVM/logging
  path itself allocates.
- **Disk footprint**: JFR's binary format is dramatically smaller for the same event
  count, consistently ~7.5-8x smaller than the TTLL text file at both scales tested
  (2.6 MB vs 19.6 MB at N=1000; 12.6 MB vs 98.2 MB at N=5000).

## Reproducing

```
cd benchmark/rainbowgum-benchmark-jfr
../../mvnw -q -pl benchmark/rainbowgum-benchmark-jfr -am install -DskipTests
./run-ttll.sh 1000
./run-jfr.sh 1000
```
