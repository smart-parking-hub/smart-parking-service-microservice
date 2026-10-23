@echo off
echo ==========================================
echo  Starting Microservices Containers
echo ==========================================
cd ..\docker
docker compose -f docker-compose.apps.yml up -d --build
echo Microservices started!
pause
