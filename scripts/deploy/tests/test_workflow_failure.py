"""Execute real workflow shells with fake Git/Docker/Python; no network or VPS."""
import os
from pathlib import Path
import re
import shutil
import subprocess
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[3]


@unittest.skipUnless(os.name == "posix" and shutil.which("bash") and shutil.which("flock"), "Linux workflow shell contract")
class WorkflowFailureTest(unittest.TestCase):
    def test_each_vps_job_propagates_fetch_and_application_failures(self):
        lines = (ROOT / ".github/workflows/cd-deploy.yml").read_text().splitlines()
        scripts = []
        for index, line in enumerate(lines):
            if line == "          script: |":
                block = []
                for row in lines[index + 1:]:
                    if row and not row.startswith("            "):
                        break
                    block.append(row[12:] if row else "")
                scripts.append("\n".join(block))
        self.assertEqual(3, len(scripts))
        for script in scripts:
            for failure in ("fetch", "application"):
                with self.subTest(failure=failure, node=script.split("--node ")[-1].split()[0]), tempfile.TemporaryDirectory(prefix="sph-workflow-") as folder:
                    root = Path(folder)
                    (root / "smartparking/.git").mkdir(parents=True)
                    binaries = root / "bin"
                    binaries.mkdir()
                    commands = {
                        "git": "case \"$*\" in *rev-parse*) printf '%s\\n' \"$SPH_SOURCE_SHA\";; *fetch*) test \"$FAILURE\" != fetch || exit 23;; esac\nexit 0\n",
                        "docker": "cat >/dev/null\nexit 0\n",
                        "python3": "echo SIMULATED_APPLICATION_FAILURE >&2\nexit 24\n",
                    }
                    for name, body in commands.items():
                        path = binaries / name
                        path.write_text("#!/bin/sh\n" + body)
                        path.chmod(0o700)
                    shell = re.sub(r"\$\{\{.*?\}\}", "TEST_PLACEHOLDER", script)
                    shell = shell.replace("\nSPH_DEPLOY", "\necho FALSE_SUCCESS\nSPH_DEPLOY")
                    result = subprocess.run(["bash"], input=shell, text=True, capture_output=True, timeout=15,
                                            env={**os.environ, "HOME": folder, "PATH": str(binaries) + os.pathsep + os.environ["PATH"], "SPH_SOURCE_SHA": "a" * 40, "SPH_RELEASE_JSON": "{}", "FAILURE": failure})
                    self.assertEqual(23 if failure == "fetch" else 24, result.returncode, result.stdout + result.stderr)
                    self.assertNotIn("FALSE_SUCCESS", result.stdout)


if __name__ == "__main__":
    unittest.main()
