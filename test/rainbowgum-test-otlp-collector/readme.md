# OTLP Collector integration test

This runs real SLF4J logging through Rainbow Gum's async publisher, OpenTelemetry
key values contributor and OTLP output into an OpenTelemetry Collector process.
It verifies the records exported by the collector, including their resource,
instrumentation scope, message, severity, attributes, exception and trace context.
Both HTTP/protobuf and HTTP/JSON run with and without gzip. The test uses a real
OpenTelemetry SDK span, then logs outside its scope to check that context does
not leak. Closing Rainbow Gum must drain the async publisher and flush the last
OTLP batch.

This module is deliberately absent from `test/pom.xml`'s module list. Normal
reactor builds neither download nor start a collector.

## Run it

From the repository root, first install the branch's artifacts:

```sh
./mvnw install
bin/test-otlp-collector.sh
```

The runner downloads OpenTelemetry Collector Contrib **0.162.0**, checks the
release archive's SHA-256, and caches the executable under
`${XDG_CACHE_HOME:-$HOME/.cache}/rainbowgum/otelcol/`. Later runs reuse that binary.
Automatic download supports Linux and macOS on ARM64 and AMD64. It needs `curl`,
`tar`, and either `sha256sum` or `shasum`; Docker is not required.

To use an already installed collector:

```sh
OTELCOL_BIN=/absolute/path/to/otelcol-contrib bin/test-otlp-collector.sh
```

Or invoke Maven directly:

```sh
./mvnw -f test/rainbowgum-test-otlp-collector/pom.xml \
    -Dotelcol.binary=/absolute/path/to/otelcol-contrib verify
```

Missing or incompatible collectors fail the test; it does not silently skip.
The module disables Maven's build cache so an explicit invocation always runs
against the consumer. Normal reactor builds retain their usual caching.

## Consumer setup

[src/test/resources/collector.yaml](src/test/resources/collector.yaml) configures
an OTLP HTTP receiver, a health endpoint, and protobuf and JSON file exporters. It binds to the
loopback address on ephemeral ports, needs no account or cloud service, and
contains no backend credentials. There are no processors, so assertions inspect
what the collector accepted from Rainbow Gum.

The test starts a fresh collector per protocol/compression combination. It waits
for the health endpoint, logs, closes Rainbow Gum, and then stops the collector
gracefully so its file buffer is flushed. The collector is also stopped on test
failure. Readiness and shutdown waits have deadlines.

Output remains under `target/collector/<protocol>-<compression>/<run-id>/`:

- `collector.yaml`: the consumer configuration.
- `collector.log`: startup, export and shutdown diagnostics.
- `logs.pb`: received logs in the collector file exporter's protobuf format.
- `logs.json`: the same logs as readable OTLP JSON, one batch per line.

`logs.pb` contains batches framed with a four-byte big-endian length. The test
parses each payload using the official Java OTLP protobuf classes and compares
its records. This checks the consumer's exported data rather than just treating
an HTTP 200 response as proof that logging worked.

To inspect one received batch:

```sh
head -n 1 target/collector/<protocol>-<compression>/<run-id>/logs.json | python3 -m json.tool
```

The file exporter is a test sink, not an interactive log viewer. A production
collector would usually export to your chosen observability backend instead.
This test does not exercise a backend UI, authentication, TLS, or trace export.

Sources: [Collector configuration](https://opentelemetry.io/docs/collector/configuration/),
[pinned file exporter documentation](https://github.com/open-telemetry/opentelemetry-collector-contrib/blob/v0.162.0/exporter/fileexporter/README.md),
[collector release](https://github.com/open-telemetry/opentelemetry-collector-releases/releases/tag/v0.162.0).
