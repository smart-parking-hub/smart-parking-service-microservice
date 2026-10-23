@echo off
echo ==========================================================
echo  [SmartParkingHub] Starting Local Dev Profile
echo ==========================================================

cd ..
docker compose -f compose.yaml -f compose.local.yaml up -d --build
echo Local Dev services started!
pause
