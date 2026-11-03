@echo off
title SmartParkingHub - Microservices Control Center

:MENU
cls
echo ================================================================
echo        SMARTPARKINGHUB - TRUNG TAM DIEU PHOI MICROSERVICES
echo ================================================================
echo.
echo  [1] MOI TRUONG VA CONTAINER - scripts/dev
echo      1.1 - Khoi dong Ha tang: MySQL, Redis, Kafka, Prometheus
echo      1.2 - Khoi dong Local Dev Profile
echo      1.3 - Khoi dong Full Local Lab
echo      1.4 - Giai phong port bi chiem dung (Kill Busy Ports)
echo.
echo  [2] BIEN DICH VA KIEM THU - scripts/build-test
echo      2.1 - Build toan bo Microservices
echo      2.2 - Chay toan bo Unit va Integration Tests
echo      2.3 - Kiem tra toan dien: Compile, Test va Check
echo      2.4 - Kiem tra cu phap SQL Flyway Migrations
echo.
echo  [3] CHAT LUONG MA NGUON - scripts/quality
echo      3.1 - Khoi dong SonarQube Container
echo      3.2 - Chay SonarQube Scanner va JaCoCo Coverage
echo.
echo  [4] QUAN TRI TASK VA TRELLO - scripts/trello
echo      4.1 - Mo Giao dien Trello Team Dashboard (Browser)
echo      4.2 - Xem phan bo task tung thanh vien (CLI)
echo      4.3 - Xem trang thai cac cot va the (CLI)
echo      4.4 - Xem cac task dang bi Blocked (CLI)
echo.
echo  [0] Thoat
echo.
echo ================================================================
set /p opt=" Nhap lua chon: "

if "%opt%"=="1.1" goto DEV_INFRA
if "%opt%"=="1.2" goto DEV_LOCAL
if "%opt%"=="1.3" goto DEV_FULL
if "%opt%"=="1.4" goto DEV_KILL
if "%opt%"=="2.1" goto BT_BUILD
if "%opt%"=="2.2" goto BT_TEST
if "%opt%"=="2.3" goto BT_VERIFY
if "%opt%"=="2.4" goto BT_FLYWAY
if "%opt%"=="3.1" goto QL_START_SONAR
if "%opt%"=="3.2" goto QL_SCAN
if "%opt%"=="4.1" goto TR_DASHBOARD
if "%opt%"=="4.2" goto TR_MEMBERS
if "%opt%"=="4.3" goto TR_STATUS
if "%opt%"=="4.4" goto TR_BLOCKED
if "%opt%"=="0" goto EXIT_PROG

echo Lua chon khong hop le!
pause
goto MENU

:DEV_INFRA
call "%~dp0scripts\dev\start-infra.bat"
goto MENU

:DEV_LOCAL
call "%~dp0scripts\dev\start-local-dev.bat"
goto MENU

:DEV_FULL
call "%~dp0scripts\dev\start-local-full.bat"
goto MENU

:DEV_KILL
call "%~dp0scripts\dev\kill-ports.bat"
goto MENU

:BT_BUILD
call "%~dp0scripts\build-test\build-all.bat"
goto MENU

:BT_TEST
call "%~dp0scripts\build-test\test-all.bat"
goto MENU

:BT_VERIFY
call "%~dp0scripts\build-test\verify-all.bat"
goto MENU

:BT_FLYWAY
call "%~dp0scripts\build-test\db-migrate-dry-run.bat"
goto MENU

:QL_START_SONAR
call "%~dp0scripts\quality\start-sonarqube.bat"
goto MENU

:QL_SCAN
call "%~dp0scripts\quality\sonar-scan.bat"
goto MENU

:TR_DASHBOARD
start "" "%~dp0docs\trello-team-dashboard.html"
goto MENU

:TR_MEMBERS
call "%~dp0scripts\trello\trello.bat" members
pause
goto MENU

:TR_STATUS
call "%~dp0scripts\trello\trello.bat" status
pause
goto MENU

:TR_BLOCKED
call "%~dp0scripts\trello\trello.bat" blocked
pause
goto MENU

:EXIT_PROG
exit /b 0
