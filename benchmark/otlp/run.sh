#!/bin/sh
# Runs the OTLP export benchmark for each framework in turn (never concurrently), against
# an OpenTelemetry Collector in Docker that accepts and discards the records.
#
#   ./run.sh                       all three frameworks
#   ./run.sh rainbowgum logback    just these
#
# Environment: EVENTS (default 200000), WARMUP (default 50000), THREADS (default 4),
# JAVA_OPTS (extra JVM options, for example -Dbench.otel.maxQueueSize=100000).
set -eu
cd "$(dirname "$0")"

EVENTS=${EVENTS:-200000}
WARMUP=${WARMUP:-50000}
THREADS=${THREADS:-4}
JAVA_OPTS=${JAVA_OPTS:-}
COLLECTOR_IMAGE=${COLLECTOR_IMAGE:-otel/opentelemetry-collector:0.162.0}
FRAMEWORKS=${*:-rainbowgum logback log4j2}

../../mvnw -q package -DskipTests

if ! curl -s -o /dev/null http://localhost:8888/metrics; then
	docker rm -f rg-otlp-bench >/dev/null 2>&1 || true
	docker run -d --name rg-otlp-bench --network host \
		-v "$(pwd)/collector.yaml:/etc/otelcol/config.yaml:ro" \
		"$COLLECTOR_IMAGE" --config /etc/otelcol/config.yaml >/dev/null
	until curl -s -o /dev/null http://localhost:8888/metrics; do sleep 0.5; done
fi

mkdir -p results
CSV="results/results.csv"
for f in $FRAMEWORKS; do
	echo "=== $f"
	# shellcheck disable=SC2086
	java -Xms512m -Xmx512m $JAVA_OPTS \
		-Dbench.events="$EVENTS" -Dbench.warmup="$WARMUP" -Dbench.threads="$THREADS" -Dbench.out="$CSV" \
		-cp "$f/target/classes:$(cat "$f/target/classpath.txt")" "bench.otlp.$f.Main" \
		>"results/$f-out.log" 2>"results/$f-err.log" || { echo "$f failed, see results/$f-err.log"; tail -20 "results/$f-err.log"; continue; }
	grep "^$f" "results/$f-err.log"
done
echo
column -s, -t <"$CSV"
