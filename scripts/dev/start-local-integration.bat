@echo off
setlocal
echo ==========================================================
echo  [SmartParkingHub] Local Integration: Apps + MySQL/Redis/Kafka
echo ==========================================================
cd /d "%~dp0..\.."
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0sync-all.ps1" -Mode "local-integration" %*
set "START_EXIT=%ERRORLEVEL%"
if not "%START_EXIT%"=="0" echo [ERROR] Local Integration startup failed.
pause
exit /b %START_EXIT%
