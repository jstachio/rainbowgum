#!/bin/bash

set -e

_profiles="$1"
if [ -z "$_profiles" ]; then
  # "eclipse" is deliberately not in this default set: it is known-broken
  # (unrelated -failOnWarning/unsupported @SuppressWarnings issues in ECJ, not
  # something a normal code change here fixes) and is kept around anyway - this
  # project is one of the few actually exercising Eclipse's own JDT null-analysis
  # against JSpecify annotations, with an eye towards eventually helping the
  # Eclipse team get their side of JSpecify support working. Pass it explicitly
  # (./bin/analyze.sh eclipse) if you want to run it.
  _profiles="checkerframework errorprone"
fi

_ignored_profiles="-enforce-maven-version,-format-apply,-deploy-local,-javadoc-jar"

# Null analysis (checkerframework/errorprone) is being turned on one module at a time,
# one commit per module - see develop.md. Grows here as each module is verified clean
# (or fixed) rather than flipping the whole reactor on at once.
_modules="core,rainbowgum-annotation,rainbowgum-jul,rainbowgum-scoped-key-values-api,rainbowgum-jdk,rainbowgum"

for profile in $_profiles; do
echo ""
echo "--------------------- Running $profile -----------------------"
echo ""

_CLEAN="clean"
#if [[ "eclipse" == "$profile" ]]; then
#  _CLEAN=""
#fi
./mvnw $MAVEN_CLI_OPTS ${_CLEAN} verify -pl ${_modules} -P${profile},show-profiles,${_ignored_profiles} -Dmaven.javadoc.skip -DskipTests -Dmaven.source.skip=true
done

# Checker or the maven compiler leaves these files around
# I'm not sure why
find . -name "javac.*.args" | xargs rm -f
