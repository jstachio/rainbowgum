#!/bin/bash
# Fast local dev build: parallel reactor (-T1C), no per-module javadoc generation, and
# JUnit 5 test-level parallelism across classes within each module. Not a substitute for a
# real CI/release build - use bin/doc.sh for the comprehensive javadoc build this
# intentionally skips. See the "fast" profile's comment in pom.xml for how the test
# parallelism is made safe (deterministic thread name/id in golden-string assertions,
# @Isolated on classes that share JVM-wide static state) - and note it buys little at the
# full-reactor level, since -T1C already saturates available cores; it mainly helps
# individual test-heavy modules like core.
# -Pfast
mvnd --batch-mode --no-transfer-progress -T2C -Dmaven.javadoc.skip=true -q clean install $*
