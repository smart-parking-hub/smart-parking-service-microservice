@echo off
echo ==========================================================
echo  [SmartParkingHub] Verification Loop: Full System Check
echo ==========================================================

cd /d "%~dp0..\.."
echo [1/3] Compiling all Microservices...
call gradlew.bat compileJava compileTestJava --no-daemon
if %ERRORLEVEL% NEQ 0 (
    echo [ERROR] Compilation failed!
    pause
    exit /b %ERRORLEVEL%
)

echo [2/3] Running automated tests...
call gradlew.bat test --no-daemon
if %ERRORLEVEL% NEQ 0 (
    echo [ERROR] Tests failed!
    pause
    exit /b %ERRORLEVEL%
)

echo [3/3] Checking Docker Compose configuration validity...
cd /d "%~dp0..\..\docker"
docker compose -f docker-compose.infra.yml config > nul
if %ERRORLEVEL% NEQ 0 (
    echo [ERROR] docker-compose.infra.yml syntax error!
    pause
    exit /b %ERRORLEVEL%
)
docker compose -f docker-compose.apps.yml config > nul
if %ERRORLEVEL% NEQ 0 (
    echo [ERROR] docker-compose.apps.yml syntax error!
    pause
    exit /b %ERRORLEVEL%
)

echo ==========================================================
echo  [SUCCESS] All verification loops passed! Microservices are stable.
echo ==========================================================
pause
