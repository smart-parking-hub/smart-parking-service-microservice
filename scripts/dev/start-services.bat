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
docker compose --project-directory . -f docker\compose\local\compose.yaml up -d --build
echo All microservices started!
pause
