# Logback issue review: feature demand and Rainbow Gum risks

Reviewed on **2026-10-02** against Rainbow Gum main at
`fdee6b69cd8c8925b10a4bdf65ae00ab97ab2fe8`.

The most useful feature signals are framework compatibility, configuration without
runtime compilation, scoped context, and logging-system observability. Rainbow Gum
already addresses several of these. The highest-priority correctness follow-ups are
async worker reentry and shutdown, incomplete file writes, diagnostic secret exposure,
and throwable handling.

## Scope and evidence

* Collected all **235 public issues indexed by GitHub Search** in `qos-ch/logback`:
  **124 open and 111 closed**, excluding pull requests.
* Scanned issue titles and metadata, ranked by thumbs-up reactions and comment count,
  and examined **47 selected issues**. Retrieved **285 comments across 40 threads**
  and reviewed relevant maintainer and user replies. The remaining seven selected
  issues had no comments.
* Read relevant Rainbow Gum implementation and existing tests. **No new runtime
  reproductions were run.** Findings below distinguish code risks, existing defenses,
  feature opportunities, and platform limitations.
* Counts refer to reactions on the issue itself and GitHub's issue comment count.
  They exclude reactions on individual comments. Comments indicate discussion, not
  necessarily independent users requesting a feature.
* GitHub state and maintainer conclusions sometimes disagree. For example, an issue
  can remain open with a `DONE` label and a comment describing its fix. Both are
  recorded where relevant.
* This covers GitHub issues, not the complete historical Jira backlog, GitHub
  Discussions, or the separate `logback-access` issue tracker. Related repositories
  are referenced only to explain an issue's outcome.

The browser could not load sorted issue-list pages, and the local GitHub CLI was
not authenticated. GitHub's unauthenticated public REST API worked for the inventory
and comments. There was no remaining access blocker.

