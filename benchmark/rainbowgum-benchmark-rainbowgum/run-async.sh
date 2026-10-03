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
mkdir -p "$results"

./mvnw -pl benchmark/rainbowgum-benchmark-rainbowgum -am verify > "$results/build.log" 2>&1
classpath=core/target/classes:rainbowgum-annotation/target/classes:benchmark/rainbowgum-benchmark-rainbowgum/target/classes
for ((fork = 1; fork <= forks; fork++)); do
    java -Xms512m -Xmx512m -cp "$classpath" \
        io.jstach.rainbowgum.rainbowgum.AsyncPublisherBenchmark \
        "$events" "$warmups" "$measurements" "$capacity" "$fork" \
        > "$results/fork-$fork.csv" 2> "$results/fork-$fork.log"
    printf 'Completed fork %s/%s: %s/fork-%s.csv\n' "$fork" "$forks" "$results" "$fork"
done
