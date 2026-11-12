#!/bin/sh
set -eu
cd "$(dirname "$0")"
JAR=$(ls target/*.jar | grep -v '\.original$' | head -1)
exec java -Xms512m -Xmx512m -XX:StartFlightRecording=filename=target/app.jfr,settings=profile \
	-DFILE_LOG_PATTERN='%d{HH:mm:ss.SSS} %p [%X{requestId}] %logger - %m%n' \
	-Dlogging.file.name=./benchmark.log \
	-jar "$JAR" "$@"
