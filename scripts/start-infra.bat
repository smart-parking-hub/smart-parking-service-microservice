@echo off
echo ==========================================
echo  Starting Infrastructure (MySQL, Redis, Kafka, Prometheus, Grafana)
echo ==========================================
cd /d "%~dp0..\docker"
docker compose -f docker-compose.infra.yml up -d
echo Infrastructure containers started!
pause
