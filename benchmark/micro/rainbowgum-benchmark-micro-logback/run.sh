#!/bin/sh
set -eu
cd "$(dirname "$0")"
exec java -jar "target/rainbowgum-benchmark-micro-logback-0.10.0-SNAPSHOT.jar" "$@"
