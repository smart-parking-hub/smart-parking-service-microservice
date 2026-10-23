@echo off
echo ==========================================================
echo  [SmartParkingHub] Starting Local Dev Profile
echo ==========================================================

cd /d "%~dp0.."
docker compose -f compose.yaml -f compose.local.yaml up -d --build
echo Local Dev services started!
pause
