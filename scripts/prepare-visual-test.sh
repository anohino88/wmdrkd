#!/usr/bin/env bash
# Prepare a local, isolated Paper 26.3 capture server. An administrator starts it after accepting the EULA.
set -euo pipefail
if (( $# < 2 || $# > 3 )); then
    printf 'Usage: %s NEW_DIRECTORY PLUGIN_JAR [DIRECT_PACK_URL]\n' "$0" >&2
    exit 2
fi
root=$(cd -- "$(dirname -- "$0")/.." && pwd)
jar=$(realpath -- "$2")
test -f "$jar"
target=$(realpath -m -- "$1")
temp=$(mktemp -d)
trap 'rm -rf -- "$temp"' EXIT
agent='AugmentWild-visual-test/1.0 (https://github.com/anohino88/wmdrkd)'
curl -fsS --max-time 30 --user-agent "$agent" \
    https://fill.papermc.io/v3/projects/paper/versions/26.3/builds -o "$temp/builds.json"
build=$(jq -cer 'first(.[] | select(.id == 257 and .channel == "STABLE") | .downloads["server:default"])' "$temp/builds.json")
url=$(jq -er '.url' <<<"$build")
sha=$(jq -er '.checksums.sha256' <<<"$build")
[[ "$url" == https://fill-data.papermc.io/* && "$sha" =~ ^[[:xdigit:]]{64}$ ]]
curl -fsS --max-time 180 "$url" -o "$temp/paper.jar"
printf '%s  %s\n' "$sha" "$temp/paper.jar" | sha256sum -c - >&2
options=()
if (( $# == 3 )); then options=(--url "$3"); fi
python3 "$root/scripts/install-pack.py" "${options[@]}" > "$temp/config.yml"
cat >> "$temp/config.yml" <<'YAML'
arena:
  world: world
dev:
  combat-lab:
    enabled: true
    world: visual_lab
    allow-solo: true
    generate-missing-chunks: true
YAML
mkdir -- "$target"
mkdir -p -- "$target/plugins/AugmentWild"
cp -- "$temp/paper.jar" "$target/paper.jar"
cp -- "$jar" "$target/plugins/AugmentWild.jar"
cp -- "$temp/config.yml" "$target/plugins/AugmentWild/config.yml"
cat > "$target/server.properties" <<'PROPERTIES'
server-ip=127.0.0.1
server-port=25565
online-mode=true
view-distance=8
simulation-distance=4
level-seed=67026
PROPERTIES
printf 'Prepared %s. Accept the Minecraft EULA, then start Paper with Java 25.\n' "$target"
