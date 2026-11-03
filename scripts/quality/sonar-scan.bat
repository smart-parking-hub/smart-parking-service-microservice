@echo off
echo ==========================================================
echo  [SmartParkingHub] Running Test Suite + JaCoCo + SonarQube Scan
echo ==========================================================

cd /d "%~dp0..\.."

echo [1/2] Running automated unit tests and generating JaCoCo coverage reports...
call gradlew.bat test jacocoTestReport --no-daemon
if %ERRORLEVEL% NEQ 0 (
    echo [ERROR] Tests failed! Please fix test failures before running Sonar scan.
    pause
    exit /b %ERRORLEVEL%
)

echo.
echo [2/2] Scanning code and sending metrics to SonarQube (http://localhost:9000)...
call gradlew.bat sonar --no-daemon
if %ERRORLEVEL% NEQ 0 (
    echo [ERROR] SonarQube scan failed!
    echo Ensure SonarQube is running at http://localhost:9000
    echo If required, pass your token: gradlew sonar -Dsonar.token=your_token
    pause
    exit /b %ERRORLEVEL%
)

echo.
echo ==========================================================
echo  [SUCCESS] Scan complete! View your project report at:
echo  http://localhost:9000/dashboard?id=SmartParkingHub
echo ==========================================================
pause
