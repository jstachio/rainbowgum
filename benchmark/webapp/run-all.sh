#!/bin/sh
# Builds and runs each webapp benchmark app in turn (sequentially, never concurrently, so
# results aren't skewed by CPU contention between apps), collecting throughput/latency
# (via the driver module) and RSS + a JFR recording per app. See ../../README or the repo
# root for the plan this implements.
set -eu
cd "$(dirname "$0")"

WARMUP_SECONDS=${WARMUP_SECONDS:-10}
DURATION_SECONDS=${DURATION_SECONDS:-30}
CONCURRENCY=${CONCURRENCY:-50}
URL_PATH="/api/greet/world"
PORT=8080

echo "Building..."
( cd ../.. && ./mvnw -q -pl benchmark/webapp -am install -DskipTests )

RESULTS_DIR="$(pwd)/results"
mkdir -p "$RESULTS_DIR"
rm -f "$RESULTS_DIR/results.csv"

run_one() {
	name="$1"
	app_dir="rainbowgum-benchmark-webapp-$name"

	echo "=== $name ==="
	rm -f "$app_dir/target/app.jfr" "$app_dir"/benchmark.log
	(cd "$app_dir" && exec ./run.sh) >"$RESULTS_DIR/$name-stdout.log" 2>&1 &
	pid=$!

	i=0
	until curl -s -o /dev/null "http://localhost:$PORT$URL_PATH"; do
		i=$((i + 1))
		if [ "$i" -gt 60 ]; then
			echo "$name did not become ready in time" >&2
			kill "$pid" 2>/dev/null || true
			exit 1
		fi
		sleep 1
	done

	./rainbowgum-benchmark-webapp-driver/run.sh \
		--url "http://localhost:$PORT$URL_PATH" \
		--warmup "$WARMUP_SECONDS" \
		--duration "$DURATION_SECONDS" \
		--concurrency "$CONCURRENCY" \
		--pid "$pid" \
		--label "$name" \
		--out "$RESULTS_DIR/results.csv"

	kill "$pid" 2>/dev/null || true
	wait "$pid" 2>/dev/null || true

	jfr_file="$app_dir/target/app.jfr"
	if [ -f "$jfr_file" ]; then
		jfr print --events jdk.GCHeapSummary,jdk.ThreadAllocationStatistics "$jfr_file" \
			>"$RESULTS_DIR/$name-jfr.txt" 2>&1 || true
	fi
}

run_one logback
run_one log4j2
run_one rainbowgum

echo
echo "Results ($RESULTS_DIR/results.csv):"
cat "$RESULTS_DIR/results.csv"
