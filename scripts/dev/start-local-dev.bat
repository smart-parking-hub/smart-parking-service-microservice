@echo off
echo ==========================================================
echo  [SmartParkingHub] Starting Local Dev Profile
echo ==========================================================

cd /d "%~dp0..\.."
docker compose --project-directory . -f docker\compose\local\compose.yaml -f docker\compose\local\compose.local.yaml up -d --build
echo Local Dev services started!
pause
