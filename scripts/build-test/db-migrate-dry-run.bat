@echo off
setlocal
echo ==========================================================
echo  [SmartParkingHub] Migration File Readability Check
echo ==========================================================

cd /d "%~dp0..\.."

echo Checking that canonical Flyway SQL files exist and can be read...
echo This does not validate SQL syntax, Flyway checksums or MySQL upgrade behavior.
powershell -NoProfile -Command "$ErrorActionPreference='Stop'; $files=@(Get-ChildItem -Path .\*\src\main\resources\db\migration\*.sql); if ($files.Count -eq 0) { throw 'No canonical migrations found' }; $files | ForEach-Object { Get-Content -LiteralPath $_.FullName -ErrorAction Stop | Out-Null }; Write-Host '[SUCCESS] Migration files are readable. Run the documented isolated MySQL migration tests for schema verification.'"
set "MIGRATION_CHECK_EXIT=%ERRORLEVEL%"

pause
exit /b %MIGRATION_CHECK_EXIT%
