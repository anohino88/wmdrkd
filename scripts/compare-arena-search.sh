#!/usr/bin/env bash
set -euo pipefail
root=$(cd -- "$(dirname -- "$0")/.." && pwd)
cd "$root"
temp=${RUNNER_TEMP:-/tmp}
baseline=a2a057001d539ce7ff82d376cc291e1b60caf286
if ! git cat-file -e "$baseline^{commit}"; then
    git fetch --no-tags --depth=1 origin "$baseline"
fi
source=$(mktemp -d "$temp/aw-arena-baseline.XXXXXX")
trap 'rm -rf "$source"' EXIT
git archive "$baseline" | tar -x -C "$source"
(cd "$source" && ./gradlew --no-daemon assemble)
AW_ARENA_BENCHMARK=1 AW_ARENA_POLICY=ZERO AW_ARENA_PLUGIN_JAR="$source/build/libs/AugmentWild-0.1.0-SNAPSHOT.jar" \
    RUNNER_TEMP="$temp/arena-zero" bash scripts/test-paper-smoke.sh
AW_ARENA_BENCHMARK=1 AW_ARENA_POLICY=MAX_RATIO RUNNER_TEMP="$temp/arena-ratio" bash scripts/test-paper-smoke.sh
