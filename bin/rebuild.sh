#!/bin/bash
# We assume the tag version has ben checkedout already
# -T1: opt back out of the -T2C default in .mvn/maven.config for release builds.
bin/vh set pom && mvn clean package -Ddeploy=release -Duser.timezone=UTC -DskipTests -Dmaven.javadoc.skip -Dgpg.skip -T1 $@
