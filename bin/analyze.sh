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
  _profiles="checkerframework errorprone nullaway"
fi

_ignored_profiles="-enforce-maven-version,-format-apply,-deploy-local,-javadoc-jar"

# Null analysis (checkerframework/errorprone/nullaway) is being turned on one module at a
# time, one commit per module - see develop.md. Grows here as each module is verified
# clean (or fixed) rather than flipping the whole reactor on at once.
_modules="core,rainbowgum-annotation,rainbowgum-jul,rainbowgum-scoped-key-values-api,rainbowgum-jdk,rainbowgum,rainbowgum-simple-props,rainbowgum-scoped-key-values,rainbowgum-avaje-config,rainbowgum-jansi,rainbowgum-disruptor"

# Modules where Checker Framework itself crashes (BugInCF on
# getElementValueArray/resourceleak, reproducibly, analyzing a close() call against a
# freshly-compiled - not stub/bytecode - declaring class - not a code problem here, see
# develop.md) - excluded from the checkerframework profile only. errorprone and nullaway
# are independent tools (nullaway doesn't use Checker Framework machinery at all) and both
# pass clean on these, so they still get analyzed by those two.
_modules_no_checkerframework="rainbowgum-file,rainbowgum-tomcat,:rainbowgum-spring-boot4"

for profile in $_profiles; do
echo ""
echo "--------------------- Running $profile -----------------------"
echo ""

_CLEAN="clean"
#if [[ "eclipse" == "$profile" ]]; then
#  _CLEAN=""
#fi
_run_modules="${_modules}"
if [[ "checkerframework" != "$profile" ]]; then
  _run_modules="${_modules},${_modules_no_checkerframework}"
fi
./mvnw $MAVEN_CLI_OPTS ${_CLEAN} verify -pl ${_run_modules} -P${profile},show-profiles,${_ignored_profiles} -Dmaven.javadoc.skip -DskipTests -Dmaven.source.skip=true
done

# Checker or the maven compiler leaves these files around
# I'm not sure why
find . -name "javac.*.args" | xargs rm -f
