@echo off
setlocal
title SmartParkingHub - Deploy Microservices to VPS Cluster

cd /d "%~dp0..\.."

if "%~1"=="" goto INTERACTIVE

:: Run directly with passed arguments
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0deploy-vps.ps1" %*
goto END

:INTERACTIVE
cls
echo ================================================================
echo    SMARTPARKINGHUB - TRIEN KHAI MICROSERVICES LEN VPS CLUSTER
echo ================================================================
echo.
echo  Chon che do trien khai:
echo.
echo   [1] Cap nhat TOAN BO 3 VPS (Edge, Data, Core Services)
echo   [2] Cap nhat VPS 1 (Edge: Gateway, Identity, Nginx)
echo   [3] Cap nhat VPS 2 (Data: Payment, Notification, AI)
echo   [4] Cap nhat VPS 3 (Core: Parking-lot, Booking)
echo   [5] Cap nhat chi 1 Service cu the
echo   [6] Kiem tra ket noi SSH tien tram (Dry-Run Test)
echo   [7] Dang nhap Token GHCR cho ca 3 VPS (Chi can chay 1 lan)
echo.
echo   [0] Quay lai
echo.
echo ================================================================
set /p choice=" Nhap lua chon [1-7]: "

if "%choice%"=="1" (
    powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0deploy-vps.ps1" -Target all
    goto END
)
if "%choice%"=="2" (
    powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0deploy-vps.ps1" -Target vps1
    goto END
)
if "%choice%"=="3" (
    powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0deploy-vps.ps1" -Target vps2
    goto END
)
if "%choice%"=="4" (
    powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0deploy-vps.ps1" -Target vps3
    goto END
)
if "%choice%"=="5" (
    echo.
    set /p svcname=" Nhap ten service (vd: identity-service, api-gateway, booking-service): "
    powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0deploy-vps.ps1" -Service %svcname%
    goto END
)
if "%choice%"=="6" (
    powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0deploy-vps.ps1" -Target all -DryRun
    goto END
)
if "%choice%"=="7" (
    powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0deploy-vps.ps1" -Target all -LoginVPS
    goto END
)
if "%choice%"=="0" exit /b 0

echo Lua chon khong hop le!
pause
goto INTERACTIVE

:END
if %ERRORLEVEL% NEQ 0 (
    echo.
    echo [ERROR] Qua trinh deploy co loi xay ra. Vui long kiem tra log o tren.
)
echo.
pause
