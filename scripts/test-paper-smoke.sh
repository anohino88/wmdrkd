#!/usr/bin/env bash
set -euo pipefail
root=$(cd -- "$(dirname -- "$0")/.." && pwd)
shopt -s nullglob
jars=("$root"/build/libs/*.jar)
test "${#jars[@]}" -eq 1
target="${RUNNER_TEMP:-/tmp}/augmentwild-paper-smoke"
mkdir -p "$target/plugins"
cd "$target"
cp "${jars[0]}" plugins/
if [[ "${AW_BORDER_PROBE:-0}" == 1 ]]; then
    cp "$root/build/paper-probe/augmentwild-paper-probe.jar" plugins/
fi
agent='AugmentWild-CI/1.0 (https://github.com/anohino88/wmdrkd)'
curl --fail --silent --show-error --max-time 30 --user-agent "$agent" \
    https://fill.papermc.io/v3/projects/paper/versions/26.3/builds -o builds.json
url=$(jq -er 'first(.[] | select(.channel == "STABLE") | .downloads["server:default"].url)' builds.json)
sha=$(jq -er 'first(.[] | select(.channel == "STABLE") | .downloads["server:default"].checksums.sha256)' builds.json)
[[ "$url" == https://fill-data.papermc.io/* && "$sha" =~ ^[0-9a-f]{64}$ ]]
curl --fail --silent --show-error --max-time 60 "$url" -o paper.jar
printf '%s  paper.jar\n' "$sha" | sha256sum -c -
# Disposable CI server only.
printf 'eula=true\n' > eula.txt
printf 'server-ip=127.0.0.1\nserver-port=25565\nview-distance=3\nsimulation-distance=3\npause-when-empty-seconds=-1\n' > server.properties
if [[ "${AW_ARENA_BENCHMARK:-0}" == 1 ]]; then
    printf 'level-type=minecraft:flat\n' >> server.properties
    mkdir -p plugins/AugmentWild
    cat > plugins/AugmentWild/config.yml <<'YAML'
arena:
  world: world
  search:
    radius: 1
    max-candidate-attempts: 1
    surface-sample-step: 1
dev:
  combat-lab:
    enabled: true
    world: fixture_lab_unloaded
YAML
fi
python3 - "$root" <<'PY'
import os
import pathlib
import runpy
import sys
import subprocess
import time

benchmark = os.environ.get("AW_ARENA_BENCHMARK") == "1"
log = pathlib.Path("server.log")
with log.open("wb") as output:
    with subprocess.Popen(["java", "-Xms512M", "-Xmx4096M" if benchmark else "-Xmx1536M", "-jar", "paper.jar", "--nogui"],
                          stdin=subprocess.PIPE, stdout=output, stderr=subprocess.STDOUT) as server:
        def wait_for(marker, seconds):
            deadline = time.monotonic() + seconds
            while time.monotonic() < deadline:
                if marker in log.read_text(encoding="utf-8", errors="replace"):
                    return
                if server.poll() is not None:
                    break
                time.sleep(1)
            raise RuntimeError(f"Paper smoke marker missing: {marker}")

        def command(text):
            server.stdin.write((text + "\n").encode("utf-8"))
            server.stdin.flush()

        try:
            wait_for('Done (', 140)
            wait_for('Enabling AugmentWild', 5)
            command("aw status")
            wait_for('경기 단계 WAITING', 15)
            command("aw augments")
            wait_for("플레이어 전용 명령입니다", 15)
            command("aw admin test status")
            if benchmark:
                wait_for("전투 실험실 설정됨 (fixture_lab_unloaded)", 15)
                command("aw admin test presets")
                wait_for("실험 프리셋 [calamity_train, hell_lord, infinite_barrage, life_echo, steel_king, thousand_swords]", 15)
                command("aw admin test preset Missing steel_king")
                wait_for("진행 중이며 일시정지 상태가 아닌 테스트 경기에서만", 15)
                command("aw admin test start")
                wait_for("참가자가 최소 2명 필요합니다", 15)
            else:
                wait_for("전투 실험실 사용 불가", 15)
                command("aw admin test start")
                wait_for("운영 월드와 다른 기존 테스트 월드를 지정하세요", 15)
            if os.environ.get("AW_BORDER_PROBE") == "1":
                wait_for("관리자 권한이 없습니다", 15)
                wait_for("AW_PHASE68_MENU_PASS", 15)
                wait_for("AW_ART_PROBE_PASS", 15)
                wait_for("AW_PUBLIC_ART_PASS", 15)
                wait_for("AW_ART_SUSTAIN ticks=1200 created=1 remaining=0", 20)
                wait_for("AW_ART_LIFECYCLE_PASS models=84 variable_timing=true remaining=0", 15)
                for size in (2, 4, 8, 16):
                    wait_for(f"AW_HUD_LOAD_PASS synthetic={size} ticks=40 scans={8*size}", 15)
                wait_for("AW_BORDER_PROBE_PASS", 15)
                wait_for("AW_AUGMENT_PROBE_PASS", 15)
                wait_for("AW_RESONANCE_PROBE_PASS", 15)
                wait_for("AW_COMBAT_LOAD_PASS", 270 if os.environ.get("AW_EXTENDED_LOAD") == "1" else 30)
                wait_for("AW_PHASE64_PROBE_PASS", 15)
            if benchmark:
                commands = runpy.run_path(str(pathlib.Path(sys.argv[1]) / "scripts/paper-arena-benchmark.py"))
                commands["benchmark_arena"](command, log, wait_for)
            command("stop")
            if server.wait(timeout=30) != 0:
                raise RuntimeError("Paper exited with an error")
            wait_for('Disabling AugmentWild', 1)
        except Exception as error:
            lines = log.read_text(encoding="utf-8", errors="replace").splitlines()
            diagnostic = next((line for line in reversed(lines)
                               if "Arena search" in line or "Exception" in line or "Error" in line), "")
            message = f"{error}. {diagnostic}".replace("%", "%25").replace("\r", "%0D").replace("\n", "%0A")
            print(f"::error::{message}", flush=True)
            if summary := os.environ.get("GITHUB_STEP_SUMMARY"):
                with open(summary, "a", encoding="utf-8") as report:
                    report.write(f"\nPaper check failed: {error}\n\n" + "```text\n"
                                 + "\n".join(lines[-25:]) + "\n```\n")
            raise
        finally:
            if server.poll() is None:
                server.kill()
                server.wait()
PY
