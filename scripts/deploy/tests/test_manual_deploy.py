"""Actual PowerShell failure propagation with every external operation mocked."""
import os
import json
import tempfile
from pathlib import Path
import shutil
import subprocess
import unittest

ROOT = Path(__file__).resolve().parents[3]
POWERSHELL = shutil.which("powershell") or shutil.which("pwsh")


@unittest.skipUnless(POWERSHELL and os.name == "nt", "Windows PowerShell native runner")
class ManualDeployTest(unittest.TestCase):
    def test_desktop_startup_ready_missing_and_timeout(self):
        script = r'''
$ErrorActionPreference='Stop'
. $env:SPH_DESKTOP_HELPER
$global:started=$false
function global:docker {
    $global:LASTEXITCODE=0
    if ($env:SPH_SCENARIO -eq 'ready' -or ($env:SPH_SCENARIO -eq 'start' -and $global:started)) { return 'linux' }
    return 'windows'
}
function global:Start-Process {
    param([string]$FilePath,[string]$WindowStyle)
    if ($WindowStyle -ne 'Hidden') { throw 'Desktop must start hidden' }
    $global:started=$true
    Write-Output 'MOCK_DESKTOP_START'
}
try { Ensure-LocalLinuxDocker -TimeoutSeconds 1 -DesktopPath $env:SPH_DESKTOP_EXE; Write-Output 'ENGINE_READY'; exit 0 }
catch { Write-Output $_.Exception.Message; exit 1 }
'''
        with tempfile.TemporaryDirectory(prefix="sph-engine-test-") as folder:
            exe = Path(folder) / "Docker Desktop.exe"
            exe.write_bytes(b"fake; process launcher is mocked")
            for scenario, code in (("ready", 0), ("start", 0), ("missing", 1), ("timeout", 1)):
                with self.subTest(scenario=scenario):
                    env = {**os.environ, "SPH_DESKTOP_HELPER": str(ROOT / "scripts/deploy/DockerDesktop.ps1"),
                           "SPH_SCENARIO": scenario, "SPH_DESKTOP_EXE": str(exe if scenario != "missing" else exe.with_name("absent.exe"))}
                    result = subprocess.run([POWERSHELL, "-NoProfile", "-NonInteractive", "-Command", script], env=env, capture_output=True, timeout=15)
                    self.assertEqual(code, result.returncode, result.stdout.decode(errors="replace") + result.stderr.decode(errors="replace"))
                    self.assertEqual(scenario in ("start", "timeout"), b"MOCK_DESKTOP_START" in result.stdout)
                    if scenario == "timeout":
                        self.assertIn(b"timed out", result.stdout)

    def test_local_build_modes_and_failures(self):
        for scenario, code in (("local-ready", 0), ("local-start", 0), ("local-build-failure", 1), ("local-push-failure", 1), ("local-source-changed", 1)):
            with self.subTest(scenario=scenario), tempfile.TemporaryDirectory(prefix="sph-desktop-test-") as folder:
                root = Path(folder)
                desktop = root / "Programs/DockerDesktop/Docker Desktop.exe"
                desktop.parent.mkdir(parents=True)
                desktop.write_bytes(b"fake executable; Start-Process must be mocked")
                log = root / "operations.json"
                env = {**os.environ, "LOCALAPPDATA": folder, "SPH_TEST_OPS_PATH": str(log)}
                result = subprocess.run([POWERSHELL, "-NoProfile", "-NonInteractive", "-ExecutionPolicy", "Bypass", "-File", str(Path(__file__).with_name("mock-deploy.ps1")), "-Runner", str(ROOT / "scripts/deploy/deploy-vps.ps1"), "-Scenario", scenario], cwd=ROOT, env=env, capture_output=True, timeout=25)
                self.assertEqual(code, result.returncode, result.stdout.decode(errors="replace")[-1000:] + result.stderr.decode(errors="replace")[-500:])
                ops = json.loads(log.read_text(encoding="utf-8-sig"))
                if code:
                    self.assertNotIn("REMOTE_ACTIVATE", ops)
                else:
                    self.assertIn("REMOTE_ACTIVATE", ops)
                self.assertEqual(scenario != "local-ready", "DESKTOP Hidden" in ops)
                builds = [op for op in ops if op.startswith("DOCKER build")]
                self.assertTrue(builds)
                self.assertTrue(all("--platform linux/amd64" in op and ":local-" in op and ":latest" not in op and ":sha-" not in op for op in builds))

    def test_ci_artifact_deploy_does_not_require_local_docker(self):
        result = subprocess.run([POWERSHELL, "-NoProfile", "-NonInteractive", "-ExecutionPolicy", "Bypass", "-File", str(Path(__file__).with_name("mock-deploy.ps1")), "-Runner", str(ROOT / "scripts/deploy/deploy-vps.ps1"), "-Scenario", "success"], cwd=ROOT, capture_output=True, timeout=25)
        self.assertEqual(0, result.returncode, result.stdout.decode(errors="replace")[-1600:])
        self.assertIn(b"[SUCCESS] Every requested", result.stdout)

    def test_failed_remote_and_partial_ssh_are_not_success(self):
        for scenario, code in (("remote-failure", 1), ("partial-ssh", 1), ("dirty", 1), ("stale", 1), ("local-skip", 1), ("success", 0)):
            with self.subTest(scenario=scenario):
                result = subprocess.run([POWERSHELL, "-NoProfile", "-NonInteractive", "-ExecutionPolicy", "Bypass", "-File", str(Path(__file__).with_name("mock-deploy.ps1")), "-Runner", str(ROOT / "scripts/deploy/deploy-vps.ps1"), "-Scenario", scenario], cwd=ROOT, capture_output=True, timeout=25)
                self.assertEqual(code, result.returncode, result.stdout.decode(errors="replace")[-1200:] + result.stderr.decode(errors="replace")[-800:])
                if code:
                    self.assertNotIn(b"[SUCCESS] Every requested", result.stdout)


if __name__ == "__main__":
    unittest.main()
