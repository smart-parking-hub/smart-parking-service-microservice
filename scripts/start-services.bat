@echo off
echo ==========================================
echo  Building Microservice JARs
echo ==========================================
cd /d "%~dp0.."
call gradlew.bat bootJar -x test
if errorlevel 1 (
    echo [ERROR] Build failed! Please check logs above.
    pause
    exit /b %errorlevel%
)

echo ==========================================
echo  Starting Microservices Containers
echo ==========================================
cd /d "%~dp0..\docker"
docker compose -f docker-compose.apps.yml up -d --build
echo ==========================================
echo  Microservices started successfully!
echo ==========================================
pause
