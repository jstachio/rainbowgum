# Switch to Rainbow Gum

Your application probably logs on every request. A line when the request starts, a
line when it ends, maybe a few in between. That logging runs on the same CPUs that
serve your users, millions of times a day, and the library doing it was chosen once,
years ago, usually by a framework default nobody revisited.

Rainbow Gum is a logging implementation for JDK 21+ built to make that cost as small
as possible, to never quietly lose your logs, and to be easy to switch to and easy to
switch away from. If you use SLF4J, the Log4j2 API, System.Logger, or JUL, your logging
code does not change.

This page makes the case for switching. The detailed, feature by feature comparison is
in [why_rainbowgum_is_better.md](why_rainbowgum_is_better.md), and every number below
links to how it was measured.

## Logging is a cost you pay on every request

Logging looks free because each call is small. It is not free at scale: formatting,
encoding, locking, and writing happen for every event, on the thread that is supposed
to be answering a request.

In our [Spring Boot benchmark](why_rainbowgum_is_better.md#spring-boot), the same
application on the same machine, logging structured GELF to the console as a container
application would:

| | Rainbow Gum | Logback | Log4j2 |
| --- | ---: | ---: | ---: |
| Requests per second | **34,172** | 15,002 | 19,639 |

That is about 2.3 times Logback's throughput, from changing only the logging library.
Under [GraalVM Native Image](why_rainbowgum_is_better.md#graalvm-native-image) with
GELF it is 92,264 requests per second against Logback's 56,772.

Exporting logs to an OpenTelemetry Collector, Rainbow Gum used
[32% less CPU than Logback and 37% less than Log4j2](https://github.com/jstachio/rainbowgum/blob/benchmark/otlp/benchmark/otlp/RESULTS.md)
to deliver the same 200,000 records, and needed less than a third of the dependency
size ([0.83 MiB against 3.01 and 4.31 MiB](doc/why/opentelemetry_export_size.md)).

These benchmarks are designed so that logging is a large part of each request, to
make its cost visible. In your application the share depends on how much other work a
request does, so your gain may well be smaller. But the cost is per request, so the
saving grows with your traffic, and CPU you do not spend on logging is CPU, money, and
energy you can spend on something else or not spend at all.

## Your logs should actually arrive

With their default settings, the OpenTelemetry Logback and Log4j2 appenders
[delivered only 7% and 10% of a 200,000 event burst](https://github.com/jstachio/rainbowgum/blob/benchmark/otlp/benchmark/otlp/RESULTS.md#defaults).
The OpenTelemetry SDK they hand events to holds at most 2,048 records by default and
drops the rest, and nothing is logged when it does. Their logging calls look very fast
in that benchmark because most events go nowhere.

Rainbow Gum delivered all 200,000. Its asynchronous publisher is bounded too, but by
default it makes logging threads wait when it is full instead of discarding. If you
choose to drop events under load, every dropped event is counted in Rainbow Gum's
built in metrics and raises an alert, so dropping is never silent.

## Switching takes minutes, and so does switching back

Logging facades exist so that the implementation can be swapped without touching
application code. Rainbow Gum is a native implementation of the common ones (SLF4J,
the Log4j2 API, `System.Logger`, JUL, JBoss Logging), so switching is a dependency
change.

**Spring Boot.** Replace `spring-boot-starter-logging` with the Rainbow Gum starter:

```xml
<dependency>
  <groupId>io.jstach.rainbowgum</groupId>
  <artifactId>rainbowgum-spring-boot4-starter</artifactId>
  <version>${rainbowgum.version}</version>
</dependency>
<dependency>
  <groupId>org.springframework.boot</groupId>
  <artifactId>spring-boot-starter-web</artifactId>
  <exclusions>
    <exclusion>
      <groupId>org.springframework.boot</groupId>
      <artifactId>spring-boot-starter-logging</artifactId>
    </exclusion>
  </exclusions>
</dependency>
```

Rainbow Gum reads Spring Boot's own logging properties (`logging.level.*`, log groups,
`logging.file.*`, `logging.pattern.*`, and structured logging with
`logging.structured.format.*`), so most `application.properties` and
`application.yml` logging configuration keeps working. Spring Boot 3 uses
`rainbowgum-spring-boot3-starter` instead.

**Anything using SLF4J.** Remove Logback or Log4j2's SLF4J binding and add:

```xml
<dependency>
  <groupId>io.jstach.rainbowgum</groupId>
  <artifactId>rainbowgum</artifactId>
  <version>${rainbowgum.version}</version>
  <scope>runtime</scope>
</dependency>
```

**Code written against the Log4j2 API** keeps it: add `rainbowgum-log4j2`, a native
Log4j2 API implementation rather than a bridge. **Micronaut and Helidon** have
dedicated integrations; see the [user guide](https://jstach.io/rainbowgum/#simple_other_frameworks).

**Switching back** is the same change in reverse. Your logging calls never depended
on Rainbow Gum, so nothing is locked in. That makes trying it on one service, in one
environment, a low risk experiment: deploy it, compare your own latency and CPU, and
decide.

## Safer by default

A logging library sees every request and much of your data, so what it can do matters.

* **No lookups or expression language.** Log messages are data, never evaluated.
  Message lookups are what made [Log4Shell](https://en.wikipedia.org/wiki/Log4Shell)
  possible.
* **No XML parser, and core needs only `java.base`.** File output and other
  capabilities are separate modules, so configuration cannot turn on something that is
  not installed.
* **Mistakes fail at startup with a clear message.** A bad level or an unknown encoder
  stops startup and says what is wrong and what was probably meant, instead of
  silently falling back to defaults. With `logging.alerts.fail=warning`, a misspelled
  property key does too. See
  [what each framework reports](doc/why/error_messages_comparison.md).

## When not to switch yet

We would rather you switch for the right reasons than switch and regret it. Rainbow
Gum is not the right choice today if:

* **You are on a JDK older than 21.** Rainbow Gum requires 21 or newer.
* **You rely on in process time based log rolling,** such as a new file every day.
  Rainbow Gum rolls files by size, and supports the classic external rotation where
  `logrotate` moves the file and the application reopens it. Daily rolling inside the
  JVM is not supported.
* **You depend on Logback or Log4j2 specific extensions,** such as custom appenders,
  filters, or features configured only through `logback.xml` or `log4j2.xml`. Rainbow
  Gum covers the common outputs and formats (console, files, JSON, GELF, Logstash, ECS,
  OTLP, JFR), but it does not run other frameworks' plugins.
* **Your organization requires long established projects with a large maintainer
  base.** Logback and Log4j2 have decades of production use and scrutiny; Rainbow Gum is
  younger and not yet 1.0. Because your code only talks to a facade, the cost of
  leaving later is small, but that is a real tradeoff to weigh.

If none of those apply, there is very little reason not to try it.

## Try it

1. Make the dependency change above on one service.
2. Run your usual load test or watch production metrics: request latency, CPU, and
   memory.
3. If you export to OpenTelemetry, compare what the collector receives with what you
   logged.

If something does not work or a feature you need is missing,
[open an issue](https://github.com/jstachio/rainbowgum/issues). Reports from real
switches are the most useful thing you can give the project.
