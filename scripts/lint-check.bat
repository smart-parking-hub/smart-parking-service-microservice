@echo off
echo ==========================================================
echo  [SmartParkingHub] Code Quality & Linting Check
echo ==========================================================

cd ..
call gradlew.bat check -x test --no-daemon
if %ERRORLEVEL% NEQ 0 (
    echo [ERROR] Code check found violations!
    exit /b %ERRORLEVEL%
)

echo [SUCCESS] Code quality & syntax validation passed!
pause
