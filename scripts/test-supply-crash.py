#!/usr/bin/env python3
"""Kill a disposable Paper process and verify its markers on two restarts."""
import os
from pathlib import Path
import shutil
import subprocess
import sys
import tempfile
import time

smoke = Path(sys.argv[1]).resolve()
runner = Path(os.environ["RUNNER_TEMP"]).resolve()
assert smoke.parent == runner and (smoke / "paper.jar").is_file()
fixture = Path(tempfile.mkdtemp(prefix="augmentwild-supply-crash-", dir=runner))
shutil.copy2(smoke / "paper.jar", fixture / "paper.jar")
(fixture / "plugins").mkdir()
for jar in (smoke / "plugins").glob("*.jar"):
    shutil.copy2(jar, fixture / "plugins" / jar.name)
(fixture / "eula.txt").write_text("eula=true\n")
(fixture / "server.properties").write_text(
    "level-name=supply-crash-fixture\nlevel-type=minecraft:flat\n"
    "server-ip=127.0.0.1\nserver-port=25565\nview-distance=3\nsimulation-distance=3\n"
)

for phase in ("seed", "restart", "repeat"):
    log = fixture / (phase + ".log")
    expected = "AW_SUPPLY_CRASH_SEEDED" if phase == "seed" else "AW_SUPPLY_CRASH_PASS " + phase
    with log.open("wb") as output:
        with subprocess.Popen(
            ["java", "-Xms512M", "-Xmx1536M", "-jar", "paper.jar", "--nogui"], cwd=fixture,
            stdin=subprocess.PIPE, stdout=output, stderr=subprocess.STDOUT,
            env=dict(os.environ, AW_SUPPLY_CRASH_PHASE=phase)
        ) as server:
            try:
                deadline = time.monotonic() + 140
                while time.monotonic() < deadline:
                    contents = log.read_text(errors="replace")
                    if expected in contents:
                        break
                    if server.poll() is not None or "Supply crash fixture failed" in contents:
                        raise RuntimeError(f"{phase}: Paper fixture failed")
                    time.sleep(.15)
                else:
                    raise RuntimeError(f"{phase}: timed out")
                if phase == "seed":
                    server.kill()
                    if server.wait(15) != -9:
                        raise RuntimeError("Fixture did not exit from SIGKILL")
                    print("::notice::Supply fixture received SIGKILL", flush=True)
                else:
                    server.stdin.write(b"stop\n")
                    server.stdin.flush()
                    if server.wait(45) != 0:
                        raise RuntimeError(f"{phase}: Paper stop failed")
            except Exception:
                print(log.read_text(errors="replace")[-6500:], flush=True)
                error = next((line for line in log.read_text(errors="replace").splitlines()
                              if "AssertionError" in line or "Supply crash fixture failed" in line), phase)
                error = error.replace("%", "%25").replace("\r", "%0D").replace("\n", "%0A")
                print("::error::Supply crash probe " + error, flush=True)
                raise
            finally:
                if server.poll() is None:
                    server.kill()
                    server.wait()
print("::notice::AW_SUPPLY_CRASH_REAL_PASS", flush=True)
