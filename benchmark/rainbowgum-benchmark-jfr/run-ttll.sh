#!/usr/bin/env bash
# Baseline: RainbowGum's default TTLL encoder on the default "console" appender
# (stdout), with stdout redirected to a plain text file - the common "just log to
# stdout, let the platform capture it to a file" production pattern.
set -euo pipefail
cd "$(dirname "$0")"

JAR=$(ls target/rainbowgum-benchmark-jfr-*.jar | grep -v sources | head -1)
OUT=out-ttll.log

/usr/bin/time -v java -jar "$JAR" "$@" > "$OUT" 2> time-ttll.txt
grep "^DURATION" "$OUT"
echo "output file size: $(stat -c%s "$OUT") bytes"
grep -E "Maximum resident set size|Elapsed" time-ttll.txt
