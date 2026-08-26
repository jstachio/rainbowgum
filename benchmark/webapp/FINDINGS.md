# Findings

Durable knowledge from the webapp benchmark investigation, kept short. For actual
numbers from specific runs (including which appenders/flags were confirmed active and
which git revision produced them), see `RESULTS.md`. This file is for facts about *why*,
not run-by-run numbers - it gets rewritten/trimmed as understanding improves rather than
appended to forever.

## Open: rainbowgum-tomcat has an unexplained ~15-17% throughput regression

Swapping in the `rainbowgum-tomcat` dependency (routes Tomcat's own internal
`org.apache.juli.logging.Log` calls through RainbowGum instead of JUL) reproducibly costs
~15-17% throughput versus the same app without it, in complete isolation
(`run-tomcat-jul.sh`). Three specific theories have been ruled out by direct
instrumentation, not just profiling:

- **Tomcat calling raw JUL directly, bypassing its own `Log` abstraction** - ruled out; a
  patched `SystemLoggerQueueJULHandler.publish()` saw zero hits during load.
- **Per-call `RainbowGumTomcatLog`/`TomcatLevelLog` overhead** - ruled out; JFR execution
  and allocation sampling show zero frames in `io.jstach.rainbowgum.tomcat` or
  `org.apache.juli` during steady-state load.
- **Reflective `Log` construction on every `LogFactory.getLog()` call** - ruled out; an
  exhaustive per-name counter showed ~76 total constructions, all during startup, zero
  during the load window.

The regression is real and reproduces consistently but its cause is still unidentified -
likely something structural (classpath/JIT shape, GC behavior, or an init-time cost) that
per-request profiling doesn't surface. Needs a clean non-shared environment and a GC/heap
diff to chase further.

## Why Logback wins under virtual threads and Log4j2/RainbowGum win under platform threads

Confirmed by reading `log4j-core` 2.25.4 and decompiling `logback-core`/`logback-classic`
1.5.34 (the exact versions Spring Boot 4.1's BOM pins - see `RESULTS.md` for how these
compare to each project's latest release).

**Log4j2's file/console appenders** (`AbstractOutputStreamAppender`/`OutputStreamManager`):
per-thread `StringBuilder` for pattern formatting, per-thread scratch `CharBuffer`/
`ByteBuffer` for the `CharsetEncoder` transcoding step - both entirely outside any lock.
Only the final encoded-bytes copy into the shared destination is `synchronized`. Buffer
size is 8KB by default (`RollingFileAppender`, what Spring Boot actually configures) -
same as RainbowGum/Logback; the 256KB buffer belongs only to
`RandomAccessFileAppender`, which Spring Boot's default config doesn't use.

**Logback's file/console appenders** (`OutputStreamAppender`, shared by
`FileAppender`/`ConsoleAppender`): the opposite strategy - **no persistent buffer at
all**. `PatternLayoutBase` allocates a fresh `StringBuilder(256)` on every single call;
`LayoutWrappingEncoder.encode()` then does exactly `s.getBytes(charset)` on the resulting
`String`, no `CharsetEncoder`, no `ThreadLocal`. Locking is a `ReentrantLock` guarding
only the raw `OutputStream.write()`+flush - the same "lock only the final write" shape as
Log4j2, so the lock primitive was never the differentiator (confirmed: Log4j2 already
uses this shape and still trails Logback under VT).

**`String.getBytes(UTF_8)` has a whole-string Latin1 fast path.** The JDK's compact
strings give every `String` a coder: `LATIN1` if every char is U+0000-U+00FF, `UTF16`
otherwise. `String.encodeUTF8` fast-paths the `LATIN1` case (a `countPositives()` check
plus, for pure ASCII, a straight array clone - no per-byte encoding work). The moment a
`String` contains **any** character above U+00FF (an emoji, most CJK, etc.), compact
strings flips the *entire* string's coder to `UTF16`, and encoding falls to a full
char-by-char, surrogate-aware path for the **whole line**, not just the offending
character. Logback's per-call `getBytes()` strategy lives or dies on this fast path;
Log4j2/RainbowGum's `CharsetEncoder`-based transcoding doesn't care either way. Verified
empirically: a single emoji in the logged message costs Logback ~7% throughput under VT
while Log4j2/RainbowGum are unaffected (see `RESULTS.md`).

**Net**: Logback's strategy (allocate fresh, let the JDK intrinsic do the encoding) wins
when messages are pure Latin1 and threads are cheap to spin up (virtual threads, so
there's no long-lived thread to amortize a reusable buffer against). Log4j2/RainbowGum's
strategy (reuse a per-thread buffer, encode outside the lock) wins under platform threads
regardless of content, and stays competitive under VT once content isn't pure ASCII.
RainbowGum's current default appender (`SYNCHRONIZED_THREAD_LOCAL_BUFFER` on JDK 24+,
`THREAD_LOCAL_BUFFER` below that) is Log4j2's strategy, not Logback's - deliberately, since
platform threads are still the common case and the VT gap is content-dependent rather
than fixed.

## How to run

`./run-all.sh` from this directory (env vars: `LOG_LEVEL`, `STRUCTURED_FORMAT=gelf`,
`VIRTUAL_THREADS=true`, combinable). `./run-tomcat-jul.sh` for the isolated Tomcat
regression test. Results land in `results/` (gitignored).
