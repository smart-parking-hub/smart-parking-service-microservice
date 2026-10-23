@echo off
echo ==========================================
echo  Starting Infrastructure (MySQL, Redis, Kafka, Prometheus, Grafana)
echo ==========================================
cd ..\docker
docker compose -f docker-compose.infra.yml up -d
echo Infrastructure containers started!
pause
