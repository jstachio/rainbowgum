#!/bin/sh
set -eu
cd "$(dirname "$0")"
exec java \
	-Dlogging.level=INFO \
	-Dlogging.appender.console.encoder=pattern \
	-Dlogging.encoder.console.pattern='%d{HH:mm:ss.SSS} [%thread] %-5level %logger - %msg%n' \
	-Dlogging.appender.console.flags=immediate_flush \
	-jar "target/rainbowgum-benchmark-micro-rainbowgum-0.10.0-SNAPSHOT.jar" "$@"
