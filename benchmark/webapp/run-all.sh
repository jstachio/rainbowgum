#!/bin/sh
# Builds and runs each webapp benchmark app in turn (sequentially, never concurrently, so
# results aren't skewed by CPU contention between apps), collecting throughput/latency
# (via the driver module) and RSS + a JFR recording per app. See ../../README or the repo
# root for the plan this implements.
#
# LOG_LEVEL (optional, e.g. LOG_LEVEL=ERROR): overrides logging.level.root on the app's
# command line, for isolating disabled-logging-call overhead (the "mostly noop" case) from
# the normal (INFO, everything fires) case.
#
# STRUCTURED_FORMAT (optional, currently only "gelf" is wired up): overrides
# logging.structured.format.file on the app's command line. Logback/Log4j2 honor this via
# Spring Boot's own built-in structured logging support; RainbowGum has no such property
# support yet, so rainbowgum-benchmark-webapp-rainbowgum's GelfSpringRainbowGumServiceProvider
# checks the same property key and swaps in rainbowgum-json's GelfEncoder to match.
#
# VIRTUAL_THREADS (optional, e.g. VIRTUAL_THREADS=true): overrides
# spring.threads.virtual.enabled, switching the embedded Tomcat's request-handling threads
# (and other Spring-managed executors) from platform to virtual threads. Pure Spring Boot/
# Tomcat property, honored identically by all three apps - no per-app code needed, unlike
# STRUCTURED_FORMAT.
#
# RG_IMMEDIATE_FLUSH (optional, e.g. RG_IMMEDIATE_FLUSH=true): RainbowGum-only. Logback's
# OutputStreamAppender defaults immediateFlush=true (confirmed in bytecode); RainbowGum's
# DefaultLogAppender defaults it false unless the IMMEDIATE_FLUSH AppenderFlag is set. This
# passes --logging.appender.file.flags=immediate_flush to the RainbowGum app only, to check
# how much of the gap to Logback is this flush-on-every-write difference rather than
# anything architectural. No equivalent needed for Logback/Log4j2 - they already flush by
# default.
#
# EXCLUDE_JUL (optional, e.g. EXCLUDE_JUL=true): silences Tomcat's own internal JUL-routed
# diagnostic logging (org.apache.tomcat/catalina/coyote, e.g. DirectJDKLog - see the JFR
# profiling section of FINDINGS.md) by setting those three package levels to OFF, applied
# identically to all three apps via the standard Spring Boot logging.level.<package>
# property. Tomcat's own internal logging noise was found to behave inconsistently between
# frameworks (RainbowGum's JULConfigurator sets the JUL root level; Logback's LoggingSystem
# doesn't) and isn't part of what this benchmark is actually trying to compare (each
# framework's own application-logging pipeline) - this isolates that.
#
# All are included in the CSV label and the per-app stdout/jfr filenames so different runs
# don't clobber each other's output.
set -eu
cd "$(dirname "$0")"

WARMUP_SECONDS=${WARMUP_SECONDS:-10}
DURATION_SECONDS=${DURATION_SECONDS:-30}
CONCURRENCY=${CONCURRENCY:-50}
LOG_LEVEL=${LOG_LEVEL:-}
STRUCTURED_FORMAT=${STRUCTURED_FORMAT:-}
VIRTUAL_THREADS=${VIRTUAL_THREADS:-}
RG_IMMEDIATE_FLUSH=${RG_IMMEDIATE_FLUSH:-}
EXCLUDE_JUL=${EXCLUDE_JUL:-}
URL_PATH="/api/greet/world"
PORT=8080
SUFFIX=${LOG_LEVEL:+-$LOG_LEVEL}${STRUCTURED_FORMAT:+-$STRUCTURED_FORMAT}${VIRTUAL_THREADS:+-vt}${RG_IMMEDIATE_FLUSH:+-flush}${EXCLUDE_JUL:+-nojul}

echo "Building..."
( cd ../.. && ./mvnw -q -pl benchmark/webapp,benchmark/webapp/rainbowgum-benchmark-webapp-share,benchmark/webapp/rainbowgum-benchmark-webapp-logback,benchmark/webapp/rainbowgum-benchmark-webapp-log4j2,benchmark/webapp/rainbowgum-benchmark-webapp-rainbowgum,benchmark/webapp/rainbowgum-benchmark-webapp-driver -am install -DskipTests )

RESULTS_DIR="$(pwd)/results"
mkdir -p "$RESULTS_DIR"

run_one() {
	name="$1"
	label="$name$SUFFIX"
	app_dir="rainbowgum-benchmark-webapp-$name"

	echo "=== $label ==="
	rm -f "$app_dir/target/app.jfr" "$app_dir"/benchmark.log
	rg_flush_arg=""
	if [ "$name" = "rainbowgum" ] && [ -n "$RG_IMMEDIATE_FLUSH" ]; then
		rg_flush_arg="--logging.appender.file.flags=immediate_flush"
	fi
	jul_args=""
	if [ -n "$EXCLUDE_JUL" ]; then
		jul_args="--logging.level.org.apache.tomcat=OFF --logging.level.org.apache.catalina=OFF --logging.level.org.apache.coyote=OFF"
	fi
	(cd "$app_dir" && exec ./run.sh \
		${LOG_LEVEL:+--logging.level.root=$LOG_LEVEL} \
		${STRUCTURED_FORMAT:+--logging.structured.format.file=$STRUCTURED_FORMAT} \
		${STRUCTURED_FORMAT:+--logging.structured.gelf.host=benchmark-host} \
		${VIRTUAL_THREADS:+--spring.threads.virtual.enabled=$VIRTUAL_THREADS} \
		$rg_flush_arg $jul_args) \
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

run_one logback
run_one log4j2
run_one rainbowgum

echo
echo "Results ($RESULTS_DIR/results.csv):"
cat "$RESULTS_DIR/results.csv"
