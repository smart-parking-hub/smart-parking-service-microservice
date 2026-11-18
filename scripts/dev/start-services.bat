@echo off
echo ==========================================
echo  Building Microservice JARs
echo ==========================================
cd /d "%~dp0..\.."
call gradlew.bat bootJar -x test
if errorlevel 1 (
    echo [ERROR] Build failed! Please check logs above.
    pause
    exit /b %errorlevel%
)

echo ==========================================
echo  Starting Microservices Containers
echo ==========================================
docker network inspect smart-parking-net >nul 2>&1 || docker network create smart-parking-net >nul 2>&1
docker compose -p docker --project-directory . -f docker\compose\local\compose.yaml -f docker\compose\local\compose.local.yaml up -d --build --force-recreate api-gateway identity-service parking-lot-service booking-service payment-service notification-service ai-service
echo All microservices started!
pause
