#!/bin/sh
set -eu
cd "$(dirname "$0")"
exec java -jar "target/rainbowgum-benchmark-micro-log4j2-0.10.0-SNAPSHOT.jar" "$@"
