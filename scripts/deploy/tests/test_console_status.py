"""Console-only feedback: no real build, Docker, registry or SSH operations."""
import os
from pathlib import Path
import shutil
import subprocess
import unittest

ROOT = Path(__file__).resolve().parents[3]
POWERSHELL = shutil.which("powershell") or shutil.which("pwsh")


@unittest.skipUnless(POWERSHELL and os.name == "nt", "Windows PowerShell console contract")
class ConsoleStatusTest(unittest.TestCase):
    def run_script(self, script, stdin=None):
        return subprocess.run([POWERSHELL, "-NoProfile", "-NonInteractive", "-Command",
                               "$ErrorActionPreference='Stop'; . $env:SPH_STATUS_HELPER; " + script],
                              env={**os.environ, "SPH_STATUS_HELPER": str(ROOT / "scripts/deploy/ConsoleStatus.ps1")},
                              input=stdin, capture_output=True, timeout=15)

    def test_silent_action_has_heartbeat_and_preserves_json_and_failure_code(self):
        result = self.run_script(r'''
$payload = Invoke-ConsoleStep -Label 'MOCK_SILENT' -IntervalMilliseconds 100 -Action {
    [Threading.Thread]::Sleep(650)
    $global:LASTEXITCODE=7
    '{"value":42}'
}
$code=$LASTEXITCODE
if (($payload | ConvertFrom-Json).value -ne 42 -or $code -ne 7) { throw 'Output or exit code changed' }
Write-Output 'PRESERVED_JSON_AND_EXIT'
''')
        self.assertEqual(0, result.returncode, result.stderr.decode(errors="replace"))
        self.assertIn(b"[DANG CHAY] MOCK_SILENT", result.stdout)
        self.assertIn(b"[DANG CHAY] MOCK_SILENT", result.stderr)
        self.assertIn(b"PRESERVED_JSON_AND_EXIT", result.stdout)
        self.assertNotIn(b"[SUCCESS]", result.stdout)

    def test_real_native_exit_code_is_preserved_between_steps(self):
        result = self.run_script(r'''
Invoke-ConsoleStep -Label 'MOCK_NATIVE_FAIL' -Action { & $env:ComSpec /d /c exit 9 }
if ($LASTEXITCODE -ne 9) { throw 'Native failure was hidden' }
Invoke-ConsoleStep -Label 'MOCK_NATIVE_OK' -Action { & $env:ComSpec /d /c exit 0 }
if ($LASTEXITCODE -ne 0) { throw 'Native success was changed' }
Write-Output 'NATIVE_CODES_PRESERVED'
''')
        self.assertEqual(0, result.returncode, result.stderr.decode(errors="replace"))
        self.assertIn(b"NATIVE_CODES_PRESERVED", result.stdout)

    def test_exception_is_not_swallowed_and_heartbeat_finishes(self):
        result = self.run_script(r'''
try { Invoke-ConsoleStep -Label 'MOCK_THROW' -IntervalMilliseconds 100 -Action {
    [Threading.Thread]::Sleep(300)
    throw 'EXPECTED_FAILURE'
}; throw 'Failure was swallowed' }
catch { if ($_.Exception.Message -ne 'EXPECTED_FAILURE') { throw }; Write-Output 'EXPECTED_CAUGHT' }
''')
        self.assertEqual(0, result.returncode, result.stderr.decode(errors="replace"))
        self.assertIn(b"EXPECTED_CAUGHT", result.stdout)
        self.assertIn(b"[KET THUC BUOC] MOCK_THROW", result.stdout)

    def test_redirected_input_is_not_consumed(self):
        result = self.run_script(r'''
Invoke-ConsoleStep -Label 'MOCK_INPUT' -Action { $global:LASTEXITCODE=0 }
if ([Console]::ReadLine() -ne 'KEEP_THIS_LINE') { throw 'Redirected stdin was consumed' }
Write-Output 'INPUT_PRESERVED'
''', b"KEEP_THIS_LINE\r\n")
        self.assertEqual(0, result.returncode, result.stderr.decode(errors="replace"))
        self.assertIn(b"INPUT_PRESERVED", result.stdout)


if __name__ == "__main__":
    unittest.main()
