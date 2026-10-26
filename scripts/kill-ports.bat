@echo off
title [SmartParkingHub] Free Busy Ports
echo ==========================================================
echo  [SmartParkingHub] Scanning and Freeing Microservices Ports
echo ==========================================================

powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0kill-ports.ps1"

echo.
echo Done.
pause
