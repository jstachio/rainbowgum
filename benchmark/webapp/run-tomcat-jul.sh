#!/bin/sh
# Isolated 3-way comparison of how Tomcat's own internal logging (catalina/coyote/tomcat
# packages) is handled, using ONLY the rainbowgum-benchmark-webapp-rainbowgum app (no
# logback/log4j2, no JSON/GELF, no virtual threads) so the only thing varying between runs
# is this one dimension:
#
#   jul     - default: no rainbowgum-tomcat dependency, RainbowGum's JUL bridge installed
#             and enabled as normal, so Tomcat's own java.util.logging calls route through
#             it (full pipeline: JUL Logger -> SystemLoggerQueueJULHandler -> RainbowGum
#             LogRouter), unsilenced (INFO).
#   nojul   - same build as "jul" (no rainbowgum-tomcat dependency) but JUL is completely
#             disabled: --logging.jul.disable=true (RainbowGum never installs its JUL
#             bridge handler) plus -Djava.util.logging.config.file=jul-disabled.properties
#             (handlers= and .level=OFF) so JUL's own default ConsoleHandler doesn't pick
#             up the slack and print uncoordinated output straight to stderr. This is the
#             zero-Tomcat-internal-logging-cost floor.
#   tomcat  - built with -Ptomcat (adds the rainbowgum-tomcat dependency), JUL left enabled
#             as normal - Tomcat's own logging now goes through RainbowGumTomcatLog directly,
#             bypassing java.util.logging entirely, while everything else about the run is
#             identical to "jul".
#
# Two builds are needed (one without -Ptomcat covers jul+nojul, one with -Ptomcat covers
# tomcat) since the dependency is compiled/packaged in, not a runtime toggle.
set -eu
cd "$(dirname "$0")"

WARMUP_SECONDS=${WARMUP_SECONDS:-10}
DURATION_SECONDS=${DURATION_SECONDS:-30}
CONCURRENCY=${CONCURRENCY:-50}
URL_PATH="/api/greet/world"
PORT=8080
APP_DIR="rainbowgum-benchmark-webapp-rainbowgum"

RESULTS_DIR="$(pwd)/results"
mkdir -p "$RESULTS_DIR"
OUT_CSV="$RESULTS_DIR/tomcat-jul-isolation.csv"
rm -f "$OUT_CSV"

build() {
	profile_arg="$1"
	echo "Building ($profile_arg)..."
	rm -rf ../../rainbowgum-apt/target
	( cd ../.. && ./mvnw -q -pl benchmark/webapp,benchmark/webapp/rainbowgum-benchmark-webapp-share,benchmark/webapp/rainbowgum-benchmark-webapp-rainbowgum,benchmark/webapp/rainbowgum-benchmark-webapp-driver -am install -DskipTests $profile_arg )
}

run_one() {
	label="$1"
	jvm_args="$2"
	app_args="$3"

	echo "=== $label ==="
	rm -f "$APP_DIR/target/app.jfr" "$APP_DIR"/benchmark.log
	JAR=$(ls "$APP_DIR"/target/*.jar | grep -v '\.original$' | head -1)
	(cd "$APP_DIR" && exec java -Xms512m -Xmx512m -XX:StartFlightRecording=filename=target/app.jfr,settings=profile \
		-DFILE_LOG_PATTERN='%d{HH:mm:ss.SSS} %p [%X{requestId}] %logger - %m%n' \
		-Dlogging.file.name=./benchmark.log \
		$jvm_args \
		-jar "target/$(basename "$JAR")" \
		$app_args) \
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
		--out "$OUT_CSV"

	kill "$pid" 2>/dev/null || true
	wait "$pid" 2>/dev/null || true

	jfr_file="$APP_DIR/target/app.jfr"
	if [ -f "$jfr_file" ]; then
		jfr print --events jdk.GCHeapSummary,jdk.ThreadAllocationStatistics "$jfr_file" \
			>"$RESULTS_DIR/$label-jfr.txt" 2>&1 || true
	fi
}

build ""
run_one "rainbowgum-jul" "" ""
run_one "rainbowgum-nojul" \
	"-Djava.util.logging.config.file=$(pwd)/$APP_DIR/jul-disabled.properties" \
	"--logging.jul.disable=true"

build "-Ptomcat"
run_one "rainbowgum-tomcat" "" ""

echo
echo "Results ($OUT_CSV):"
cat "$OUT_CSV"
