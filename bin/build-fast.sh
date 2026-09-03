#!/bin/bash
# Fast local dev build: parallel reactor (-T1C), no per-module javadoc generation.
# Not a substitute for a real CI/release build - use bin/doc.sh for the comprehensive
# javadoc build this intentionally skips. JUnit 5 test-level parallelism was tried and
# deliberately left out for now - see the "fast" profile's comment in pom.xml for why.
./mvnw --batch-mode --no-transfer-progress -T1C -Pfast -q clean install $*
