#!/bin/sh
# Builds and runs the from-scratch (no Spring) SLF4J logging microbenchmark for Logback,
# Log4j2, and RainbowGum in turn, sequentially (never concurrently, so results aren't
# skewed by CPU contention between JVMs). Console output for each framework goes to
# results/<name>-out.log; the human-readable per-combination report and the shared CSV go
# to results/<name>-report.log and results/results.csv respectively.
#
# WARMUP / MEASURE (ops per worker, optional): default 5000 / 20000.
# CONCURRENCY (workers for PLATFORM/VIRTUAL modes, optional): default 16.
set -eu
cd "$(dirname "$0")"

WARMUP=${WARMUP:-5000}
MEASURE=${MEASURE:-20000}
CONCURRENCY=${CONCURRENCY:-16}

echo "Building..."
( cd ../.. && ./mvnw -q \
	-pl benchmark/micro,benchmark/micro/rainbowgum-benchmark-micro-share,benchmark/micro/rainbowgum-benchmark-micro-logback,benchmark/micro/rainbowgum-benchmark-micro-log4j2,benchmark/micro/rainbowgum-benchmark-micro-rainbowgum \
	-am install -DskipTests )

RESULTS_DIR="$(pwd)/results"
mkdir -p "$RESULTS_DIR"
CSV="$RESULTS_DIR/results.csv"

jar_of() {
	ls "rainbowgum-benchmark-micro-$1"/target/*.jar | grep -v '\.original$'
}

echo "=== logback ==="
java -Dbench.warmup="$WARMUP" -Dbench.measure="$MEASURE" -Dbench.concurrency="$CONCURRENCY" \
	-Dbench.out="$CSV" \
	-jar "$(jar_of logback)" \
	>"$RESULTS_DIR/logback-out.log" 2>"$RESULTS_DIR/logback-report.log"
tail -25 "$RESULTS_DIR/logback-report.log"

echo "=== log4j2 ==="
java -Dbench.warmup="$WARMUP" -Dbench.measure="$MEASURE" -Dbench.concurrency="$CONCURRENCY" \
	-Dbench.out="$CSV" \
	-jar "$(jar_of log4j2)" \
	>"$RESULTS_DIR/log4j2-out.log" 2>"$RESULTS_DIR/log4j2-report.log"
tail -25 "$RESULTS_DIR/log4j2-report.log"

echo "=== rainbowgum ==="
java -Dbench.warmup="$WARMUP" -Dbench.measure="$MEASURE" -Dbench.concurrency="$CONCURRENCY" \
	-Dbench.out="$CSV" \
	-Dlogging.level=INFO \
	-Dlogging.appender.console.encoder=pattern \
	-Dlogging.encoder.console.pattern='%d{HH:mm:ss.SSS} [%thread] %-5level %logger - %msg%n' \
	-Dlogging.appender.console.flags=immediate_flush \
	-jar "$(jar_of rainbowgum)" \
	>"$RESULTS_DIR/rainbowgum-out.log" 2>"$RESULTS_DIR/rainbowgum-report.log"
tail -25 "$RESULTS_DIR/rainbowgum-report.log"

echo
echo "Results ($CSV):"
cat "$CSV"
