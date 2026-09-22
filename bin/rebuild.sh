#!/bin/bash
# We assume the tag version has ben checkedout already
# -T1: opt back out of the -T2C default in .mvn/maven.config for release builds.
# -Dmaven.build.cache.enabled=false: explicit, not just relying on -Ddeploy=release's
# own deploy-release profile to imply it, since a reproducible build must never be
# allowed to silently restore a cached artifact from some other local build/checkout.
bin/vh set pom && ./mvnw clean package -Ddeploy=release -Duser.timezone=UTC -DskipTests -Dmaven.javadoc.skip -Dgpg.skip -Dmaven.build.cache.enabled=false -T1 $@
