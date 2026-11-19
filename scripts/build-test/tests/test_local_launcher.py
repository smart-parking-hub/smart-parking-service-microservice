"""Exercise the Windows runner with all mutation commands replaced by mocks."""

import json
from pathlib import Path
import shutil
import subprocess
import tempfile
import unittest


ROOT = Path(__file__).resolve().parents[3]
POWERSHELL = shutil.which("powershell") or shutil.which("pwsh")


@unittest.skipUnless(POWERSHELL, "PowerShell is required for the Windows launcher mock test")
class LocalLauncherTest(unittest.TestCase):
    def test_existing_and_integration_command_plans(self):
        with tempfile.TemporaryDirectory(prefix="sph-launcher-test-") as folder:
            repo = Path(folder)
            runner = repo / "scripts/dev/sync-all.ps1"
            runner.parent.mkdir(parents=True)
            shutil.copyfile(ROOT / "scripts/dev/sync-all.ps1", runner)
            (repo / ".env").write_text("", encoding="utf-8")
            harness = Path(__file__).with_name("mock-local-launcher.ps1")
            result = subprocess.run([POWERSHELL, "-NoProfile", "-NonInteractive", "-ExecutionPolicy", "Bypass", "-File", str(harness), "-Runner", str(runner)], capture_output=True, timeout=30)
            self.assertEqual(0, result.returncode, "Mocked launcher failed; no real container command was allowed")
            plans = json.loads(result.stdout)
        base = "/c docker compose -p docker --project-directory . -f docker\\compose\\local\\compose.yaml -f docker\\compose\\local\\compose.local.yaml"
        apps = "api-gateway identity-service parking-lot-service booking-service payment-service notification-service ai-service"
        for mode, suffix, infra in (
            ("local-dev", "", "mysql redis"),
            ("local-full", " -f docker\\compose\\local\\compose.local-full.yaml", "mysql redis kafka"),
            ("local-integration", " -f docker\\compose\\local\\compose.kafka.yaml", "mysql redis kafka"),
        ):
            self.assertEqual([base + suffix + " up -d " + infra, base + suffix + " up -d --build --force-recreate " + apps], plans[mode])
        self.assertEqual("/c .\\gradlew.bat :payment-service:bootJar -x test", plans["payment"][1])
        self.assertEqual(base + " -f docker\\compose\\local\\compose.kafka.yaml up -d --build --force-recreate payment-service", plans["payment"][2])


if __name__ == "__main__":
    unittest.main()
