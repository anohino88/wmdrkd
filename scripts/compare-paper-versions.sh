#!/usr/bin/env bash
set -euo pipefail
root=$(cd -- "$(dirname -- "$0")/.." && pwd)
temp=${RUNNER_TEMP:-/tmp}
fixture=$(mktemp -d "$temp/augmentwild-comparison.XXXXXX")
out="$root/build/version-comparison"
mkdir -p "$out"
trap 'rm -rf "$fixture"' EXIT

for version in 8.1 8.2; do
    case "$version" in
        8.1) commit=099a6d668fcbca9f357a49953c2fc49738160b31 ;;
        8.2) commit=$(git -C "$root" rev-parse HEAD) ;;
    esac
    if ! git -C "$root" cat-file -e "$commit^{commit}"; then
        git -C "$root" fetch --no-tags --depth=1 origin "$commit"
    fi
    checkout="$fixture/$version"
    mkdir -p "$checkout"
    git -C "$root" archive "$commit" | tar -x -C "$checkout"
    # Change only the disposable test harness parameters, never the plugin sources.
    python3 - "$checkout" <<'PY'
import sys
from pathlib import Path

root = Path(sys.argv[1])
# Match the API dependency to the server binary in both disposable builds.
gradle = root / "build.gradle.kts"
text = gradle.read_text()
assert "paper-api:26.3.build.+" in text
gradle.write_text(text.replace("paper-api:26.3.build.+","paper-api:26.3.build.257-stable"))

probe = root / "src/paperProbe/java/io/github/augmentwild/probe/SyntheticCombatLoad.java"
text = probe.read_text()
for original, replacement in (
    ("warmup = extended ? 50 : 0", "warmup = 20"),
    ("samples = extended ? 300 : 30", "samples = 100"),
):
    if original not in text:
        raise SystemExit(f"Fixture contract changed for {probe.name}")
    text = text.replace(original, replacement)
probe.write_text(text)
vfx = root / "src/paperProbe/java/io/github/augmentwild/probe/PublicArtProbe.java"
text = vfx.read_text()
assert "WARMUP = 12, MEASURED = 48" in text
text = text.replace("WARMUP = 12, MEASURED = 48", "WARMUP = 32, MEASURED = 96")
text = text.replace("total_ticks=960","total_ticks=1920")
marker = "            java.util.Arrays.sort(timing);"
assert marker in text
text = text.replace(marker, """
            if (size == 16) {
                for (int window = 0; window < 2; window++) {
                    var half = java.util.Arrays.copyOfRange(timing,window * 48,(window + 1) * 48);
                    java.util.Arrays.sort(half);
                    probe.getLogger().info(String.format(java.util.Locale.ROOT,
                            "AW_VFX_WINDOW mode=%s viewers=16 window=%d p95=%.3f",
                            MODES.get(mode),window + 1,half[45]));
                }
            }
""" + marker)
vfx.write_text(text)
smoke = root / "scripts/test-paper-smoke.sh"
text = smoke.read_text().replace("pause-when-empty-seconds=-1", "pause-when-empty-seconds=-1\\nlevel-seed=67026")
text = text.replace("total_ticks=960\", 150", "total_ticks=1920\", 220")
text = text.replace('select(.channel == "STABLE")', 'select(.channel == "STABLE" and .id == 257)')
# Independent servers still use one pinned Paper binary.
text = text.replace('else 30)', 'else 65)')
smoke.write_text(text)
PY
    (
        cd "$checkout"
        ./gradlew --no-daemon assemble paperProbeJar
        AW_BORDER_PROBE=1 AW_EXTENDED_LOAD=1 RUNNER_TEMP="$checkout" bash scripts/test-paper-smoke.sh
    )
    cp "$checkout/augmentwild-paper-smoke/server.log" "$out/$version.log"
    grep -E 'SYNTHETIC_LOAD_TEST|AW_VFX_WINDOW|AW_VFX_LOAD mode=.*viewers=16' "$out/$version.log"
done
