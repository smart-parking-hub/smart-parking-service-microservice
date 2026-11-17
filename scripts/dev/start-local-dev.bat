@echo off
setlocal
echo ==========================================================
echo  [SmartParkingHub] Starting Local Dev Profile
echo ==========================================================

cd /d "%~dp0..\.."
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0sync-all.ps1" -Mode "local-dev" %*
if %ERRORLEVEL% NEQ 0 (
    echo.
    echo [ERROR] Khoi dong Local Dev that bai.
)
pause
