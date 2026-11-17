@echo off
setlocal
echo ==========================================================
echo  [SmartParkingHub] Starting Full Local Integration Lab
echo  (Microservices + MySQL + Redis + Kafka + Prometheus + Grafana)
echo ==========================================================

cd /d "%~dp0..\.."
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0sync-all.ps1" -Mode "local-full" %*
if %ERRORLEVEL% NEQ 0 (
    echo.
    echo [ERROR] Khoi dong Full Local Lab that bai.
)
pause
