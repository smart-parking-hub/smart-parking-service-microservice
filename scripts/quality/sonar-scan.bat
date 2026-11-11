@echo off
setlocal enabledelayedexpansion
echo ==========================================================
echo  [SmartParkingHub] Running Test Suite + JaCoCo + SonarQube Scan
echo ==========================================================

cd /d "%~dp0..\.."

REM 1. Doc SONAR_TOKEN tu file .env neu co
if exist ".env" (
    for /f "usebackq tokens=1,* delims==" %%a in (".env") do (
        set "KEY=%%a"
        set "VAL=%%b"
        if "!KEY!"=="SONAR_TOKEN" set "SONAR_TOKEN=!VAL!"
    )
)

echo [1/2] Running automated unit tests and generating JaCoCo coverage reports...
call gradlew.bat test jacocoTestReport --no-daemon
if %ERRORLEVEL% NEQ 0 (
    echo [ERROR] Tests failed! Please fix test failures before running Sonar scan.
    pause
    exit /b %ERRORLEVEL%
)

echo.
echo [2/2] Scanning code and sending metrics to SonarQube (http://localhost:9000)...

REM 2. Neu da co SONAR_TOKEN thi bo qua buoc hoi
if not "%SONAR_TOKEN%"=="" goto DO_SCAN

echo.
echo ==========================================================
echo  [CHU Y] SonarQube yeu cau Token xac thuc de phan tich.
echo  Ban co 2 cach:
echo    Cach 1: Vao http://localhost:9000
echo            Avatar (goc phai tren) - [My Account] - [Security]
echo            Tao token 'gradle' roi dan ma vao day.
echo    Cach 2: Neu da tat Force Authentication tren SonarQube,
echo            chi can an ENTER de tiep tuc.
echo ==========================================================
echo.
set /p "USER_INPUT_TOKEN=Nhap ma Sonar Token (hoac an ENTER de tiep tuc): "

if not "!USER_INPUT_TOKEN!"=="" (
    set "SONAR_TOKEN=!USER_INPUT_TOKEN!"
    echo.>> .env
    echo # SonarQube Analysis Token>> .env
    echo SONAR_TOKEN=!USER_INPUT_TOKEN!>> .env
    echo [INFO] Da tu dong luu SONAR_TOKEN vao file .env cho cac lan sau!
)

:DO_SCAN
if not "%SONAR_TOKEN%"=="" (
    call gradlew.bat sonar -Dsonar.token=%SONAR_TOKEN% -Dsonar.login=%SONAR_TOKEN% --no-daemon
) else (
    call gradlew.bat sonar --no-daemon
)

if %ERRORLEVEL% NEQ 0 (
    echo.
    echo [ERROR] SonarQube scan failed!
    echo Vui long kiem tra:
    echo   1. SonarQube da khoi dong tai http://localhost:9000 chua?
    echo   2. Token da dung chua hoac da tat Force User Authentication chua?
    pause
    exit /b %ERRORLEVEL%
)

echo.
echo ==========================================================
echo  [SUCCESS] Scan complete! View your project report at:
echo  http://localhost:9000/dashboard?id=SmartParkingHub
echo ==========================================================
pause
