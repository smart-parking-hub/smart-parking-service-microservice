@echo off
echo ==========================================
echo  Starting Infrastructure (MySQL, Redis, Kafka, Prometheus, Grafana)
echo ==========================================
cd /d "%~dp0..\..\docker"
docker network inspect smart-parking-net >nul 2>&1 || docker network create smart-parking-net >nul 2>&1
docker compose -f compose\local\docker-compose.infra.yml up -d
echo Infrastructure containers started!
pause
