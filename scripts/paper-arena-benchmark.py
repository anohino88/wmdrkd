"""Drive the full-resolution scan on Paper using its console and an isolated flat world."""

import os
import re
import time


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

    command("forceload remove all")
    command("save-all flush")
    command("aw admin arena preview")
    wait_for("Arena search ", 135)
    output = log.read_text(encoding="utf-8", errors="replace")
    match = re.search(
        r"Arena search FOUND: size=1000x1000 step=1 .*finalSamples=1000000"
        r" .*chunkRequests=(\d+) peakPending=(\d+) elapsedMs=(\d+).*",
        output,
    )
    if not match:
        raise RuntimeError("Full 1,000,000-column arena scan did not finish successfully")
    requests, peak, elapsed = map(int, match.groups())
    if requests < 3900 or not 1 <= peak <= 8 or elapsed >= 120_000:
        raise RuntimeError(f"Arena scan exceeded configured limits: {match[0]}")
    print(match[0], flush=True)
    if summary := os.environ.get("GITHUB_STEP_SUMMARY"):
        with open(summary, "a", encoding="utf-8") as report:
            report.write("\n```text\n" + match[0] + "\n```\n")
