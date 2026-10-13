# OpenTelemetry Log Export Size: Rainbow Gum vs Logback vs Log4j2

A companion to [why_rainbowgum_is_better.md](../../why_rainbowgum_is_better.md), like
[error_messages_comparison.md](error_messages_comparison.md).

The question: how many JARs, and how many bytes, does an application ship to send its
logs to an OpenTelemetry Collector over OTLP? Each setup below was resolved from Maven
Central with `mvn dependency:copy-dependencies -DincludeScope=runtime`. The sizes are
the sum of the resolved JARs, measured on 2026-10-13, not estimated from documentation.

Versions: Rainbow Gum 0.15.0, Logback 1.6.5, Log4j2 2.26.1, SLF4J 2.0.20, OpenTelemetry
Java 1.67.0 (imported through `opentelemetry-bom`), and OpenTelemetry Java
Instrumentation 2.32.0 (`2.32.0-alpha` for the appenders).

**The headline finding: Rainbow Gum exports OTLP in 0.83 MiB, about a quarter of the
smallest Logback setup and a fifth of the smallest Log4j2 setup.** With the
OpenTelemetry exporter's default HTTP sender, Logback and Log4j2 need 7 to 9 times as
much.

## Summary

| Setup | JARs | Total |
| --- | ---: | ---: |
| **Rainbow Gum** (`rainbowgum` + `rainbowgum-otlp`) | 10 | **850 KiB (0.83 MiB)** |
| **Rainbow Gum**, plus `rainbowgum-opentelemetry` for span ids | 14 | 1081 KiB (1.06 MiB) |
| Logback + OpenTelemetry appender, JDK HTTP sender | 20 | 3085 KiB (3.01 MiB) |
| Logback + OpenTelemetry appender, default OkHttp sender | 24 | 6127 KiB (5.98 MiB) |
| Log4j2 + OpenTelemetry appender, JDK HTTP sender | 20 | 4413 KiB (4.31 MiB) |
| Log4j2 + OpenTelemetry appender, default OkHttp sender | 24 | 7456 KiB (7.28 MiB) |

Every setup includes the facade API an application logs through: `slf4j-api` for
Rainbow Gum and Logback, `log4j-api` for Log4j2.

## Why the difference

