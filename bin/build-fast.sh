#!/bin/bash
# Fast local dev build: parallel reactor (-T2C), no per-module javadoc generation. Not a
# substitute for a real CI/release build - use bin/doc.sh for the comprehensive javadoc
# build this intentionally skips.
#
# Uses -D (command line, highest precedence) rather than a Maven profile property - a
# profile-scoped <maven.javadoc.skip>true</maven.javadoc.skip> did not reliably skip
# javadoc across this multi-module reactor, so that profile (formerly "fast" in pom.xml)
# was removed.
#
# JUnit 5 test-level parallelism was also tried here and dropped: it did not actually
# speed up the build, and the real fix for slow tests was moving them into their own
# test module instead (see test/rainbowgum-test-file, RollingFileOutputTest and friends).
mvnd --batch-mode --no-transfer-progress -T2C -Dmaven.javadoc.skip=true -q clean install $*
