# Logger name edge cases in Logback, Log4j2 and Rainbow Gum

Logger names are usually class names, but a facade accepts any string, and some libraries
build names from data. Testcontainers, for example, names loggers after image names such as
`tc.floci/floci-gcp@sha256:405c128b...` (Rainbow Gum issue
[#752](https://github.com/jstachio/rainbowgum/issues/752)). This note records what Logback,
Log4j2 and Rainbow Gum do with unusual names, so the differences and bugs can be raised with
each project. Kept in the same spirit as `doc/logback-json-encoder-bugs.md` and
`doc/jdk-jfr-bugs.md`.

## Versions and method

- Logback `logback-classic` 1.5.37 with `slf4j-api` 2.0.17.
- Log4j2 `log4j-core` 2.26.1.
- Rainbow Gum 1.0.0-SNAPSHOT (main at `5d70b6cc`, plus the two fixes below).
- JDK: Temurin 27+35.

Each name was run through each framework directly, not through SLF4J:

- **Logback:** a fresh `LoggerContext` with the root at WARN. `getLogger(name)`, then the
  parent (the name up to its last dot) set to DEBUG, then a `PatternLayout` of
  `%logger|%logger{0}|%logger{1}|%logger{5}|%logger{10}|%logger{20}` on a `LoggingEvent`.
- **Log4j2:** a programmatic configuration with the root at WARN. `getLogger(name)`, then a
  `LoggerConfig` for the parent at DEBUG, then a `PatternLayout` of
  `%c|%c{1}|%c{2}|%c{1.}|%c{-1}|%c{1.1.~}` on a `Log4jLogEvent`.
- **Rainbow Gum:** `logging.level=warn` with either `logging.level.<parent>=debug` or the
  exact `logging.level.<name>=debug`, supplied as a map since a properties file cannot hold
  every name as a key. One event per name through the TTLL, logfmt, JSON5, GELF, ECS,
  Logstash and Logback JSON encoders, and the pattern encoder with Logback's pattern above.

The Rainbow Gum results are asserted by `LoggerNameEdgeCaseTest` in
`test/rainbowgum-test-kitchensink`, which runs every name with all optional modules loaded.

In the tables, control characters in names and in text output are shown as `⟨LF⟩`, `⟨CR⟩`,
`⟨TAB⟩`, `⟨NUL⟩` and `⟨ESC⟩`. JSON and logfmt values are shown exactly as written, so `\n`
there is a backslash followed by `n`. A `-` in a parent column means the name has no
non-empty parent.

## Bugs worth reporting

### Abbreviation splits a surrogate pair (Logback and Log4j2)

When abbreviation shortens a segment to its first character, both keep the first UTF-16
`char`, not the first code point. A segment that starts with a character outside the Basic
Multilingual Plane, such as an emoji, is cut in half and the output holds a lone high
surrogate. Encoding that to UTF-8 writes `?` in place of the character. Every shortened
segment is affected: Logback's `%logger{1}` of `😀.😀.😀` is `?.?.😀` and of `😀.b` is `?.b`,
and Log4j2's `%c{1.}` gives the same. A literal `?` in a name, as in `???` or `a.?.b`, is
kept as is by all three.

| Framework | Pattern | Name | Output (UTF-16 code units) |
|---|---|---|---|
| Logback 1.5.37 | `%logger{1}` | `a.😀.b` (`0061 002E D83D DE00 002E 0062`) | `0061 002E D83D 002E 0062` |
| Log4j2 2.26.1 | `%c{1.}` | same | `0061 002E D83D 002E 0062` |

Logback reproduction:

```java
var ctx = new LoggerContext();
ctx.start();
var layout = new PatternLayout();
layout.setContext(ctx);
layout.setPattern("%logger{1}");
layout.start();
var event = new LoggingEvent("fq", ctx.getLogger("a.😀.b"), Level.INFO, "m", null, null);
String out = layout.doLayout(event); // "a.\uD83D.b": a lone high surrogate
```

Log4j2 reproduction:

```java
var layout = PatternLayout.newBuilder().withPattern("%c{1.}").build();
var event = Log4jLogEvent.newBuilder()
    .setLoggerName("a.😀.b")
    .setLevel(Level.INFO)
    .setMessage(new SimpleMessage("m"))
    .build();
String out = layout.toSerializable(event); // "a.\uD83D.b"
```

Rainbow Gum's pattern encoder ported Logback's `TargetLengthBasedClassNameAbbreviator` and
had the same bug: its UTF-8 output was `a.?.b`. It now keeps the whole first code point. A
web search found no existing report against either project; that search was not
exhaustive.

## Rainbow Gum bugs found and fixed

- **Non-ASCII text in list, map and URI query values was corrupted.** `PercentCodec.decode`
  wrote each character that was not percent encoded as its low byte, which is only right
  for ASCII. `logging.group.g=café.Ünïcode` read back as `caf�.�n�code`, so the group's
  level silently never applied, and an emoji came back as `=` and a NUL. The same decoding
  is used for every list property and URI query value, so a query value such as
  `ecs:///?serviceName=café` was affected too. Characters that are not ASCII are now kept
  as is.
- **The abbreviator split surrogate pairs**, as described above.

## Differences that are behaviour, not bugs

These are worth knowing when moving between frameworks, but each is a defensible choice.

- **The empty name.** Log4j2's root logger is named `""`, so `getLogger("")` is the root.
  Rainbow Gum also treats `""` as the root: `logging.level` is its key. Logback creates a
  separate logger named `""`, a child of `ROOT`.
- **`root` as a name.** Logback returns its root logger for `root` in any case (`root`,
  `ROOT`, `Root`), since `Logger.ROOT_LOGGER_NAME` is matched case-insensitively. Log4j2
  treats all three as ordinary loggers. Rainbow Gum treats lowercase `root` as the root,
  because `logging.level.root` is the root's alias (as in Spring Boot), so a logger named
  `root` cannot be given its own level. `ROOT` and `Root` are ordinary loggers.
- **`$` in the hierarchy.** Logback splits names at `$` as well as `.`, so `com.Foo$Bar`
  is a child of `com.Foo` and inherits its level. In Log4j2 and Rainbow Gum the parent of
  `com.Foo$Bar` is `com`: with `com.Foo` set to DEBUG and the root at WARN, Logback logs
  `com.Foo$Bar` at DEBUG and the other two at WARN.
- **A `null` name.** Logback throws `IllegalArgumentException: name argument cannot be
  null`. Log4j2's `LogManager.getLogger((String) null)` returns a logger named after the
  calling class. Rainbow Gum's SLF4J factory throws `NullPointerException`; the JDK's own
  `System.getLogger` and `java.util.logging.Logger.getLogger` reject `null` before any
  framework sees it.
- **Empty segments and trailing dots.** All three keep the name exactly and walk up to the
  parents: `a.` inherits from `a`, `a..b` from `a.`, and the Testcontainers name from its
  name minus one trailing dot. Logback creates every intermediate logger (five for the
  Testcontainers name). The abbreviators disagree on what the last segment of `a.` is:
  Logback's `%logger{0}` gives an empty string, Log4j2's `%c{1}` gives `a.` and `%c{-1}`
  gives an empty string.
- **Line breaks and escape sequences in text output.** None of the three escape a logger
  name in their plain text layouts, so a name holding a line feed starts a new line and one
  holding `ESC [31m` changes a terminal's colour. That matches how they treat messages.
  Each has an opt in: Logback's `%replace`, Log4j2's `%enc{%c}{CRLF}`, and Rainbow Gum's
  `crlf` post processor (line breaks only). All of Rainbow Gum's JSON and logfmt encoders
  escape every control character in the name.

## Full results

### Hierarchy and levels

| Name | Logback logger | Logback parents created | Logback: parent set to DEBUG | Log4j2 logger | Log4j2: parent set to DEBUG | Rainbow Gum: parent set to debug | Rainbow Gum: exact key set to debug |
|---|---|---|---|---|---|---|---|
| (empty) | (empty) | (empty) | - | (empty) | - | - | DEBUG (root key, other loggers DEBUG) |
| `.` | `.` | (empty), `.` | - | `.` | - | - | DEBUG |
| `..` | `..` | (empty), `.`, `..` | DEBUG | `..` | DEBUG via `.` | DEBUG | DEBUG |
| `a.` | `a.` | `a`, `a.` | DEBUG | `a.` | DEBUG via `a` | DEBUG | DEBUG |
| `.a` | `.a` | (empty), `.a` | - | `.a` | - | - | DEBUG |
| `a..b` | `a..b` | `a`, `a.`, `a..b` | DEBUG | `a..b` | DEBUG via `a.` | DEBUG | DEBUG |
| `a.b.` | `a.b.` | `a`, `a.b`, `a.b.` | DEBUG | `a.b.` | DEBUG via `a.b` | DEBUG | DEBUG |
| ` ` | ` ` | ` ` | - | ` ` | - | - | DEBUG |
| ` a` | ` a` | ` a` | - | ` a` | - | - | DEBUG |
| `a ` | `a ` | `a ` | - | `a ` | - | - | DEBUG |
| `a b` | `a b` | `a b` | - | `a b` | - | - | DEBUG |
| `a⟨LF⟩b` | `a⟨LF⟩b` | `a⟨LF⟩b` | - | `a⟨LF⟩b` | - | - | DEBUG |
| `a⟨CR⟩⟨LF⟩b` | `a⟨CR⟩⟨LF⟩b` | `a⟨CR⟩⟨LF⟩b` | - | `a⟨CR⟩⟨LF⟩b` | - | - | DEBUG |
| `a⟨TAB⟩b` | `a⟨TAB⟩b` | `a⟨TAB⟩b` | - | `a⟨TAB⟩b` | - | - | DEBUG |
| `root` | `ROOT` (the root logger) | none | - | `root` | - | - | WARNING |
| `ROOT` | `ROOT` (the root logger) | none | - | `ROOT` | - | - | DEBUG |
| `Root` | `ROOT` (the root logger) | none | - | `Root` | - | - | DEBUG |
| `root.child` | `root.child` | `root`, `root.child` | DEBUG | `root.child` | DEBUG via `root` | WARNING | DEBUG |
| `com.Foo$Bar` | `com.Foo$Bar` | `com`, `com.Foo`, `com.Foo$Bar` | DEBUG | `com.Foo$Bar` | DEBUG via `com` | DEBUG | DEBUG |
| `com.Foo$Bar.baz` | `com.Foo$Bar.baz` | `com`, `com.Foo`, `com.Foo$Bar`, `com.Foo$Bar.baz` | DEBUG | `com.Foo$Bar.baz` | DEBUG via `com.Foo$Bar` | DEBUG | DEBUG |
| `$` | `$` | (empty), `$` | - | `$` | - | - | DEBUG |
| `a.$.b` | `a.$.b` | `a`, `a.`, `a.$`, `a.$.b` | DEBUG | `a.$.b` | DEBUG via `a.$` | DEBUG | DEBUG |
| `café.Ünïcode` | `café.Ünïcode` | `café`, `café.Ünïcode` | DEBUG | `café.Ünïcode` | DEBUG via `café` | DEBUG | DEBUG |
| `a.😀.b` | `a.😀.b` | `a`, `a.😀`, `a.😀.b` | DEBUG | `a.😀.b` | DEBUG via `a.😀` | DEBUG | DEBUG |
| `a⟨NUL⟩b` | `a⟨NUL⟩b` | `a⟨NUL⟩b` | - | `a⟨NUL⟩b` | - | - | DEBUG |
| `a⟨ESC⟩[31mred` | `a⟨ESC⟩[31mred` | `a⟨ESC⟩[31mred` | - | `a⟨ESC⟩[31mred` | - | - | DEBUG |
| `a"b` | `a"b` | `a"b` | - | `a"b` | - | - | DEBUG |
| `a\b` | `a\b` | `a\b` | - | `a\b` | - | - | DEBUG |
| `a=b` | `a=b` | `a=b` | - | `a=b` | - | - | DEBUG |
| `a:b` | `a:b` | `a:b` | - | `a:b` | - | - | DEBUG |
| `a#b` | `a#b` | `a#b` | - | `a#b` | - | - | DEBUG |
| `a{name}b` | `a{name}b` | `a{name}b` | - | `a{name}b` | - | - | DEBUG |
| `tc.floci/floci-gcp@sha256:405c128b...` | `tc.floci/floci-gcp@sha256:405c128b...` | `tc`, `tc.floci/floci-gcp@sha256:405c128b`, `tc.floci/floci-gcp@sha256:405c128b.`, `tc.floci/floci-gcp@sha256:405c128b..`, `tc.floci/floci-gcp@sha256:405c128b...` | DEBUG | `tc.floci/floci-gcp@sha256:405c128b...` | DEBUG via `tc.floci/floci-gcp@sha256:405c128b..` | DEBUG | DEBUG |
| `com.example.service.impl.VeryLongClassNameForAbbreviation` | `com.example.service.impl.VeryLongClassNameForAbbreviation` | `com`, `com.example`, `com.example.service`, `com.example.service.impl`, `com.example.service.impl.VeryLongClassNameForAbbreviation` | DEBUG | `com.example.service.impl.VeryLongClassNameForAbbreviation` | DEBUG via `com.example.service.impl` | DEBUG | DEBUG |
| `a.b.c.d.e.f.g.h.i.j.k.l.m.n.o.p` | `a.b.c.d.e.f.g.h.i.j.k.l.m.n.o.p` | `a`, `a.b`, `a.b.c`, `a.b.c.d`, `a.b.c.d.e`, `a.b.c.d.e.f`, `a.b.c.d.e.f.g`, `a.b.c.d.e.f.g.h`, `a.b.c.d.e.f.g.h.i`, `a.b.c.d.e.f.g.h.i.j`, `a.b.c.d.e.f.g.h.i.j.k`, `a.b.c.d.e.f.g.h.i.j.k.l`, `a.b.c.d.e.f.g.h.i.j.k.l.m`, `a.b.c.d.e.f.g.h.i.j.k.l.m.n`, `a.b.c.d.e.f.g.h.i.j.k.l.m.n.o`, `a.b.c.d.e.f.g.h.i.j.k.l.m.n.o.p` | DEBUG | `a.b.c.d.e.f.g.h.i.j.k.l.m.n.o.p` | DEBUG via `a.b.c.d.e.f.g.h.i.j.k.l.m.n.o` | DEBUG | DEBUG |
| `...` | `...` | (empty), `.`, `..`, `...` | DEBUG | `...` | DEBUG via `..` | DEBUG | DEBUG |
| `?` | `?` | `?` | - | `?` | - | - | DEBUG |
| `???` | `???` | `???` | - | `???` | - | - | DEBUG |
| `a.?.b` | `a.?.b` | `a`, `a.?`, `a.?.b` | DEBUG | `a.?.b` | DEBUG via `a.?` | DEBUG | DEBUG |
| `😀😀😀` | `😀😀😀` | `😀😀😀` | - | `😀😀😀` | - | - | DEBUG |
| `😀.😀.😀` | `😀.😀.😀` | `😀`, `😀.😀`, `😀.😀.😀` | DEBUG | `😀.😀.😀` | DEBUG via `😀.😀` | DEBUG | DEBUG |
| `😀.b` | `😀.b` | `😀`, `😀.b` | DEBUG | `😀.b` | DEBUG via `😀` | DEBUG | DEBUG |

`root.child` with `logging.level.root=debug` stays at WARNING in Rainbow Gum because
`logging.level=warn` was also set: the root key wins over its `root` alias from the same
source.

### Logback abbreviation, and Rainbow Gum with the same pattern

Rainbow Gum's pattern encoder gives the same output as Logback except for the root names,
which Logback renames to `ROOT`, and the emoji. Logback's `a.?.b` is its lone surrogate
printed through a UTF-8 console.

| Name | Logback `%logger` | `{0}` | `{1}` | `{5}` | `{10}` | `{20}` | Rainbow Gum same pattern |
|---|---|---|---|---|---|---|---|
| (empty) | (empty) | (empty) | (empty) | (empty) | (empty) | (empty) | same |
| `.` | `.` | (empty) | `.` | `.` | `.` | `.` | same |
| `..` | `..` | (empty) | `..` | `..` | `..` | `..` | same |
| `a.` | `a.` | (empty) | `a.` | `a.` | `a.` | `a.` | same |
| `.a` | `.a` | `a` | `.a` | `.a` | `.a` | `.a` | same |
| `a..b` | `a..b` | `b` | `a..b` | `a..b` | `a..b` | `a..b` | same |
| `a.b.` | `a.b.` | (empty) | `a.b.` | `a.b.` | `a.b.` | `a.b.` | same |
| ` ` | ` ` | ` ` | ` ` | ` ` | ` ` | ` ` | same |
| ` a` | ` a` | ` a` | ` a` | ` a` | ` a` | ` a` | same |
| `a ` | `a ` | `a ` | `a ` | `a ` | `a ` | `a ` | same |
| `a b` | `a b` | `a b` | `a b` | `a b` | `a b` | `a b` | same |
| `a⟨LF⟩b` | `a⟨LF⟩b` | `a⟨LF⟩b` | `a⟨LF⟩b` | `a⟨LF⟩b` | `a⟨LF⟩b` | `a⟨LF⟩b` | same |
| `a⟨CR⟩⟨LF⟩b` | `a⟨CR⟩⟨LF⟩b` | `a⟨CR⟩⟨LF⟩b` | `a⟨CR⟩⟨LF⟩b` | `a⟨CR⟩⟨LF⟩b` | `a⟨CR⟩⟨LF⟩b` | `a⟨CR⟩⟨LF⟩b` | same |
| `a⟨TAB⟩b` | `a⟨TAB⟩b` | `a⟨TAB⟩b` | `a⟨TAB⟩b` | `a⟨TAB⟩b` | `a⟨TAB⟩b` | `a⟨TAB⟩b` | same |
| `root` | `ROOT` | `ROOT` | `ROOT` | `ROOT` | `ROOT` | `ROOT` | `root` / `root` / `root` / `root` / `root` / `root` |
| `ROOT` | `ROOT` | `ROOT` | `ROOT` | `ROOT` | `ROOT` | `ROOT` | same |
| `Root` | `ROOT` | `ROOT` | `ROOT` | `ROOT` | `ROOT` | `ROOT` | `Root` / `Root` / `Root` / `Root` / `Root` / `Root` |
| `root.child` | `root.child` | `child` | `r.child` | `r.child` | `r.child` | `root.child` | same |
| `com.Foo$Bar` | `com.Foo$Bar` | `Foo$Bar` | `c.Foo$Bar` | `c.Foo$Bar` | `c.Foo$Bar` | `com.Foo$Bar` | same |
| `com.Foo$Bar.baz` | `com.Foo$Bar.baz` | `baz` | `c.F.baz` | `c.F.baz` | `c.F.baz` | `com.Foo$Bar.baz` | same |
| `$` | `$` | `$` | `$` | `$` | `$` | `$` | same |
| `a.$.b` | `a.$.b` | `b` | `a.$.b` | `a.$.b` | `a.$.b` | `a.$.b` | same |
| `café.Ünïcode` | `café.Ünïcode` | `Ünïcode` | `c.Ünïcode` | `c.Ünïcode` | `c.Ünïcode` | `café.Ünïcode` | same |
| `a.😀.b` | `a.😀.b` | `b` | `a.?.b` | `a.?.b` | `a.😀.b` | `a.😀.b` | `a.😀.b` / `b` / `a.😀.b` / `a.😀.b` / `a.😀.b` / `a.😀.b` |
| `a⟨NUL⟩b` | `a⟨NUL⟩b` | `a⟨NUL⟩b` | `a⟨NUL⟩b` | `a⟨NUL⟩b` | `a⟨NUL⟩b` | `a⟨NUL⟩b` | same |
| `a⟨ESC⟩[31mred` | `a⟨ESC⟩[31mred` | `a⟨ESC⟩[31mred` | `a⟨ESC⟩[31mred` | `a⟨ESC⟩[31mred` | `a⟨ESC⟩[31mred` | `a⟨ESC⟩[31mred` | same |
| `a"b` | `a"b` | `a"b` | `a"b` | `a"b` | `a"b` | `a"b` | same |
| `a\b` | `a\b` | `a\b` | `a\b` | `a\b` | `a\b` | `a\b` | same |
| `a=b` | `a=b` | `a=b` | `a=b` | `a=b` | `a=b` | `a=b` | same |
| `a:b` | `a:b` | `a:b` | `a:b` | `a:b` | `a:b` | `a:b` | same |
| `a#b` | `a#b` | `a#b` | `a#b` | `a#b` | `a#b` | `a#b` | same |
| `a{name}b` | `a{name}b` | `a{name}b` | `a{name}b` | `a{name}b` | `a{name}b` | `a{name}b` | same |
| `tc.floci/floci-gcp@sha256:405c128b...` | `tc.floci/floci-gcp@sha256:405c128b...` | (empty) | `t.f...` | `t.f...` | `t.f...` | `t.f...` | same |
| `com.example.service.impl.VeryLongClassNameForAbbreviation` | `com.example.service.impl.VeryLongClassNameForAbbreviation` | `VeryLongClassNameForAbbreviation` | `c.e.s.i.VeryLongClassNameForAbbreviation` | `c.e.s.i.VeryLongClassNameForAbbreviation` | `c.e.s.i.VeryLongClassNameForAbbreviation` | `c.e.s.i.VeryLongClassNameForAbbreviation` | same |
| `a.b.c.d.e.f.g.h.i.j.k.l.m.n.o.p` | `a.b.c.d.e.f.g.h.i.j.k.l.m.n.o.p` | `p` | `a.b.c.d.e.f.g.h.i.j.k.l.m.n.o.p` | `a.b.c.d.e.f.g.h.i.j.k.l.m.n.o.p` | `a.b.c.d.e.f.g.h.i.j.k.l.m.n.o.p` | `a.b.c.d.e.f.g.h.i.j.k.l.m.n.o.p` | same |
| `...` | `...` | (empty) | `...` | `...` | `...` | `...` | same |
| `?` | `?` | `?` | `?` | `?` | `?` | `?` | same |
| `???` | `???` | `???` | `???` | `???` | `???` | `???` | same |
| `a.?.b` | `a.?.b` | `b` | `a.?.b` | `a.?.b` | `a.?.b` | `a.?.b` | same |
| `😀😀😀` | `😀😀😀` | `😀😀😀` | `😀😀😀` | `😀😀😀` | `😀😀😀` | `😀😀😀` | same |
| `😀.😀.😀` | `😀.😀.😀` | `😀` | `?.?.😀` | `?.?.😀` | `😀.😀.😀` | `😀.😀.😀` | `😀.😀.😀` / `😀` / `😀.😀.😀` / `😀.😀.😀` / `😀.😀.😀` / `😀.😀.😀` |
| `😀.b` | `😀.b` | `b` | `?.b` | `😀.b` | `😀.b` | `😀.b` | `😀.b` / `b` / `😀.b` / `😀.b` / `😀.b` / `😀.b` |

### Log4j2 abbreviation

| Name | Log4j2 `%c` | `%c{1}` | `%c{2}` | `%c{1.}` | `%c{-1}` | `%c{1.1.~}` |
|---|---|---|---|---|---|---|
| (empty) | (empty) | (empty) | (empty) | (empty) | (empty) | (empty) |
| `.` | `.` | `.` | `.` | `.` | (empty) | `.` |
| `..` | `..` | `.` | `..` | `..` | `.` | `..` |
| `a.` | `a.` | `a.` | `a.` | `a.` | (empty) | `a.` |
| `.a` | `.a` | `a` | `.a` | `.a` | `a` | `.a` |
| `a..b` | `a..b` | `b` | `.b` | `a..b` | `.b` | `a..b` |
| `a.b.` | `a.b.` | `b.` | `a.b.` | `a.b.` | `b.` | `a.b.` |
| ` ` | ` ` | ` ` | ` ` | ` ` | ` ` | ` ` |
| ` a` | ` a` | ` a` | ` a` | ` a` | ` a` | ` a` |
| `a ` | `a ` | `a ` | `a ` | `a ` | `a ` | `a ` |
| `a b` | `a b` | `a b` | `a b` | `a b` | `a b` | `a b` |
| `a⟨LF⟩b` | `a⟨LF⟩b` | `a⟨LF⟩b` | `a⟨LF⟩b` | `a⟨LF⟩b` | `a⟨LF⟩b` | `a⟨LF⟩b` |
| `a⟨CR⟩⟨LF⟩b` | `a⟨CR⟩⟨LF⟩b` | `a⟨CR⟩⟨LF⟩b` | `a⟨CR⟩⟨LF⟩b` | `a⟨CR⟩⟨LF⟩b` | `a⟨CR⟩⟨LF⟩b` | `a⟨CR⟩⟨LF⟩b` |
| `a⟨TAB⟩b` | `a⟨TAB⟩b` | `a⟨TAB⟩b` | `a⟨TAB⟩b` | `a⟨TAB⟩b` | `a⟨TAB⟩b` | `a⟨TAB⟩b` |
| `root` | `root` | `root` | `root` | `root` | `root` | `root` |
| `ROOT` | `ROOT` | `ROOT` | `ROOT` | `ROOT` | `ROOT` | `ROOT` |
| `Root` | `Root` | `Root` | `Root` | `Root` | `Root` | `Root` |
| `root.child` | `root.child` | `child` | `root.child` | `r.child` | `child` | `r.child` |
| `com.Foo$Bar` | `com.Foo$Bar` | `Foo$Bar` | `com.Foo$Bar` | `c.Foo$Bar` | `Foo$Bar` | `c.Foo$Bar` |
| `com.Foo$Bar.baz` | `com.Foo$Bar.baz` | `baz` | `Foo$Bar.baz` | `c.F.baz` | `Foo$Bar.baz` | `c.F.baz` |
| `$` | `$` | `$` | `$` | `$` | `$` | `$` |
| `a.$.b` | `a.$.b` | `b` | `$.b` | `a.$.b` | `$.b` | `a.$.b` |
| `café.Ünïcode` | `café.Ünïcode` | `Ünïcode` | `café.Ünïcode` | `c.Ünïcode` | `Ünïcode` | `c.Ünïcode` |
| `a.😀.b` | `a.😀.b` | `b` | `😀.b` | `a.?.b` | `😀.b` | `a.?.b` |
| `a⟨NUL⟩b` | `a⟨NUL⟩b` | `a⟨NUL⟩b` | `a⟨NUL⟩b` | `a⟨NUL⟩b` | `a⟨NUL⟩b` | `a⟨NUL⟩b` |
| `a⟨ESC⟩[31mred` | `a⟨ESC⟩[31mred` | `a⟨ESC⟩[31mred` | `a⟨ESC⟩[31mred` | `a⟨ESC⟩[31mred` | `a⟨ESC⟩[31mred` | `a⟨ESC⟩[31mred` |
| `a"b` | `a"b` | `a"b` | `a"b` | `a"b` | `a"b` | `a"b` |
| `a\b` | `a\b` | `a\b` | `a\b` | `a\b` | `a\b` | `a\b` |
| `a=b` | `a=b` | `a=b` | `a=b` | `a=b` | `a=b` | `a=b` |
| `a:b` | `a:b` | `a:b` | `a:b` | `a:b` | `a:b` | `a:b` |
| `a#b` | `a#b` | `a#b` | `a#b` | `a#b` | `a#b` | `a#b` |
| `a{name}b` | `a{name}b` | `a{name}b` | `a{name}b` | `a{name}b` | `a{name}b` | `a{name}b` |
| `tc.floci/floci-gcp@sha256:405c128b...` | `tc.floci/floci-gcp@sha256:405c128b...` | `.` | `..` | `t.f...` | `floci/floci-gcp@sha256:405c128b...` | `t.f...` |
| `com.example.service.impl.VeryLongClassNameForAbbreviation` | `com.example.service.impl.VeryLongClassNameForAbbreviation` | `VeryLongClassNameForAbbreviation` | `impl.VeryLongClassNameForAbbreviation` | `c.e.s.i.VeryLongClassNameForAbbreviation` | `example.service.impl.VeryLongClassNameForAbbreviation` | `c.e.~.~.VeryLongClassNameForAbbreviation` |
| `a.b.c.d.e.f.g.h.i.j.k.l.m.n.o.p` | `a.b.c.d.e.f.g.h.i.j.k.l.m.n.o.p` | `p` | `o.p` | `a.b.c.d.e.f.g.h.i.j.k.l.m.n.o.p` | `b.c.d.e.f.g.h.i.j.k.l.m.n.o.p` | `a.b.~.~.~.~.~.~.~.~.~.~.~.~.~.p` |
| `...` | `...` | `.` | `..` | `...` | `..` | `...` |
| `?` | `?` | `?` | `?` | `?` | `?` | `?` |
| `???` | `???` | `???` | `???` | `???` | `???` | `???` |
| `a.?.b` | `a.?.b` | `b` | `?.b` | `a.?.b` | `?.b` | `a.?.b` |
| `😀😀😀` | `😀😀😀` | `😀😀😀` | `😀😀😀` | `😀😀😀` | `😀😀😀` | `😀😀😀` |
| `😀.😀.😀` | `😀.😀.😀` | `😀` | `😀.😀` | `?.?.😀` | `😀.😀` | `?.?.😀` |
| `😀.b` | `😀.b` | `b` | `😀.b` | `?.b` | `b` | `?.b` |

### Rainbow Gum encoders

| Name | TTLL | logfmt `logger` | JSON5 `logger` | ECS `log.logger` | Logstash `logger_name` | GELF `_logger` |
|---|---|---|---|---|---|---|
| `a⟨LF⟩b` | `ERROR a⟨LF⟩b - m` | `"a\nb"` | `"a\nb"` | `"a\nb"` | `"a\nb"` | `"a\nb"` |
| `a⟨CR⟩⟨LF⟩b` | `ERROR a⟨CR⟩⟨LF⟩b - m` | `"a\r\nb"` | `"a\r\nb"` | `"a\r\nb"` | `"a\r\nb"` | `"a\r\nb"` |
| `a⟨TAB⟩b` | `ERROR a⟨TAB⟩b - m` | `"a\tb"` | `"a\tb"` | `"a\tb"` | `"a\tb"` | `"a\tb"` |
| `a⟨NUL⟩b` | `ERROR a⟨NUL⟩b - m` | `"a\u0000b"` | `"a\u0000b"` | `"a\u0000b"` | `"a\u0000b"` | `"a\u0000b"` |
| `a⟨ESC⟩[31mred` | `ERROR a⟨ESC⟩[31mred - m` | `"a\u001b[31mred"` | `"a\u001b[31mred"` | `"a\u001b[31mred"` | `"a\u001b[31mred"` | `"a\u001b[31mred"` |
| `a"b` | `ERROR a"b - m` | `"a\"b"` | `"a\"b"` | `"a\"b"` | `"a\"b"` | `"a\"b"` |
| `a\b` | `ERROR a\b - m` | `"a\\b"` | `"a\\b"` | `"a\\b"` | `"a\\b"` | `"a\\b"` |
| ` ` | `ERROR   - m` | `" "` | `" "` | `" "` | `" "` | `" "` |
| `a b` | `ERROR a b - m` | `"a b"` | `"a b"` | `"a b"` | `"a b"` | `"a b"` |
| `a=b` | `ERROR a=b - m` | `"a=b"` | `"a=b"` | `"a=b"` | `"a=b"` | `"a=b"` |
| `a.😀.b` | `ERROR a.😀.b - m` | `a.😀.b` | `"a.😀.b"` | `"a.😀.b"` | `"a.😀.b"` | `"a.😀.b"` |

Every encoder writes control characters with lowercase hex (`\u001b`), as ECMAScript's
`JSON.stringify` and RFC 8785 do; RFC 8259 allows either case. logfmt quotes a value only
when it needs to.