The OpenTelemetry
[Logback](https://github.com/open-telemetry/opentelemetry-java-instrumentation/tree/main/instrumentation/logback/logback-appender-1.0/library)
and
[Log4j2](https://github.com/open-telemetry/opentelemetry-java-instrumentation/tree/main/instrumentation/log4j/log4j-appender-2.17/library)
appenders do not send anything themselves. They hand each event to the OpenTelemetry
SDK, which needs an exporter to reach a collector. So the appender brings the SDK, the
SDK brings its metrics and trace modules even when only logs are exported, and the
exporter brings an HTTP client.

`rainbowgum-otlp` writes the OTLP protobuf or JSON itself and posts it with the JDK's
`java.net.http` client. It depends only on Rainbow Gum core and `rainbowgum-json`.

Protobuf is not the difference. Both sides send OTLP protobuf by default and neither
uses the protobuf library: no resolved JAR is `protobuf-java` or `grpc-java`, and no
class in any of them references `com.google.protobuf`. The OpenTelemetry exporter
writes the wire format with its own marshalers (108 marshaler classes in
`opentelemetry-exporter-otlp-common`, plus `ProtoSerializer` and `JsonSerializer` in
`opentelemetry-exporter-common`), the same approach `rainbowgum-otlp` takes. The
OpenTelemetry exporter also offers OTLP over gRPC, which Rainbow Gum does not; that
transport is in the measured JARs either way.

### The OpenTelemetry stack alone is about 2 MiB

With the JDK sender, the OpenTelemetry JARs in the Logback setup add up to 2095 KiB
(2102 KiB for Log4j2, which adds a context data JAR):

| JAR | Size |
| --- | ---: |
| `opentelemetry-sdk-metrics` | 364 KiB |
| `opentelemetry-instrumentation-api-incubator` | 316 KiB |
| `opentelemetry-instrumentation-api` | 273 KiB |
| `opentelemetry-exporter-otlp-common` | 214 KiB |
| `opentelemetry-api` | 175 KiB |
| `opentelemetry-sdk-trace` | 143 KiB |
| `opentelemetry-exporter-otlp` | 112 KiB |
| `opentelemetry-exporter-common` | 111 KiB |
| `opentelemetry-sdk-common` | 99 KiB |
| `opentelemetry-api-incubator` | 81 KiB |
| `opentelemetry-sdk-logs` | 68 KiB |
| `opentelemetry-context` | 50 KiB |
| `opentelemetry-logback-appender-1.0` | 28 KiB |
| `opentelemetry-semconv` | 23 KiB |
| `opentelemetry-exporter-sender-jdk` | 21 KiB |
| `opentelemetry-sdk` | 13 KiB |
| `opentelemetry-common` | 6 KiB |

That is more than twice the size of the entire Rainbow Gum setup.

### The default sender brings Kotlin

`opentelemetry-exporter-otlp` uses OkHttp unless told otherwise. OkHttp 5 is written in
Kotlin, so the default adds:

| JAR | Size |
| --- | ---: |
| `kotlin-stdlib` | 1684 KiB |
| `okhttp-jvm` | 939 KiB |
| `okio-jvm` | 383 KiB |
| `opentelemetry-exporter-sender-okhttp` | 40 KiB |
| `annotations` (JetBrains) | 17 KiB |

Getting the smaller setup takes two changes: exclude
`opentelemetry-exporter-sender-okhttp` from `opentelemetry-exporter-otlp` and add
`opentelemetry-exporter-sender-jdk`.

### Rainbow Gum's JARs

| JAR | Size |
| --- | ---: |
| `rainbowgum-core` | 555 KiB |
| `slf4j-api` | 69 KiB |
| `rainbowgum-slf4j` | 69 KiB |
| `rainbowgum-json` | 63 KiB |
| `rainbowgum-otlp` | 51 KiB |
| `rainbowgum-systemlogger` | 17 KiB |
| `rainbowgum-jul` | 10 KiB |
| `rainbowgum-annotation` | 8 KiB |
| `rainbowgum-jdk` | 5 KiB |
| `rainbowgum` | 3 KiB |

The Rainbow Gum setup also covers more than the Logback one: the `rainbowgum` bundle
includes the JUL and `System.Logger` integrations, which a Logback application needs
`jul-to-slf4j` for.

`rainbowgum-opentelemetry` is optional. It reads the current span from the
OpenTelemetry context and adds 231 KiB, almost all of it `opentelemetry-api` and
`opentelemetry-context`, which an application with tracing already has. Without it,
trace and span ids can still come from the MDC, for example from Micrometer Tracing.

## What this does not measure

* **Features.** The OpenTelemetry appenders can capture markers, message templates, and
  arguments, which Rainbow Gum's OTLP output does not yet. The OpenTelemetry SDK also
  supports gRPC, batching policies, and exporting traces and metrics through the same
  pipeline. An application already using the SDK for traces pays most of its cost
  anyway.
* **The Java agent.** With the
  [OpenTelemetry Java agent](https://github.com/open-telemetry/opentelemetry-java-instrumentation),
  log export is built into the agent and needs none of the JARs above on the
  application classpath. The agent JAR itself (2.32.0) is 25.2 MiB.
* **Runtime cost.** These are JAR sizes only, not memory, startup time, or throughput.

## Reproducing

Each setup is a POM with only dependencies; copy them out and total the sizes:

```bash
mvn -q dependency:copy-dependencies -DincludeScope=runtime -DoutputDirectory=libs
ls libs | wc -l
cat libs/*.jar | wc -c
```

Rainbow Gum:

```xml
<dependency>
  <groupId>io.jstach.rainbowgum</groupId>
  <artifactId>rainbowgum</artifactId>
  <version>0.15.0</version>
</dependency>
<dependency>
  <groupId>io.jstach.rainbowgum</groupId>
  <artifactId>rainbowgum-otlp</artifactId>
  <version>0.15.0</version>
</dependency>
```

Logback, JDK sender (Log4j2 swaps the first three for `log4j-api`, `log4j-core`, and
`opentelemetry-log4j-appender-2.17`; the OkHttp variants drop the exclusion and
`opentelemetry-exporter-sender-jdk`):

```xml
<dependencyManagement>
  <dependencies>
    <dependency>
      <groupId>io.opentelemetry</groupId>
      <artifactId>opentelemetry-bom</artifactId>
      <version>1.67.0</version>
      <type>pom</type>
      <scope>import</scope>
    </dependency>
  </dependencies>
</dependencyManagement>
<dependencies>
  <dependency>
    <groupId>org.slf4j</groupId>
    <artifactId>slf4j-api</artifactId>
    <version>2.0.20</version>
  </dependency>
  <dependency>
    <groupId>ch.qos.logback</groupId>
    <artifactId>logback-classic</artifactId>
    <version>1.6.5</version>
  </dependency>
  <dependency>
    <groupId>io.opentelemetry.instrumentation</groupId>
    <artifactId>opentelemetry-logback-appender-1.0</artifactId>
    <version>2.32.0-alpha</version>
  </dependency>
  <dependency>
    <groupId>io.opentelemetry</groupId>
    <artifactId>opentelemetry-sdk</artifactId>
  </dependency>
  <dependency>
    <groupId>io.opentelemetry</groupId>
    <artifactId>opentelemetry-exporter-otlp</artifactId>
    <exclusions>
      <exclusion>
        <groupId>io.opentelemetry</groupId>
        <artifactId>opentelemetry-exporter-sender-okhttp</artifactId>
      </exclusion>
    </exclusions>
  </dependency>
  <dependency>
    <groupId>io.opentelemetry</groupId>
    <artifactId>opentelemetry-exporter-sender-jdk</artifactId>
  </dependency>
</dependencies>
```
