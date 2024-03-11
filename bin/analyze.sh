#!/bin/bash

set -e

_profiles="$1"
if [ -z "$_profiles" ]; then
  _profiles="checkerframework errorprone eclipse"
fi

_ignored_profiles="-enforce-maven-version,-format-apply,-deploy-local,-javadoc-jar"

for profile in $_profiles; do
echo ""
echo "--------------------- Running $profile -----------------------"
echo ""

_CLEAN="clean"
#if [[ "eclipse" == "$profile" ]]; then
#  _CLEAN=""
#fi
./mvnw $MAVEN_CLI_OPTS ${_CLEAN} verify -pl core -P${profile},show-profiles,${_ignored_profiles} -Dmaven.javadoc.skip -DskipTests -Dmaven.source.skip=true
done

# Checker or the maven compiler leaves these files around
# I'm not sure why
find . -name "javac.*.args" | xargs rm -f