To refresh the inventory, use the [public issue search API](https://api.github.com/search/issues?q=repo%3Aqos-ch%2Flogback%20is%3Aissue&per_page=100&page=1)
with `per_page=100` and successive pages. The ranking queries were
`repo:qos-ch/logback is:issue sort:reactions-+1-desc` and
`repo:qos-ch/logback is:issue sort:comments-desc`.

## Features with the strongest demand signals

The counts are modest for most individual features. These are the stronger signals
within this issue inventory, not claims of ecosystem-wide demand.

| Request | 👍 / comments | GitHub state and discussion outcome | Rainbow Gum relevance |
| --- | ---: | --- | --- |
| [#719: Jetty 12 access logging](https://github.com/qos-ch/logback/issues/719) | 9 / 5 | Closed. Maintainer reports support in the separate `logback-access` 2.0.0 release. | Framework upgrades can silently disable logging. Our versioned framework integrations should have real startup and output smoke tests. This request concerns HTTP access logging, not ordinary SLF4J integration. |
| [#757: conditions without Janino](https://github.com/qos-ch/logback/issues/757) | 8 / 8 | Closed. Maintainer identifies Logback 1.5.20 as providing conditions without Janino. | Rainbow Gum's Java configuration and simple-props profiles already avoid runtime expression compilation. Demonstrate switching sinks and encoders in native-image examples. Do not describe this as an unresolved Logback limitation. |
| [#917: virtual-thread MDC implementation](https://github.com/qos-ch/logback/issues/917) | 5 / 7 | Open. Discussion favors a separate scoped-context API rather than replacing mutable MDC directly. | Already substantially addressed by `rainbowgum-scopedkeyvalues-api` and `rainbowgum-scopedkeyvalues`. Emphasize child-task inheritance, nested scopes, and coexistence with MDC. |
| [#871: fluent logging and filtering parity](https://github.com/qos-ch/logback/issues/871) | 3 / 9 | Open, labeled `DONE`. Maintainer describes a fix; reporters confirm expected filtering and metrics behavior. | Classic and fluent calls must produce consistent routing and observability. This is a regression-test theme, not a request to reproduce TurboFilter's API. |
| [#754: async queue observability](https://github.com/qos-ch/logback/issues/754) | 2 / 7 | Open. Discussion requests queue occupancy, capacity, drops, and broader buffer/rotation metrics. | Our counters and Actuator bridge provide a foundation, but current `LogMetrics` has no queue occupancy gauges, blocked-producer duration, or worker health. These would be useful additions. |
| [#958: console performance diagnostic control](https://github.com/qos-ch/logback/issues/958) | 2 / 4 | Open. Maintainer explains that the message is informational and discusses less alarming wording. | Keep normal console onboarding quiet. An informational backlog should not look like an initialization failure when debug output or another warning causes it to be printed. |

The largest reaction counts are actually **maintenance requests**:

| Request | 👍 / comments | Discussion outcome | Lesson for Rainbow Gum |
| --- | ---: | --- | --- |
| [#745: security fix for Logback 1.2](https://github.com/qos-ch/logback/issues/745) | 33 / 17 | Closed. Maintainer reports the fix shipped in 1.2.13. | Framework dependency constraints can trap users on an old logging line. Publish support boundaries and a practical upgrade path. |
| [#899: further security backports to 1.2](https://github.com/qos-ch/logback/issues/899) | 9 / 13 | Closed. Maintainer declines a planned 1.2 backport and discusses bypassing Spring Boot's logging-system integration to upgrade. | Integration compatibility and release policy are product features. Keep the support policy explicit rather than implying indefinite maintenance. |
| [#889: security backport to 1.3](https://github.com/qos-ch/logback/issues/889) | 7 / 8 | Closed. Maintainer reports the relevant release as 1.3.15. | Users value fixes that preserve their JDK baseline. A tail release policy should state which fixes are eligible and how long a line is maintained. |

These requests are evidence of demand for maintenance, not evidence that Rainbow
Gum shares the referenced vulnerabilities.

## Smaller signals worth retaining

| Request | 👍 / comments | Assessment |
| --- | ---: | --- |
| [#952: simpler size-triggered rollover](https://github.com/qos-ch/logback/issues/952) | 0 / 9 | Closed. The request includes timestamped archive names without calendar-triggered rollover. Rainbow Gum already provides simple size-triggered numbered archives, but deliberately does not provide the timestamp naming portion. |
| [#731: interpolated JSON messages](https://github.com/qos-ch/logback/issues/731) | 0 / 7 | Closed. Logback added a separately configurable formatted-message field. Rainbow Gum's JSON encoders already write formatted messages; preserve this behavior across facades and async routes. See [existing JSON research](logback-json-encoder-bugs.md). |
| [#809: stack traces as JSON strings](https://github.com/qos-ch/logback/issues/809) | 0 / 6 | Open. Maintainer discusses a pluggable stack-trace strategy. Our `LogstashEncoder` already emits `stack_trace` as a string; the Logback-style encoder intentionally uses a structured throwable object. Prefer documenting the existing schema choices before adding another option. Some comments concern Git workflow rather than the feature. |
| [#710: thread ID in patterns](https://github.com/qos-ch/logback/issues/710) | 0 / 6 | Closed, labeled `DONE`. Unnamed virtual threads motivated the request. `LogFormatter.Builder.threadId()` and GELF's `_thread_id` already provide IDs; check whether users need a separate pattern keyword or JSON field rather than changing thread-name semantics. |
| [#1005: reusable encoder buffers](https://github.com/qos-ch/logback/issues/1005) | 0 / 4 | Open, although the author says they intend to close it after benchmarking. Our `LogEncoder.Buffer` design already addresses reuse. Treat allocation reduction as an architectural advantage to measure, not proof that replacing `byte[]` with `ByteBuffer` always increases throughput. |
| [#828: selective masking](https://github.com/qos-ch/logback/issues/828) | 0 / 4 | Closed. Maintainer points to replacement formatting and a later masked-key-value converter. A consistent key-based redaction policy across text, JSON, and diagnostics remains worth considering for Rainbow Gum. |
| [#1044: OTLP without the OTel dependency](https://github.com/qos-ch/logback/issues/1044) | 1 / 1 | Open. A possible optional integration, but currently a weak demand signal. Evaluate delivery, retries, queue limits, and dependency size before adding a transport. |
| [#850: Zstandard compression](https://github.com/qos-ch/logback/issues/850) | 1 / 0 | Open. Keep lower priority. Our basic rolling output currently supports gzip; external rotation tools remain the more flexible compression path. |

Scoped-context demand also appears in [#990](https://github.com/qos-ch/logback/issues/990)
(1 👍, no comments). Although #917 remains open, Logback now has a separate
[scoped MDC repository](https://github.com/qos-ch/logback-scoped-mdc). An open issue
alone is insufficient evidence that the capability is absent.

## Potential bugs and regression targets

### 1. Async worker logs into its own full queue

**Priority: high. Classification: code risk, not reproduced in Rainbow Gum.**

[#788](https://github.com/qos-ch/logback/issues/788) (0 👍, no comments) describes an
encoder whose downstream Kafka operation logs an error. The error returns to the same
async queue; if that queue is full, its only consumer blocks waiting for itself.
[#786](https://github.com/qos-ch/logback/issues/786) (2 👍, 16 comments) is a broader
blocking report against an old Logback line, not a confirmed explanation of #788.

In [BlockingQueueAsyncLogPublisher](../core/src/main/java/io/jstach/rainbowgum/publisher/BlockingQueueAsyncLogPublisher.java),
`log()` calls `queue.put(event)` without checking whether the caller is its worker.
Appender locking and reentry flags do not protect a call that blocks at the publisher
before reaching the appender. Synchronous alert listeners can also log back into that
route. The [Disruptor publisher](../rainbowgum-disruptor/src/main/java/io/jstach/rainbowgum/disruptor/DisruptorLogPublisher.java)
uses a blocking sequence claim and deserves the same scenario.

**Follow-up:** fill a bounded queue while its worker is inside an encoder/output
callback, then make that callback log to the same route. Use deterministic coordination
and a bounded completion assertion. Specify whether to drop, use a failsafe output,
or otherwise handle worker reentry; do not simply introduce unbounded inline recursion.

### 2. Shutdown races with blocked producers and slow outputs

**Priority: high. Classification: code risk, not reproduced in Rainbow Gum.**

[#1025](https://github.com/qos-ch/logback/issues/1025) (0 👍, 4 comments) concerns orphan
async workers in older Logback. The maintainer explains that unreferenced appenders
are not started in 1.3 and later, and the reporter closes the issue. It is useful as a
lifecycle test pattern, not a current general Logback defect.

Our blocking publisher clears `running`, interrupts the worker, and joins for one
second. A producer can pass the `running` check before shutdown and remain in
`queue.put()` while the worker performs its final drain. The worker's final drain and
appender close also sit outside its main exception handler. Disruptor `close()` calls
`halt()`, with no explicit graceful drain or appender close in that method.

**Follow-up:** concurrent close with a full queue; a producer completing after the final
drain; a worker blocked in I/O; and a failure during final drain. Assert worker
termination, resource closure, and exact written/lost-event accounting. Existing
[blocking publisher tests](../test/rainbowgum-test-core/src/test/java/io/jstach/rainbowgum/publisher/BlockingQueueAsyncLogPublisherAdditionalTest.java)
cover several lifecycle and interruption cases, but do not settle all these races.

### 3. File writes and shared-filesystem assumptions

**Priority: high. Classification: code risk plus platform limitation.**

[#973](https://github.com/qos-ch/logback/issues/973) (0 👍, 3 comments) reports corrupted
records with prudent mode and shared storage. The maintainer suspects the custom
rotation policy; a reporter later says a standalone Java file-lock program also
reproduces the corruption on SMB. This does not establish a Logback-only bug.

In [FileOutput](../rainbowgum-file/src/main/java/io/jstach/rainbowgum/file/FileOutput.java),
`FileChannelOutput.write()` calls `channel.write(buffer)` once and ignores the returned
byte count. The [JDK FileChannel contract](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/nio/channels/FileChannel.html#write(java.nio.ByteBuffer))
allows fewer bytes than the buffer contains to be written. A short write could therefore
lose part of an event without throwing or incrementing `events.failed`; rolling's
byte counter would still advance by the requested length.

**Follow-up:** audit the write-completion contract and test large records and short
writes. Treat cross-process file locking and rotation as separate guarantees. Validate
correctness on supported shared filesystems rather than inferring it from local tests.

### 4. Multiple outputs resolve to the same file

**Priority: high. Classification: code risk, not reproduced in Rainbow Gum.**

[#959](https://github.com/qos-ch/logback/issues/959) (0 👍, 9 comments) exposed concurrent
collision-map mutation. Logback moved collision checking into configuration analysis.
[#1041](https://github.com/qos-ch/logback/issues/1041) (0 👍, 2 comments) then reports
dynamic appenders escaping that analysis; the maintainer proposes additional coverage.

Our [output registry](../core/src/main/java/io/jstach/rainbowgum/LogOutputRegistry.java)
rejects duplicate output names, not duplicate physical file targets. Two differently
named outputs can have separate appender locks while writing or rotating the same file.
One appender's lock does not protect the other output's archive operations.

**Follow-up:** configure two outputs for one path, including relative/absolute aliases
and symlinks. Decide whether shared ordinary writes are supported and whether duplicate
rolling targets should fail configuration. Avoid assuming URI string equality proves
physical-file equality.

### 5. Size limits, interrupted rotation, and abandoned temporary files

**Priority: high for regression coverage. Classification: existing defenses with gaps to investigate.**

[#728](https://github.com/qos-ch/logback/issues/728) (0 👍, 17 comments) describes disk
exhaustion under rapid rolling. It remains open with `DONE`; the maintainer recommends
newer byte-counting releases and separately acknowledges an initial-size bug.
[#921](https://github.com/qos-ch/logback/issues/921) (0 👍, 3 comments) describes temporary
files accumulating after clock resets and archive collisions.

Our [rolling output](../rainbowgum-file/src/main/java/io/jstach/rainbowgum/rolling/DefaultRollingFileOutput.java)
tracks initial size and bytes written, has recovery state, and uses `roll.fail`.
The [rotation policy](../rainbowgum-file/src/main/java/io/jstach/rainbowgum/rolling/RollingPolicy.java)
compresses synchronously to temporary files and cleans up ordinary failures. Numbered
size-based rotation avoids Logback's calendar/counter collision mechanism. Existing
[real-file tests](../test/rainbowgum-test-file/src/test/java/io/jstach/rainbowgum/rolling/RollingFileOutputTest.java)
cover failures before and after the active file moves.

**Follow-up:** process termination during gzip; repeated failures after partial archive
shifts; starting with an oversized active file; and cleanup failures under sustained
load. Temporary files left by process death are outside the normal catch path and the
numbered-archive retention scan. `totalSizeCap` limits archives, not the active file
plus every temporary file. An individual oversized event can also exceed `maxFileSize`.

### 6. Compression blocks logging and may use undersized output buffers

**Priority: medium. Classification: performance hypothesis.**

[#980](https://github.com/qos-ch/logback/issues/980) (0 👍, 10 comments) identifies a
small gzip output buffer. The maintainer applies changes and reports a modest improvement
on an HDD VM; this is not evidence of a universal large gain.

Our `RollingPolicy.gzip()` uses the default `GZIPOutputStream` buffer directly over
`Files.newOutputStream()`. Rotation and compression run inside the appender's protected
write operation, so slower compression can delay all producers for that output.

**Follow-up:** benchmark representative archive sizes and storage with a larger gzip
buffer and buffered underlying output. Measure producer latency as well as throughput.
Retain synchronous ordering unless async compression's additional lifecycle and
retention complexity is explicitly justified.

### 7. Secrets escape through debug reports or property errors

**Priority: high. Classification: code risk, not reproduced in Rainbow Gum.**

[#986](https://github.com/qos-ch/logback/issues/986) (0 👍, 1 comment) reports substituted
keystore passwords appearing in internal status output. It remains open with `DONE`;
the maintainer describes masking selected sensitive variable names.

Our [reporter](../core/src/main/java/io/jstach/rainbowgum/LogReporter.java) prints output
URIs and component-supplied reports. [Property conversion diagnostics](../core/src/main/java/io/jstach/rainbowgum/LogProperty.java)
can include resolved values and exception messages. There is no common redaction
policy at these call sites. The built-in global-properties report uses a fixed key
list; this is **not** a claim that it dumps every environment variable or property.

**Follow-up:** put canary credentials in URI user-info/query parameters and custom
property values, then inspect debug output, alerts, reports, and conversion errors.
Define redaction across these surfaces together. Masking only a few key-name substrings
will not cover credentials embedded in URIs or arbitrary exception messages.

### 8. Unexpected throwable data, cause cycles, and uncaught errors

**Priority: high. Classification: code risk, not reproduced in Rainbow Gum.**

[#1040](https://github.com/qos-ch/logback/issues/1040) (0 👍, 2 comments) reports logging
failure when an exception returns null stack frames. The maintainer describes a fix.
This is an unusual input outside normal JDK throwable behavior, but logging should
remain useful when reporting damaged application state.

Our [Logback-style JSON encoder](../rainbowgum-json/src/main/java/io/jstach/rainbowgum/json/encoder/LogbackJsonEncoder.java)
dereferences each frame and recursively follows causes. It stops an immediate
self-reference but has no visited set for a cycle involving two or more throwables.
A cycle can therefore lead to `StackOverflowError`, which is outside appenders'
`catch (Exception)` handling. This cycle concern is an additional finding inspired
by the throwable review, not the mechanism reported in #1040. Our text throwable
formatters already use identity-based visited sets.

**Follow-up:** compare text and JSON behavior for null frames from an overriding
throwable, cyclic causes, deep cause chains, suppressed cycles, and a throwing
`toString()`. Specify useful fallback output and metric/alert behavior.

### 9. Interrupt status is preserved, but lost events need accounting

**Priority: medium. Classification: existing defense and observability gap.**

[#998](https://github.com/qos-ch/logback/issues/998) (0 👍, no comments) reports a
socket appender swallowing interruption. Rainbow Gum's blocking publisher restores
the interrupt flag after a failed queue put, and prudent file output preserves an
already-set flag around its write. Existing tests check several interruption cases.

The failed queue put records an alert but does not increment the standard
`events.dropped` or `events.failed` counters. A class-named error counter is not the
same as aggregate lost-event accounting.

**Follow-up:** choose the appropriate standard counter for interrupted publication,
and verify interrupts arriving during shutdown and file-lock acquisition, not just
before the operation begins.

### 10. Context and caller data must be captured on the producer

**Priority: medium. Classification: existing defenses worth regression tests.**

[#709](https://github.com/qos-ch/logback/issues/709) (3 👍, 3 comments) describes a custom
MDC adapter diverging from the context's adapter. [#871](https://github.com/qos-ch/logback/issues/871)
shows classic/fluent behavior diverging. [#1059](https://github.com/qos-ch/logback/issues/1059)
(0 👍, no comments) describes caller extraction occurring on the async worker and
producing unusable location information.

Our [event handler](../rainbowgum-slf4j/src/main/java/io/jstach/rainbowgum/slf4j/LogEventHandler.java)
merges scoped defaults with MDC; the [fluent builder](../rainbowgum-slf4j/src/main/java/io/jstach/rainbowgum/slf4j/RainbowGumEventBuilder.java)
copies defaults when needed; and the [router](../core/src/main/java/io/jstach/rainbowgum/LogRouter.java)
freezes events before async publication. These are meaningful defenses, not reasons
to skip integration coverage.

**Follow-up:** compare classic and fluent calls across synchronous, async, and mixed
routes. Mutate MDC and argument objects after publication; cross a scoped boundary;
and verify caller identity and precedence between fluent values, MDC, and scoped
defaults. Include loggers created before Rainbow Gum binds.

### 11. Closing one logging system must not close process stdout

**Priority: medium. Classification: existing defense plus platform regression target.**

[#1063](https://github.com/qos-ch/logback/issues/1063) (0 👍, 1 comment) describes Jansi
closing the underlying stdout descriptor during context reset. The maintainer points
to fixes. [#753](https://github.com/qos-ch/logback/issues/753) (0 👍, **22 comments**, the
most-commented issue in this inventory) concerns Windows console-host differences;
the discussion identifies Jansi installation and a Logback 1.5.17 fix.

Our standard console outputs have no-op close methods, and
[JAnsiConfigurator](../rainbowgum-jansi/src/main/java/io/jstach/rainbowgum/jansi/JAnsiConfigurator.java)
calls `AnsiConsole.systemInstall()`. Nevertheless, installation is bypassed under
Surefire, so normal unit tests do not exercise the real console setup path.

**Follow-up:** use a separate JVM with Jansi enabled, close/rebind Rainbow Gum, and
verify both application output and logging still reach stdout/stderr. Exercise
Windows Console Host, Windows Terminal, and redirected output where available.

### 12. Integration and property-resolution changes can silently change behavior

**Priority: medium. Classification: compatibility and regression theme.**

[#885](https://github.com/qos-ch/logback/issues/885) (5 👍, 10 comments) describes a
converter registry change in a patch release; the maintainer adds compatibility and
version-mismatch diagnostics. [#931](https://github.com/qos-ch/logback/issues/931)
(0 👍, 4 comments) shows a third-party extension path still breaking; it closes as
`WONT_FIX` with an extension upgrade recommended.

[#1016](https://github.com/qos-ch/logback/issues/1016) (0 👍, 10 comments),
[#1065](https://github.com/qos-ch/logback/issues/1065) (0 👍, 8 comments), and
[#1034](https://github.com/qos-ch/logback/issues/1034) (0 👍, 2 comments) show changes in
conditional configuration, substitution order, and missing-resource handling. Fixes
are described in their discussions; #1034 remains open with `DONE`.

**Follow-up:** test supported framework/module version combinations, extension
registrations, and property precedence together. For simple-props, retain cases for
no root resource, implicit optional `default`, explicit missing profiles, and whole-list
profile replacement. Rainbow Gum does not use Logback's XML model or classpath watcher,
so those exact implementation bugs do not transfer.

## Other lessons already reflected in Rainbow Gum

* [#767](https://github.com/qos-ch/logback/issues/767) (4 👍, 12 comments) and
  [#737](https://github.com/qos-ch/logback/issues/737) (0 👍, 11 comments) illustrate
  that introducing virtual-thread workers can expose blocking and initialization
  behavior. The #767 maintainer says Logback 1.5.13 stopped using virtual threads.
  Our configurable appender locking and platform-thread async worker avoid treating
  virtual-thread adoption as an automatic performance improvement. Test the actual
  supported JDK and I/O combinations.
* [#1038](https://github.com/qos-ch/logback/issues/1038) (0 👍, no comments) reports a
  logger count updated under different per-node monitors. Our logger registry uses a
  concurrent set and increments its `LongAdder` metric only when a name is newly
  added. Retain concurrent registration tests; an appender lock would not protect
  this unrelated registry state.
* [#933](https://github.com/qos-ch/logback/issues/933) (1 👍, no comments) reports
  archive names containing regex metacharacters being interpreted as regex syntax.
  Our archive index matcher uses `Pattern.quote()` for literal portions. Test brackets,
  parentheses, dots, and similar names during startup cleanup as well as rotation.
* [#865](https://github.com/qos-ch/logback/issues/865) (0 👍, 2 comments) concerns missing
  file headers after rotation. Our encoder API has no file-header/footer lifecycle,
  so the exact bug does not currently apply. If those features are introduced, treat
  every replacement file as a new lifecycle boundary.
* [#872](https://github.com/qos-ch/logback/issues/872) (0 👍, 2 comments) attributes an
  SLF4J startup message to Logback. The maintainer explains its actual source. Our
  diagnostics should identify which facade, provider, or module emitted a message;
  changing Rainbow Gum's debug option should not imply control over SLF4J diagnostics.

## Recommended follow-up order

1. Reproduce async worker reentry and shutdown races with bounded, deterministic tests.
2. Review file-write completion, then test duplicate rolling targets and crash recovery.
3. Define diagnostic redaction and test throwable robustness across all encoder schemas.
4. Extend observability with queue occupancy, blocked-producer time, worker health, and
   consistent lost-event counters. Keep metrics exporters in integration modules.
5. Add framework/native-image and real-console smoke tests for existing capabilities.
6. Improve examples for scoped context, profiles, JSON schema selection, and simple
   rolling before adding lower-demand transports or compression algorithms.

Related local research: [Logback JSON encoder](logback-json-encoder-bugs.md) and
[third-party Logstash encoder memory investigation](logstash-logback-encoder-memory-leak.md).
Those are separate investigations and are not counted as GitHub demand signals here.

Author/model: **Codex (GPT-6)**.
