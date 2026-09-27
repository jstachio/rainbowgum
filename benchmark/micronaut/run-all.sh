#!/bin/sh
# Builds and runs each Micronaut benchmark app in turn (sequentially, never concurrently,
# so results aren't skewed by CPU contention between apps), collecting throughput/latency
# (via the driver module) and RSS + a JFR recording per app.
#
# HotSpot only, JSON only, no level/thread overrides: RainbowGum (LogbackJsonEncoder) vs
# Logback (its own built-in JsonEncoder), both using whatever Micronaut does out of the
# box for request-handling threads (see FINDINGS.md - confirmed via the response body's
# own threadName field, not assumed).
set -eu
cd "$(dirname "$0")"

WARMUP_SECONDS=${WARMUP_SECONDS:-10}
DURATION_SECONDS=${DURATION_SECONDS:-30}
CONCURRENCY=${CONCURRENCY:-50}
URL_PATH="/api/greet/world"
PORT=8080

echo "Building..."
( cd ../.. && ./mvnw -q -f benchmark/micronaut/pom.xml clean package -DskipTests )

RESULTS_DIR="$(pwd)/results"
mkdir -p "$RESULTS_DIR"

run_one() {
	name="$1"
	label="$name"
	app_dir="rainbowgum-benchmark-micronaut-$name"

	echo "=== $label ==="
	rm -f "$app_dir/target/app.jfr"
	(cd "$app_dir" && exec ./run.sh) >"$RESULTS_DIR/$label-stdout.log" 2>&1 &
	pid=$!

	i=0
	until curl -s -o /dev/null "http://localhost:$PORT$URL_PATH"; do
		i=$((i + 1))
		if [ "$i" -gt 60 ]; then
			echo "$label did not become ready in time" >&2
			kill "$pid" 2>/dev/null || true
			exit 1
		fi
		sleep 1
	done

	if [ "$name" = "rainbowgum" ]; then
		curl -s "http://localhost:$PORT/api/config-report" >"$RESULTS_DIR/$label-config-report.txt" || true
	fi

	./rainbowgum-benchmark-micronaut-driver/run.sh \
		--url "http://localhost:$PORT$URL_PATH" \
		--warmup "$WARMUP_SECONDS" \
		--duration "$DURATION_SECONDS" \
		--concurrency "$CONCURRENCY" \
		--pid "$pid" \
		--label "$label" \
		--out "$RESULTS_DIR/results.csv"

	kill "$pid" 2>/dev/null || true
	wait "$pid" 2>/dev/null || true

	jfr_file="$app_dir/target/app.jfr"
	if [ -f "$jfr_file" ]; then
		jfr print --events jdk.GCHeapSummary,jdk.ThreadAllocationStatistics "$jfr_file" \
			>"$RESULTS_DIR/$label-jfr.txt" 2>&1 || true
	fi
}

run_one logback
run_one rainbowgum

echo
echo "Results ($RESULTS_DIR/results.csv):"
cat "$RESULTS_DIR/results.csv"
