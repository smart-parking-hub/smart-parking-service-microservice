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
python "%~dp0check-compose.py"
if %ERRORLEVEL% NEQ 0 (
    echo [ERROR] Compose scenario validation failed. Python 3 and Docker Compose are required.
    pause
    exit /b %ERRORLEVEL%
)

echo ==========================================================
echo  [SUCCESS] Compilation, automated tests and Compose configuration checks passed.
echo ==========================================================
pause
