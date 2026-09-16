# Encoding benchmark results

Measured on September 16, 2026 in the local Codex sandbox worktree.

Source branch: `feature/encoding-benchmark`, based on `52714c1f` from
`origin/feature/graalvm-native-benchmark`, plus this benchmark module.

Runtime:

```text
openjdk version "26.0.2" 2026-07-21
OpenJDK Runtime Environment Temurin-26.0.2+10 (build 26.0.2+10)
OpenJDK 64-Bit Server VM Temurin-26.0.2+10 (build 26.0.2+10, mixed mode, sharing)
```

Command shape:

```sh
./mvnw -f benchmark/rainbowgum-benchmark-encoding/pom.xml package -DskipTests
java -jar benchmark/rainbowgum-benchmark-encoding/target/rainbowgum-benchmark-encoding-0.12.0-SNAPSHOT.jar \
  --iterations 5000000 --warmup 1000000 --repetitions 5 --scenario <scenario>
```

The runner reports the best of 5 measured repetitions after one warmup pass. This is a
simple standalone benchmark, not JMH. Treat the absolute numbers as directional; the
relative shape is the useful result.

## ASCII

Scenario input: `world`.

| case | ns/event | events/s | bytes/event |
|---|---:|---:|---:|
| `log4j2-direct` | 88.3 | 11,326,982 | 128.5 |
| `log4j2-byte-array` | 81.7 | 12,238,496 | 128.5 |
| `rainbowgum-bytes` | 296.1 | 3,377,492 | 128.5 |
| `rainbowgum-byte-buffer` | 297.5 | 3,360,943 | 128.5 |
| `rainbowgum-string` | 82.6 | 12,106,809 | 128.5 |

## Emoji

Scenario input: `🌍`.

| case | ns/event | events/s | bytes/event |
|---|---:|---:|---:|
| `log4j2-direct` | 98.8 | 10,116,723 | 127.8 |
| `log4j2-byte-array` | 95.4 | 10,482,496 | 127.8 |
| `rainbowgum-bytes` | 315.9 | 3,165,862 | 127.8 |
| `rainbowgum-byte-buffer` | 315.9 | 3,165,973 | 127.8 |
| `rainbowgum-string` | 114.1 | 8,764,963 | 127.8 |

## Long ASCII

Scenario input: `world-` plus 8 repeats of `abcdefghijklmnopqrstuvwxyz0123456789`.

| case | ns/event | events/s | bytes/event |
|---|---:|---:|---:|
| `log4j2-direct` | 88.1 | 11,345,159 | 201.5 |
| `log4j2-byte-array` | 82.1 | 12,184,889 | 201.5 |
| `rainbowgum-bytes` | 431.0 | 2,320,415 | 201.5 |
| `rainbowgum-byte-buffer` | 432.2 | 2,313,620 | 201.5 |
| `rainbowgum-string` | 86.5 | 11,559,628 | 201.5 |

## Thread-local Rainbow Gum buffer lookup

After confirming that Log4j2's direct `PatternLayout.encode(...)` path uses
`StringBuilderEncoder`, which keeps a thread-local `CharsetEncoder`, `CharBuffer`, and
`ByteBuffer`, the benchmark was extended with `rainbowgum-threadlocal-*` modes. These
retrieve Rainbow Gum's reused `LogEncoder.Buffer` from a `ThreadLocal` for every event,
matching the lookup shape used by `LockThreadLocalBufferLogAppender` and
`SynchronizedThreadLocalBufferLogAppender`, but still deliberately omit appender locking
and output I/O.

Clean sequential ASCII run, same command shape and measurement settings as above:

| case | ns/event | events/s | bytes/event |
|---|---:|---:|---:|
| `log4j2-direct` | 89.3 | 11,194,415 | 128.5 |
| `log4j2-byte-array` | 83.3 | 12,005,063 | 128.5 |
| `rainbowgum-bytes` | 303.0 | 3,300,062 | 128.5 |
| `rainbowgum-byte-buffer` | 304.5 | 3,284,155 | 128.5 |
| `rainbowgum-string` | 82.5 | 12,117,755 | 128.5 |
| `rainbowgum-threadlocal-bytes` | 309.6 | 3,229,480 | 128.5 |
| `rainbowgum-threadlocal-byte-buffer` | 305.9 | 3,269,515 | 128.5 |
| `rainbowgum-threadlocal-string` | 84.1 | 11,886,546 | 128.5 |

In this isolated benchmark, adding a per-event `ThreadLocal.get()` on Rainbow Gum's side
changes little. The bytes path slowed by about 2.2%, byte-buffer by about 0.5%, and
string by about 1.9%. That suggests the large `rainbowgum-bytes` /
`rainbowgum-byte-buffer` gap here is not explained by Log4j2 having layout-level
thread-local encoder state while the synthetic Rainbow Gum baseline kept its buffer in a
plain field. The larger difference remains the encoding strategy itself: Rainbow Gum's
current `CharsetEncoder` path versus Log4j2's `StringBuilderEncoder` implementation and
Rainbow Gum's own `String.getBytes` path.

## Reading

With output and appender locking removed, Rainbow Gum's `STRING` path is close to Log4j2
for pure ASCII content and scales similarly on the longer ASCII message. Rainbow Gum's
`BYTES` and `BYTE_BUFFER` paths are much slower in this isolated encoder benchmark,
which points at the current `CharsetEncoder`-based path as a major remaining cost.

The emoji scenario shows the expected tradeoff: Rainbow Gum's `STRING` path loses more
than it does for ASCII, consistent with leaving the compact-string Latin1 fast path.
Even there, it remains much faster than Rainbow Gum's `CharsetEncoder` paths in this
benchmark.

Log4j2's direct encoder was not faster than `toByteArray` in this HotSpot run; they were
close, with `toByteArray` slightly ahead in these samples. That does not contradict the
native HTTP benchmark's output-buffering investigation: this benchmark intentionally
bypasses `OutputStreamManager`, appender synchronization, and filesystem writes.
