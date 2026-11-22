"""Drive the full-resolution scan on Paper using its console and an isolated flat world."""

import os
import re
import time
import statistics


def benchmark_arena(command, log, wait_for):
    command("setworldspawn 0 -60 0")
    points = []
    for x in range(-512, 512, 256):
        for z in range(-512, 512, 256):
            command(f"forceload add {x} {z} {x + 255} {z + 255}")
            points.extend([(x, z), (x + 255, z + 255)])

    deadline = time.monotonic() + 120
    while time.monotonic() < deadline:
        for index, (x, z) in enumerate(points):
            command(f"execute if loaded {x} -60 {z} run say AW_CHUNK_READY_{index}_END")
        time.sleep(2)
        output = log.read_text(encoding="utf-8", errors="replace")
        if all(f"AW_CHUNK_READY_{index}_END" in output for index in range(len(points))):
            break
    else:
        raise RuntimeError("Flat benchmark fixture did not finish loading")

    # Same flat seed, geometry (the legacy origin is within one block), loaded fixture, Paper and host.
    time.sleep(3)
    policy = os.environ.get("AW_ARENA_POLICY", "MAX_RATIO")
    matches = []
    requested_step, expected_samples = (1,1_000_000) if policy == "ZERO" else (32,1_024)
    if policy != "ZERO":
        command("worldborder set 500")
        command("aw admin arena preview")
        wait_for("경기장 배치 불가능", 10)
        if "후보 검사 한도" in log.read_text(encoding="utf-8", errors="replace"):
            raise RuntimeError("undersized world border should fail before candidates")
        command("worldborder set 1000")
        command("aw admin arena check")
        wait_for("배치 가능 중심 X 0.0 ~ 0.0", 10)
    command("worldborder set 5000")
    for trial in range(2):
        start = log.stat().st_size
        command("aw admin arena preview")
        pattern = (r"Arena search FOUND: size=1000x1000 step=" + str(requested_step) + r" .*finalSamples="
                   + str(expected_samples) + r" .*chunkRequests=(\d+) peakPending=(\d+) elapsedMs=(\d+) maxTickMs=([0-9.]+).*")
        deadline = time.monotonic() + 135
        while time.monotonic() < deadline:
            with log.open("rb") as server_log:
                server_log.seek(start)
                new = server_log.read().decode("utf-8", errors="replace")
            match = re.search(pattern,new)
            if match:
                break
            if "Arena search FAILED" in new:
                raise RuntimeError(new)
            time.sleep(1)
        else:
            raise RuntimeError(f"{policy} arena scan did not produce {expected_samples} samples: {new}")
        requests, peak, elapsed = map(int, match.groups()[:3])
        if not (3900 <= requests <= 4100 if policy == "ZERO" else 900 <= requests <= 1100):
            raise RuntimeError(f"Unexpected number of chunk requests: {match[0]}")
        if not 1 <= peak <= 8 or elapsed >= 120_000:
            raise RuntimeError(f"Arena scan exceeded configured limits: {match[0]}")
        matches.append((requests,elapsed,float(match[4])))
        print(f"AW_ARENA_TRIAL {policy} {trial + 1} {match[0]}", flush=True)
    command("forceload remove all")
    if policy != "ZERO":
        command("worldborder set 2000")
        command("worldborder center 50000 50000")
        command("aw admin arena preview")
        wait_for("스폰 주변 탐색 범위와 보더 내부 유효 범위가 겹치지 않습니다",10)
        command("setworldspawn 50000 -60 50000")
        command("aw admin arena preview")
        wait_for("미생성 또는 로드 불가 청크 (생성 금지)",25)
    summary = (f"AW_ARENA_COMPARE policy={policy} successes={len(matches)}/2 "
               f"mean_ms={statistics.mean(row[1] for row in matches):.0f} "
               f"max_ms={max(row[1] for row in matches)} requests={statistics.mean(row[0] for row in matches):.0f} "
               f"max_tick_ms={max(row[2] for row in matches):.2f}")
    print(f"::notice::{summary}", flush=True)
    if report_path := os.environ.get("GITHUB_STEP_SUMMARY"):
        with open(report_path, "a", encoding="utf-8") as report:
            report.write("\n```text\n" + summary + "\n```\n")
