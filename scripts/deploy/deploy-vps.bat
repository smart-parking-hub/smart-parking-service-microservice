@echo off
setlocal
title SmartParkingHub - Deploy Microservices to VPS Cluster

cd /d "%~dp0..\.."

if "%~1"=="" goto INTERACTIVE

:: Run directly with passed arguments
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0deploy-vps.ps1" %*
goto CAPTURE_RESULT

:INTERACTIVE
cls
echo ================================================================
echo    SMARTPARKINGHUB - TRIEN KHAI MICROSERVICES LEN VPS CLUSTER
echo ================================================================
echo.
echo  Chon nguon image:
echo   [1] LOCAL - Tu mo Docker Desktop, build va push tu may nay
echo   [2] GITHUB CI - Dung artifact main da build (mac dinh)
echo   [0] Quay lai
set "sourceChoice="
set "deploySource=CI"
set /p sourceChoice=" Nguon [1-2, Enter=2]: "
if "%sourceChoice%"=="0" exit /b 0
if "%sourceChoice%"=="1" set "deploySource=Local"
if "%sourceChoice%"=="2" set "deploySource=CI"
if not "%sourceChoice%"=="" if not "%sourceChoice%"=="1" if not "%sourceChoice%"=="2" goto INTERACTIVE
echo.
echo  Nguon da chon: %deploySource%
echo  Chon dich trien khai:
echo.
echo   [1] Cap nhat TOAN BO 3 VPS bang nguon da chon
echo   [2] Cap nhat app VPS 1 (Gateway, Identity)
echo   [3] Cap nhat VPS 2 (Data: Payment, Notification, AI)
echo   [4] Cap nhat VPS 3 (Core: Parking-lot, Booking)
echo   [5] Cap nhat chi 1 Service cu the
echo   [6] Kiem tra ket noi SSH tien tram (Dry-Run Test)
echo   [7] Dang nhap Token GHCR cho ca 3 VPS (Chi can chay 1 lan)
echo.
echo   [0] Quay lai
echo.
echo ================================================================
set "choice="
set /p choice=" Nhap lua chon [1-7]: "

if "%choice%"=="1" (
    powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0deploy-vps.ps1" -Source "%deploySource%" -Target all
    goto END
)
if "%choice%"=="2" (
    powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0deploy-vps.ps1" -Source "%deploySource%" -Target vps1
    goto END
)
if "%choice%"=="3" (
    powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0deploy-vps.ps1" -Source "%deploySource%" -Target vps2
    goto END
)
if "%choice%"=="4" (
    powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0deploy-vps.ps1" -Source "%deploySource%" -Target vps3
    goto END
)
if "%choice%"=="5" goto SINGLE_SERVICE
if "%choice%"=="6" (
    powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0deploy-vps.ps1" -Source "%deploySource%" -Target all -DryRun
    goto END
)
if "%choice%"=="7" (
    powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0deploy-vps.ps1" -Source "%deploySource%" -Target all -LoginVPS
    goto END
)
if "%choice%"=="0" exit /b 0

echo Lua chon khong hop le!
pause
goto INTERACTIVE

:SINGLE_SERVICE
set "svcname="
set /p svcname=" Nhap ten service (vd: identity-service, api-gateway, booking-service): "
if not defined svcname (
    echo [ERROR] Ten service khong duoc de trong.
    exit /b 1
)
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0deploy-vps.ps1" -Source "%deploySource%" -Service "%svcname%"
goto END

:END
:CAPTURE_RESULT
set "DEPLOY_EXIT=%ERRORLEVEL%"
if not "%DEPLOY_EXIT%"=="0" (
    echo.
    echo [ERROR] Qua trinh deploy co loi xay ra. Vui long kiem tra log o tren.
)
echo.
pause
exit /b %DEPLOY_EXIT%
