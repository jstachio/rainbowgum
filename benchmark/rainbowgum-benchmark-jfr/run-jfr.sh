#!/usr/bin/env bash
# Redirects the same default "console" appender to a JFR recording file instead of
# stdout: -Dlogging.appender.console.output=jfr:///<path> makes JfrLogOutput start and
# own its own Recording for the process lifetime, capturing every event unconditionally
# (Rainbow Gum's level resolver decides what gets logged at all, same as always; JFR's
# own per-event filtering is bypassed in this mode). No code change, no jar rebuild
# needed to switch between the two - same jar as run-ttll.sh.
set -euo pipefail
cd "$(dirname "$0")"

JAR=$(ls target/rainbowgum-benchmark-jfr-*.jar | grep -v sources | head -1)
OUT=out-jfr.jfr
rm -f "$OUT"

/usr/bin/time -v java -Dlogging.appender.console.output="jfr:///$(pwd)/$OUT" -jar "$JAR" "$@" > stdout-jfr.log 2> time-jfr.txt
grep "^DURATION" stdout-jfr.log
echo "output file size: $(stat -c%s "$OUT") bytes"
grep -E "Maximum resident set size|Elapsed" time-jfr.txt
