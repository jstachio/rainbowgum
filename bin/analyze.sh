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
_modules="core,rainbowgum-annotation,rainbowgum-jul,rainbowgum-scoped-key-values-api,rainbowgum-jdk,rainbowgum,rainbowgum-simple-props,rainbowgum-scoped-key-values,rainbowgum-avaje-config,rainbowgum-jansi,rainbowgum-disruptor,rainbowgum-pattern,rainbowgum-systemlogger"

# Modules excluded from the checkerframework profile only - errorprone and nullaway are
# independent tools (nullaway doesn't use Checker Framework machinery at all) and both
# pass clean on these, so they still get analyzed by those two:
# - rainbowgum-file, rainbowgum-tomcat, :rainbowgum-spring-boot4: Checker Framework itself
#   crashes (BugInCF on getElementValueArray/resourceleak, reproducibly, analyzing a
#   close() call against a freshly-compiled - not stub/bytecode - declaring class - not a
#   code problem here, see develop.md).
# - rainbowgum-slf4j: checkerframework's own test-compile has never actually succeeded for
#   this module - its checkerframework profile (rainbowgum-slf4j/pom.xml) never wired
#   jstachio-apt's own processor (io.jstach.apt.GenerateRendererProcessor) into the
#   root-pom checkerframework profile's explicit <annotationProcessors> list (unlike
#   errorprone/nullaway, which have no such explicit list and auto-run whatever's on the
#   annotationProcessorPath), so test-compile fails immediately on "cannot find symbol" for
#   generated renderer classes before any real nullness checking of the test tree happens.
#   Wiring it in unblocks test-compile but then surfaces real findings inside jstachio-apt's
#   *own* generated code (passing null to Function<...>-typed constructor params) that
#   aren't ours to fix - real design work, not mechanical. Main-source-only
#   `checkerframework compile` is clean; errorprone and nullaway are both fully clean,
#   main+test.
_modules_no_checkerframework="rainbowgum-file,rainbowgum-tomcat,:rainbowgum-spring-boot4,rainbowgum-slf4j"

# Modules where checkerframework and/or nullaway need real design work before they can be
# enabled (not mechanical fixes - see develop.md), but errorprone alone is clean:
# rainbowgum-json has not actually been tried under checkerframework/nullaway - included
# here for now on the assumption it needs the same kind of pass; revisit if that turns out
# to be wrong.
_modules_errorprone_only="rainbowgum-json"

# Capture an ad-hoc override once, before the loop: _run_modules gets recomputed per
# profile below (checkerframework/errorprone/nullaway each need a different module list),
# so checking "is it still empty" inside the loop doesn't work - the first iteration's
# computed value is never empty again, silently reusing that first profile's module list
# for every later profile in the same invocation instead of recomputing per profile.
_env_run_modules="${_run_modules:-}"

for profile in $_profiles; do
echo ""
echo "--------------------- Running $profile -----------------------"
echo ""

_CLEAN="clean"
#if [[ "eclipse" == "$profile" ]]; then
#  _CLEAN=""
#fi

# set env var _run_modules at the command line to run specific modules adhoc.
if [[ -n "$_env_run_modules" ]]; then
  _run_modules="$_env_run_modules"
else
  _run_modules="${_modules}"
  if [[ "checkerframework" != "$profile" ]]; then
    _run_modules="${_run_modules},${_modules_no_checkerframework}"
  fi
  if [[ "errorprone" == "$profile" ]]; then
    _run_modules="${_run_modules},${_modules_errorprone_only}"
  fi
fi

./mvnw $MAVEN_CLI_OPTS ${_CLEAN} verify -pl ${_run_modules} -P${profile},show-profiles,${_ignored_profiles} -Dmaven.javadoc.skip -DskipTests -Dmaven.source.skip=true
done

# Checker or the maven compiler leaves these files around
# I'm not sure why
find . -name "javac.*.args" | xargs rm -f
