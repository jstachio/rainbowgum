# GraalVM native image benchmark results

Methodology: see [README.md](README.md). Each app run as a real native executable
(GraalVM 21 Oracle distro, `21.0.12-graal`), driven by
`rainbowgum-benchmark-native-driver` over plain HTTP/1.1, concurrency 50 (virtual
threads on both client and server side), 5s warmup (discarded) + 20s measured, against
`GET /greet/world`. RSS sampled from `/proc/<pid>/status` while the measurement ran.

| | Rainbow Gum | Log4j2 | Logback |
|---|---:|---:|---:|
| throughput | 23,057 req/s | **26,846 req/s** | 24,035 req/s |
| p50 latency | 2.13 ms | **1.65 ms** | 2.04 ms |
| p99 latency | 4.84 ms | 5.72 ms | **4.64 ms** |
| RSS avg | **45.7 MB** | 81.4 MB | 50.6 MB |

Checked for run-to-run stability with a second, independent pass (different warmup/
duration, same relative ordering held): Log4j2 led throughput and had the lowest
latency both times; Rainbow Gum led RSS (lowest memory) both times, by a wide and
consistent margin over Log4j2 specifically (roughly half).

## Honest read

Log4j2 is ahead here on raw throughput and p50 latency, not Rainbow Gum. This is
consistent with (not a departure from) `why_rainbowgum_is_better.md`'s own existing
"Fast" section, which already notes: *"on plain platform threads with no structured
logging, Log4j2 still leads there; that specific gap isn't closed."* This benchmark
uses virtual threads on both client and server, and TTLL (not structured) output, and
Log4j2 still leads - so that already-acknowledged gap shows up here too, not just on
platform threads.

Memory tells a different, clearly favorable story: Rainbow Gum uses roughly half the
resident memory of Log4j2 under sustained load, consistent with the module/image-size
story already established in `why_rainbowgum_is_better.md`'s "Small" section (Log4j2's
native image itself is also markedly larger - 25.86MB code area vs Rainbow Gum's
9.53MB, from the build output in this same session).

## Not yet tried

Rainbow Gum's per-appender locking strategy (`logging.appender.<name>.type`,
`AppenderType`) is configurable and untouched here (default:
`LOCK_THREAD_LOCAL_BUFFER`) - worth revisiting against this specific result before
drawing final conclusions, but deliberately not chased yet.
