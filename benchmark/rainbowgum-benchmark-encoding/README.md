# Encoding benchmark

This module isolates layout/encoder cost from filesystem and console output. It uses
fixed synthetic events matching the native HTTP benchmark's TTLL pattern and writes to
in-memory counting sinks only.

Run:

```sh
./mvnw -f benchmark/rainbowgum-benchmark-encoding/pom.xml package
java -jar benchmark/rainbowgum-benchmark-encoding/target/rainbowgum-benchmark-encoding-0.12.0-SNAPSHOT.jar
```

Useful options:

```sh
java -jar target/rainbowgum-benchmark-encoding-0.12.0-SNAPSHOT.jar \
  --iterations 5000000 --warmup 1000000 --repetitions 5 --scenario ascii
```

Scenarios: `ascii`, `emoji`, `long`.

Modes:

* `log4j2-direct` - `PatternLayout.encode(event, ByteBufferDestination)`, the default
  direct-encoder path used by Log4j2 when `log4j2.enable.direct.encoders=true`.
* `log4j2-byte-array` - `PatternLayout.toByteArray(event)`, the non-direct fallback.
* `rainbowgum-bytes` - Rainbow Gum TTLL encoder with `WriteMethod.BYTES`.
* `rainbowgum-byte-buffer` - Rainbow Gum TTLL encoder with `WriteMethod.BYTE_BUFFER`.
* `rainbowgum-string` - Rainbow Gum TTLL encoder with `WriteMethod.STRING`.
* `rainbowgum-threadlocal-bytes` - Rainbow Gum TTLL encoder with `WriteMethod.BYTES`,
  retrieving the reused buffer from `ThreadLocal` per event.
* `rainbowgum-threadlocal-byte-buffer` - Rainbow Gum TTLL encoder with
  `WriteMethod.BYTE_BUFFER`, retrieving the reused buffer from `ThreadLocal` per event.
* `rainbowgum-threadlocal-string` - Rainbow Gum TTLL encoder with `WriteMethod.STRING`,
  retrieving the reused buffer from `ThreadLocal` per event.
* `rainbowgum-copy-chars` - Benchmark-only Rainbow Gum TTLL encoder that mirrors
  Log4j2's `TextEncoderHelper` shape: format into `StringBuilder`, copy with
  `StringBuilder.getChars(...)` into a reusable `CharBuffer`, then encode with a reused
  `CharsetEncoder` into a reused `ByteBuffer`.
* `rainbowgum-threadlocal-copy-chars` - Same benchmark-only copy-chars encoder, with
  the buffer retrieved from `ThreadLocal` per event.

This benchmark deliberately does not exercise Log4j2's `OutputStreamManager` shared
buffer or any appender lock. It is meant to answer whether the remaining gap is in
encoding/layout work after output buffering and flush behavior are removed. The
`rainbowgum-threadlocal-*` modes model the buffer lookup shape used by Rainbow Gum's
thread-local appender types without also adding appender locking. The
`rainbowgum-copy-chars` modes test whether Rainbow Gum's current `CharBuffer.wrap(...)`
encoding shape differs materially from Log4j2's `StringBuilder.getChars(...)` copy into a
reused `CharBuffer`.
