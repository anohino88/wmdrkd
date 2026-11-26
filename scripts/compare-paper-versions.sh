#!/usr/bin/env bash
set -euo pipefail
root=$(cd -- "$(dirname -- "$0")/.." && pwd)
temp=${RUNNER_TEMP:-/tmp}
fixture=$(mktemp -d "$temp/augmentwild-comparison.XXXXXX")
out="$root/build/version-comparison"
mkdir -p "$out"
trap 'rm -rf "$fixture"' EXIT

for version in ${AW_COMPARE_VERSIONS:-phase82 part4 part5}; do
    case "$version" in
        phase82) commit=099a6d668fcbca9f357a49953c2fc49738160b31 ;;
        part4) commit=0ef1c55f9f85fc09c9991239c63e64c5585a093f ;;
        part5) commit=$(git -C "$root" rev-parse HEAD) ;;
        *) exit 2 ;;
    esac
    if ! git -C "$root" cat-file -e "$commit^{commit}"; then
        git -C "$root" fetch --no-tags --depth=1 origin "$commit"
    fi
    checkout="$fixture/$version"
    mkdir -p "$checkout"
    git -C "$root" archive "$commit" | tar -x -C "$checkout"
    # Use the same VFX measurement fixture for all versions. Leave all production Java sources intact.
    cp "$root/src/paperProbe/java/io/github/augmentwild/probe/PublicArtProbe.java" \
        "$checkout/src/paperProbe/java/io/github/augmentwild/probe/"
    python3 - "$checkout" "$out/$version.jfr" <<'PY'
import sys
from pathlib import Path
root, recording = Path(sys.argv[1]), sys.argv[2]
gradle = root / "build.gradle.kts"
gradle.write_text(gradle.read_text().replace("paper-api:26.3.build.+","paper-api:26.3.build.257-stable"))
path = root / "scripts/test-paper-smoke.sh"
src = path.read_text().replace("pause-when-empty-seconds=-1","pause-when-empty-seconds=-1\\nlevel-seed=67026")
src = src.replace('select(.channel == "STABLE")','select(.channel == "STABLE" and .id == 257)')
src = src.replace('["java", f"-Daugmentwild.pack.fixture=','["java", '
                  +repr("-XX:StartFlightRecording=settings=profile,dumponexit=true,filename="+recording)
                  +', f"-Daugmentwild.pack.fixture=')
src = src.replace('AW_VFX_LOAD_PASS stages=20 total_ticks=960", 150','AW_VFX_REPEAT_PASS stages=25 total_ticks=1200", 420')
src = src.replace('else 30)', 'else 65)')
path.write_text(src)
PY
    status=0
    (
        cd "$checkout"
        ./gradlew --no-daemon assemble paperProbeJar &&
        AW_REPEAT_VFX=1 AW_BORDER_PROBE=1 AW_EXTENDED_LOAD=1 RUNNER_TEMP="$checkout" bash scripts/test-paper-smoke.sh
    ) || status=$?
    if [[ -f "$checkout/augmentwild-paper-smoke/server.log" ]]; then
        cp "$checkout/augmentwild-paper-smoke/server.log" "$out/$version.log"
    fi
    if (( status != 0 )); then exit "$status"; fi
    grep -E 'AW_VFX_REPEAT |AW_VFX_LOAD mode=.*viewers=16' "$out/$version.log"
    jfr summary "$out/$version.jfr" > "$out/$version-jfr.txt"
    jfr print --events jdk.ExecutionSample,jdk.ObjectAllocationSample --stack-depth 10 --json "$out/$version.jfr" > "$out/$version-profile.json"
done
