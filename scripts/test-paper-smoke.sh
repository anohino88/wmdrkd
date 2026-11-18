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
printf 'server-ip=127.0.0.1\nserver-port=25565\nview-distance=3\nsimulation-distance=3\n' > server.properties
python3 - <<'PY'
import pathlib
import subprocess
import time

log = pathlib.Path("server.log")
with log.open("wb") as output:
    with subprocess.Popen(["java", "-Xms512M", "-Xmx1536M", "-jar", "paper.jar", "--nogui"],
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

        try:
            wait_for('Done (', 140)
            wait_for('Enabling AugmentWild', 5)
            server.stdin.write(b"aw status\n")
            server.stdin.flush()
            wait_for('경기 단계 WAITING', 15)
            server.stdin.write(b"stop\n")
            server.stdin.flush()
            if server.wait(timeout=30) != 0:
                raise RuntimeError("Paper exited with an error")
            wait_for('Disabling AugmentWild', 1)
        finally:
            if server.poll() is None:
                server.kill()
                server.wait()
PY
