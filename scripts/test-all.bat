@echo off
echo ==========================================================
echo  [SmartParkingHub] Automated Test Suite Runner
echo ==========================================================

cd ..
call gradlew.bat test --no-daemon
if %ERRORLEVEL% NEQ 0 (
    echo [ERROR] Automated tests failed! Please inspect reports in build/reports/tests/test/index.html
    exit /b %ERRORLEVEL%
)

echo [SUCCESS] All unit & integration tests passed successfully!
pause
