@echo off
echo ==========================================================
echo  [SmartParkingHub] Starting VPS R4 Resource-Limited Profile
echo  (Enforces strict 512MB RAM limits, Nginx edge, and log rotation)
echo ==========================================================

cd /d "%~dp0..\.."
docker compose --project-directory . -f docker\compose\local\compose.yaml -f docker\compose\vps-single\compose.vps-r4.yaml up -d --build
echo VPS R4 Profile started!
pause
