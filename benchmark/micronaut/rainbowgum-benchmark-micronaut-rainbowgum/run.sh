#!/bin/sh
set -eu
cd "$(dirname "$0")"
JAR=$(ls target/*.jar | grep -v '^target/original-' | head -1)
# -Dlogging.appender.console.encoder, not a classpath logging.properties file: see
# FINDINGS.md for why - rainbowgum-simple-props's SimplePropertiesProvider is never
# discovered via ServiceLoader once shaded onto a plain classpath (a real bug, not
# specific to this benchmark). A JVM system property is LogProperties'
# SYSTEM_PROPERTIES layer, which needs no ServiceLoader at all, so it is unaffected.
exec java -Xms512m -Xmx512m -XX:StartFlightRecording=filename=target/app.jfr,settings=profile \
	-Dlogging.appender.console.encoder=logback \
	-jar "$JAR" "$@"
