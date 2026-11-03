@echo off
echo ==========================================================
echo  [SmartParkingHub] Starting SonarQube Quality Platform
echo ==========================================================

cd /d "%~dp0..\..\docker"
docker compose -f compose\local\docker-compose.infra.yml up -d sonarqube
if %ERRORLEVEL% NEQ 0 (
    echo [ERROR] Failed to start SonarQube container!
    pause
    exit /b %ERRORLEVEL%
)

echo.
echo [SUCCESS] SonarQube container started!
pause
