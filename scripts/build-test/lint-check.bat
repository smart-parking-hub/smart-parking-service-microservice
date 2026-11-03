@echo off
echo ==========================================================
echo  [SmartParkingHub] Code Quality & Linting Check
echo ==========================================================

cd /d "%~dp0..\.."
call gradlew.bat check -x test --no-daemon
if %ERRORLEVEL% NEQ 0 (
    echo [ERROR] Code check found violations!
    pause
    exit /b %ERRORLEVEL%
)

echo [SUCCESS] Code quality & syntax validation passed!
pause
