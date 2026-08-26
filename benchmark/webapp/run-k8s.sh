#!/bin/sh
# Models a 12-factor/k8s-style deployment: structured (GELF) logging to stdout only, no
# file output at all - the common container pattern where the platform captures stdout
# and file logging would just be wasted disk I/O. Unlike run-all.sh (which always writes
# both console and file - see FINDINGS.md), this is GELF-to-console in isolation.
#
# Runs both PLATFORM and VIRTUAL_THREADS permutations for all three apps in one go (6
# runs total). See run-all.sh for WARMUP_SECONDS/DURATION_SECONDS/CONCURRENCY env vars,
# same defaults here.
#
# RainbowGum has no property-driven equivalent to Spring Boot's logging.file.name/
# logging.structured.format.console yet (see GelfSpringRainbowGumServiceProvider) - file
# output is suppressed here via the RainbowGum-specific logging.appenders=console
# property instead of an empty logging.file.name (which crashes - RainbowGum's own file
# path resolution mishandles an empty value, treating it as the current directory rather
# than "no file"; tracked as a rough edge, not fixed here). Logback/Log4j2 use Spring
# Boot's own logging.file.name= (empty) which cleanly disables file output.
set -eu
cd "$(dirname "$0")"

WARMUP_SECONDS=${WARMUP_SECONDS:-10}
DURATION_SECONDS=${DURATION_SECONDS:-30}
CONCURRENCY=${CONCURRENCY:-50}
URL_PATH="/api/greet/world"
PORT=8080

echo "Building..."
( cd ../.. && ./mvnw -q -pl benchmark/webapp,benchmark/webapp/rainbowgum-benchmark-webapp-share,benchmark/webapp/rainbowgum-benchmark-webapp-logback,benchmark/webapp/rainbowgum-benchmark-webapp-log4j2,benchmark/webapp/rainbowgum-benchmark-webapp-rainbowgum,benchmark/webapp/rainbowgum-benchmark-webapp-driver -am install -DskipTests )

RESULTS_DIR="$(pwd)/results"
mkdir -p "$RESULTS_DIR"

run_one() {
	name="$1"
	vt="$2"
	label="$name-k8s${vt:+-vt}"
	app_dir="rainbowgum-benchmark-webapp-$name"

	echo "=== $label ==="
	rm -f "$app_dir/target/app.jfr" "$app_dir"/benchmark.log

	if [ "$name" = "rainbowgum" ]; then
		file_flag="--logging.appenders=console"
	else
		file_flag="--logging.file.name="
	fi

	(cd "$app_dir" && exec ./run.sh \
		"$file_flag" \
		--logging.structured.format.console=gelf \
		--logging.structured.gelf.host=benchmark-host \
		${vt:+--spring.threads.virtual.enabled=true}) \
		>"$RESULTS_DIR/$label-stdout.log" 2>&1 &
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

	./rainbowgum-benchmark-webapp-driver/run.sh \
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

for name in logback log4j2 rainbowgum; do
	run_one "$name" ""
done
for name in logback log4j2 rainbowgum; do
	run_one "$name" "vt"
done

echo
echo "Results ($RESULTS_DIR/results.csv):"
cat "$RESULTS_DIR/results.csv"
