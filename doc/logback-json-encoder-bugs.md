# Logback JsonEncoder design issues encountered

Found while building a Micronaut benchmark comparing RainbowGum against Logback's own
built-in JSON encoder (`benchmark/micronaut/`, see that module's `FINDINGS.md` for the
benchmark context). Kept here, in the same spirit as `doc/jdk-javadoc-bugs.md`/
`doc/jdk-jfr-bugs.md`, so it is easy to turn into a real bug report or discussion thread
against Logback (github.com/qos-ch/logback).

## `JsonEncoder`'s default output cannot be interpolated by anything that isn't SLF4J

- **Version confirmed on:** `logback-classic 1.5.37` (current release as of this
  writing), reading the actual shipped source
  (`ch.qos.logback.classic.encoder.JsonEncoder`, `@since 1.3.8/1.4.8`).
- **Symptom:** with `JsonEncoder`'s defaults, a log call like
  `log.info("received request name={}", "world")` produces:
  ```json
  {"sequenceNumber":0,"timestamp":1790495886147,"level":"INFO","threadName":"main",
   "loggerName":"...","context":{...},"mdc":{"requestId":"1"},
   "message":"received request name={}","arguments":["world"],"throwable":null}
  ```
  `message` is the **raw, unsubstituted SLF4J template**, `{}` and all. The actual
  human-readable line ("received request name=world") does not appear anywhere in the
  JSON by default. A `withFormattedMessage` flag exists that adds a `formattedMessage`
  field with the real interpolated string, but it **defaults to `false`**: the field
  that is actually useful for a JSON/structured-logging consumer is opt-in, while the
  raw-template field it is opt-in *against* needing (`withMessage`, defaults `true`)
  ships on.

- **Why this is a real problem, not just a style preference:**

  1. **The interpolation syntax is a private detail of whichever facade produced the
     event, not something a JSON consumer should need to know.** `{}` sequential
     placeholders are SLF4J's own convention. A log shipper, Kibana/Elasticsearch
     ingest pipeline, or any other downstream JSON consumer has no generic way to know
     that `{}` in `message` means "substitute the next element of `arguments`, in
     order": that is an implementation detail of `org.slf4j.helpers.MessageFormatter`,
     not part of JSON, not part of any log-shipping convention (ECS's own `message`
     field is documented as already being the human-readable line, not a template).
     Reconstructing the real message downstream means re-implementing SLF4J's exact
     substitution algorithm (including its escaping rules for a literal `\{}`) in
     whatever language the consumer happens to be written in.

  2. **That convention is not even universal within the JDK itself.** `java.lang.System.Logger`
     (`java.base/java/lang/System.java`, `Logger.log(Level, String format, Object...
     params)`) documents its own `format` parameter as following **`java.text.MessageFormat`**
     conventions instead: indexed placeholders (`{0}`, `{1}`, with optional
     `{0,number}`-style format elements), not SLF4J's sequential `{}`. A JSON schema
     that ships a raw template plus an argument array and expects the *consumer* to
     interpolate it has no way to say which convention applies to a given line (SLF4J's
     sequential `{}`, `java.text.MessageFormat`'s indexed `{0}`, or something else
     again, e.g. a `printf`-style facade) short of also emitting the origin facade as
     its own field. Fully resolving the message once, inside the logging framework,
     before it is ever written out, sidesteps the whole question.

  3. **When the `.toString()` on each argument actually happens is a real hazard, not
     just an interpolation-syntax inconvenience.** Reading the shipped source directly
     (`appendArgumentArray`):
     ```java
     protected void appendArgumentArray(StringBuilder sb, ILoggingEvent event) {
         Object[] argumentArray = event.getArgumentArray();
         ...
         sb.append(QUOTE).append(jsonEscapedToString(argumentArray[i])).append(QUOTE);
         ...
     }
     private String jsonEscapedToString(Object o) {
         if (o == null) return NULL_STR;
         return jsonEscapeString(o.toString());
     }
     ```
     `.toString()` is called on each argument **inside `encode(ILoggingEvent event)`**,
     i.e. whenever the appender actually writes the event out. For a synchronous
     appender that is effectively immediate, but Logback ships its own `AsyncAppender`
     specifically to move that work onto a separate worker thread, off a queue,
     meaning `.toString()` runs later, on a different thread, than the original log
     call. If the argument is a mutable object (a collection still being built, a
     domain object whose fields change after the log call returns, a `StringBuilder`
     reused by the caller), the JSON's `arguments` (and even `message`/
     `formattedMessage` if using `withFormattedMessage`) can end up describing the
     object's state at whatever later moment the async worker happened to encode it,
     not its state at the actual log call. This is a well-known category of bug for any
     logging design that defers formatting; `getFormattedMessage()` (used only when
     `withFormattedMessage=true`) has exactly the same exposure, since `ILoggingEvent`
     itself defers formatting the same way, for the same reason.

- **Contrast with RainbowGum's approach**, for a concrete existing example of the
  alternative: `LogEvent.freeze()` (`core/src/main/java/io/jstach/rainbowgum/LogEvent.java`)
  is called on every event **before** it is handed to an async publisher's queue
  (`LogRouter.Router.log(LogEvent)`, "only async publishers need a frozen ... event"),
  and `freeze(Instant)` eagerly calls `formattedMessage(StringBuilder)` right there, on
  the original calling thread, capturing the fully-interpolated `String` into the frozen
  event before any queueing happens:
  ```java
  // OneArgLogEvent
  public LogEvent freeze(Instant timestamp) {
      StringBuilder sb = new StringBuilder(message.length());
      formattedMessage(sb);
      return new DefaultLogEvent(timestamp, ..., sb.toString(), ...);
  }
  ```
  Every RainbowGum JSON encoder (including `LogbackJsonEncoder`, which otherwise
  deliberately mirrors `JsonEncoder`'s schema, see `rainbowgum-json`) always writes the
  already-resolved message; there is no raw-template-plus-arguments mode at all, and no
  facade-specific placeholder syntax ever reaches the JSON output.

- **Steelmanning the other side, for a fair report:** a raw template plus arguments is
  not pure oversight: it is the same idea behind "message templates" in Serilog/
  Datadog-style structured logging, where grouping/deduplicating log lines by their
  *template* (independent of the interpolated values) is a real, useful query pattern.
  But those systems normalize the template into their own canonical placeholder syntax
  rather than shipping whatever the origin framework's native syntax happened to be, and
  none of them ship the *template-only* view as the default with the *resolved* view
  opt-in; it is normally the other way around, or both are included unconditionally.

- **Discussion points for a report to Ceki / the logback-user list:**
  1. Should `withFormattedMessage` default to `true` for `JsonEncoder` specifically,
     given the entire point of this encoder is machine/downstream consumption? The
     current default optimizes for an encoder-time cost (calling
     `getFormattedMessage()`) that is only ever paid for an event already confirmed
     enabled and already being fully serialized anyway: there is no filtering
     scenario left to protect by deferring it.
  2. Should the javadoc call out explicitly that `arguments`' `.toString()` calls (and
     `getFormattedMessage()`, if enabled) happen at encode time, not at the original
     logging call site, given `AsyncAppender` is a first-class, commonly recommended
     part of Logback's own design?
