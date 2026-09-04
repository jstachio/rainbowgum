#!/bin/sh
set -eu
cd "$(dirname "$0")"
JAR=$(ls target/*.jar | grep -v '\.original$' | head -1)
exec java -Xms512m -Xmx512m -XX:StartFlightRecording=filename=target/app.jfr,settings=profile -jar "$JAR" "$@"
