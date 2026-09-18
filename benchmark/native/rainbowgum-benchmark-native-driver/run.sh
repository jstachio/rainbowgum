#!/bin/sh
set -eu
cd "$(dirname "$0")"
JAR=$(ls target/*.jar | grep -v '\.original$' | head -1)
exec java -jar "$JAR" "$@"
