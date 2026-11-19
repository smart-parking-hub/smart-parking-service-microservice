"""Windows menu and deploy contracts, using isolated scripts with no real SSH/Docker."""
import json
import os
from pathlib import Path
import shutil
import subprocess
import tempfile
import time
import unittest

ROOT = Path(__file__).resolve().parents[3]
POWERSHELL = shutil.which("powershell")


@unittest.skipUnless(os.name == "nt" and POWERSHELL, "Windows batch contract")
class BatchTest(unittest.TestCase):
    def test_cancel_real_deploy_submenu_returns_to_main_without_running_another_tool(self):
        for replies in (b"1.7\r\n0\r\n0\r\n", b"1.7\r\n2\r\n0\r\n0\r\n", b"1.7\r\n1\r\n0\r\n0\r\n"):
            with self.subTest(replies=replies), tempfile.TemporaryDirectory(prefix="sph menu cancel ") as folder:
                root = Path(folder)
                shutil.copyfile(ROOT / "menu.bat", root / "menu.bat")
                deploy = root / "scripts/deploy"
                deploy.mkdir(parents=True)
                shutil.copyfile(ROOT / "scripts/deploy/deploy-vps.bat", deploy / "deploy-vps.bat")
                (deploy / "deploy-vps.ps1").write_text("throw 'Unexpected deploy invocation'\n")
                ports = root / "scripts/dev/kill-ports.bat"
                ports.parent.mkdir(parents=True)
                ports.write_bytes(b"@echo off\r\necho UNEXPECTED_PORT_TOOL\r\nexit 91\r\n")
                answers = root / "answers.txt"
                answers.write_bytes(replies)
                with answers.open("rb") as stdin:
                    result = subprocess.run(["cmd", "/d", "/c", str(root / "menu.bat")], stdin=stdin, capture_output=True, timeout=15)
                self.assertEqual(0, result.returncode, result.stdout.decode(errors="replace")[-1500:] + result.stderr.decode(errors="replace"))
                self.assertNotIn(b"UNEXPECTED", result.stdout + result.stderr)
                self.assertNotIn(b"is not recognized", result.stderr)
                self.assertGreaterEqual(result.stdout.count(b"TRUNG TAM DIEU PHOI"), 2)

    def test_editing_source_while_child_runs_cannot_resume_in_a_different_action(self):
        with tempfile.TemporaryDirectory(prefix="sph menu live edit ") as folder:
            root = Path(folder)
            menu = root / "menu.bat"
            source = (ROOT / "menu.bat").read_bytes()
            menu.write_bytes(source)
            deploy = root / "scripts/deploy/deploy-vps.bat"
            deploy.parent.mkdir(parents=True)
            deploy.write_bytes(b'@echo off\r\necho WAITING>"%SPH_TEST_WAITING%"\r\n:WAIT\r\nif exist "%SPH_TEST_RELEASE%" exit /b 0\r\npowershell -NoProfile -Command "Start-Sleep -Milliseconds 100"\r\ngoto WAIT\r\n')
            ports = root / "scripts/dev/kill-ports.bat"
            ports.parent.mkdir(parents=True)
            ports.write_bytes(b"@echo off\r\necho UNEXPECTED_PORT_TOOL\r\nexit 91\r\n")
            waiting, release = root / "waiting", root / "release"
            answers = root / "answers.txt"
            answers.write_bytes(b"1.7\r\n0\r\n")
            with answers.open("rb") as stdin:
                process = subprocess.Popen(["cmd", "/d", "/c", str(menu)], stdin=stdin, stdout=subprocess.PIPE, stderr=subprocess.PIPE,
                                           env={**os.environ, "SPH_TEST_WAITING": str(waiting), "SPH_TEST_RELEASE": str(release)})
                try:
                    deadline = time.monotonic() + 10
                    while not waiting.exists() and process.poll() is None and time.monotonic() < deadline:
                        time.sleep(0.02)
                    self.assertTrue(waiting.exists(), "Child tool was not reached")
                    # CMD stores the caller's byte offset while CALL is pending.
                    # Replace the source at that offset with harmless poison,
                    # as an editor changing menu length can do accidentally.
                    needle = b'call "%~dp0scripts\\deploy\\deploy-vps.bat"'
                    position = source.index(needle)
                    resume = source.index(b"\n", position) + 1
                    menu.write_bytes(source[:resume] + b"echo UNEXPECTED_STALE_RETURN\r\nexit /b 91\r\n" + source[resume:])
                    release.write_text("continue")
                    stdout, stderr = process.communicate(timeout=15)
                finally:
                    if process.poll() is None:
                        process.kill()
                        process.communicate()
                self.assertEqual(0, process.returncode, stdout.decode(errors="replace")[-1500:] + stderr.decode(errors="replace"))
                self.assertNotIn(b"UNEXPECTED", stdout + stderr)

    def test_blank_enter_does_not_repeat_the_previous_menu_action(self):
        with tempfile.TemporaryDirectory(prefix="sph-menu-input-") as folder:
            root = Path(folder)
            shutil.copyfile(ROOT / "menu.bat", root / "menu.bat")
            stub = root / "scripts/build-test/build-all.bat"
            stub.parent.mkdir(parents=True)
            record = root / "calls.txt"
            stub.write_bytes(b'@echo off\r\necho CALLED>>"%SPH_TEST_RECORD%"\r\nexit /b 0\r\n')
            answers = root / "answers.txt"
            answers.write_bytes(b"2.1\r\n\r\n0\r\n")
            with answers.open("rb") as stdin:
                result = subprocess.run(["cmd", "/d", "/c", str(root / "menu.bat")], stdin=stdin,
                                        env={**os.environ, "SPH_TEST_RECORD": str(record)}, capture_output=True, timeout=15)
            self.assertEqual(0, result.returncode)
            self.assertEqual(["CALLED"], record.read_text().splitlines())
            self.assertIn(b"[DANG XU LY] 2.1", result.stdout)

    def test_single_service_prompt_and_failure_exit(self):
        with tempfile.TemporaryDirectory(prefix="sph-batch-test-") as folder:
            root = Path(folder)
            deploy = root / "scripts/deploy"
            deploy.mkdir(parents=True)
            shutil.copyfile(ROOT / "scripts/deploy/deploy-vps.bat", deploy / "deploy-vps.bat")
            (deploy / "deploy-vps.ps1").write_text('param([string]$Service,[string]$Source)\n@{service=$Service;source=$Source} | ConvertTo-Json | Set-Content $env:SPH_TEST_RECORD\nexit 7\n')
            env = {k: v for k, v in os.environ.items() if k.lower() not in ("svcname", "choice")}
            record = root / "args.json"
            env["SPH_TEST_RECORD"] = str(record)
            answer = root / "input.txt"
            for choice, expected in (("1", "Local"), ("2", "CI"), ("", "CI")):
                with self.subTest(source=choice):
                    answer.write_bytes((choice + "\r\n5\r\npayment-service\r\n\r\n").encode())
                    with answer.open("rb") as stdin:
                        result = subprocess.run(["cmd", "/d", "/c", str(deploy / "deploy-vps.bat")], stdin=stdin, env=env, capture_output=True, timeout=15)
                    self.assertEqual(7, result.returncode)
                    args = json.loads(record.read_text(encoding="utf-8-sig"))
                    self.assertEqual("payment-service", args["service"])
                    self.assertEqual(expected, args["source"])

    def test_new_menu_items_route_to_the_expected_tools(self):
        for option, target in (("1.7", "scripts/deploy/deploy-vps.bat"), ("1.9", "scripts/deploy/verify-vps-release.bat")):
            with self.subTest(option=option), tempfile.TemporaryDirectory(prefix="sph-menu-test-") as folder:
                root = Path(folder)
                shutil.copyfile(ROOT / "menu.bat", root / "menu.bat")
                stub = root / target
                stub.parent.mkdir(parents=True)
                stub.write_bytes(b"@echo off\r\necho ROUTED_EXPECTED_TOOL\r\nexit 77\r\n")
                result = subprocess.run(["cmd", "/d", "/c", str(root / "menu.bat")], input=(option + "\r\n").encode(), capture_output=True, timeout=15)
                self.assertEqual(77, result.returncode)
                self.assertIn(b"ROUTED_EXPECTED_TOOL", result.stdout)
                self.assertIn(("[DANG XU LY] " + option).encode(), result.stdout)


if __name__ == "__main__":
    unittest.main()
