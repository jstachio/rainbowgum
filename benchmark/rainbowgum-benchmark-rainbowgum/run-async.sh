#!/usr/bin/env bash
set -euo pipefail

repo_root=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/../.." && pwd)
cd "$repo_root"

results=${1:-target/async-publisher-results}
events=${2:-1000000}
warmups=${3:-3}
measurements=${4:-7}
forks=${5:-3}
capacity=${6:-1024}
opus_ref=${7:-}
mkdir -p "$results"
results=$(realpath "$results")

./mvnw -pl benchmark/rainbowgum-benchmark-rainbowgum -am verify > "$results/build.log" 2>&1
classpath=core/target/classes:rainbowgum-annotation/target/classes:benchmark/rainbowgum-benchmark-rainbowgum/target/classes
implementations=BLOCKING,CODEX
if [[ -n "$opus_ref" ]]; then
    opus_commit=$(git rev-parse --verify "${opus_ref}^{commit}")
    opus_source=core/src/main/java/io/jstach/rainbowgum/publisher/OpusAsyncPublisher.java
    mkdir -p "$results/opus-source" "$results/opus-classes"
    git show "$opus_commit:$opus_source" > "$results/opus-source/OpusAsyncPublisher.java"
    printf '%s\n' "$opus_commit" > "$results/opus-commit.txt"
    git rev-parse HEAD > "$results/codex-commit.txt"
    ./mvnw -pl core -Dmaven.build.cache.enabled=false dependency:build-classpath \
        -Dmdep.outputFile="$results/core-classpath.txt" \
        > "$results/classpath.log" 2>&1
    core_dependencies=$(cat "$results/core-classpath.txt")
    javac --release 21 -cp "$classpath:$core_dependencies" \
        -d "$results/opus-classes" "$results/opus-source/OpusAsyncPublisher.java"
    classpath="$classpath:$results/opus-classes"
    implementations=BLOCKING,CODEX,OPUS
    java -cp "$classpath" io.jstach.rainbowgum.rainbowgum.AsyncPublisherBehaviorProbe \
        > "$results/behavior.txt" 2> "$results/behavior.log"
fi
for ((fork = 1; fork <= forks; fork++)); do
    java -Xms512m -Xmx512m -cp "$classpath" \
        io.jstach.rainbowgum.rainbowgum.AsyncPublisherBenchmark \
        "$events" "$warmups" "$measurements" "$capacity" "$fork" "$implementations" \
        > "$results/fork-$fork.csv" 2> "$results/fork-$fork.log"
    printf 'Completed fork %s/%s: %s/fork-%s.csv\n' "$fork" "$forks" "$results" "$fork"
done
