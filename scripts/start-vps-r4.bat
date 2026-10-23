@echo off
echo ==========================================================
echo  [SmartParkingHub] Starting VPS R4 Resource-Limited Profile
echo  (Enforces strict 512MB RAM limits, Nginx edge, and log rotation)
echo ==========================================================

cd ..
docker compose -f compose.yaml -f compose.vps-r4.yaml up -d --build
echo VPS R4 Profile started!
pause
