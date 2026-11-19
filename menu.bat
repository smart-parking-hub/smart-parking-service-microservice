@echo off
title SmartParkingHub - Microservices Control Center

:MENU
cls
echo ================================================================
echo        SMARTPARKINGHUB - TRUNG TAM DIEU PHOI MICROSERVICES
echo ================================================================
echo.
echo  [★] DONG BO NHANH SAU KHI PULL CODE (KHUYEN DUNG CHO THANH VIEN)
echo      1.0 - [ALL-IN-ONE] Pull code xong chay cai nay: Build JAR + Docker + Sync DB
echo.
echo  [1] MOI TRUONG VA CONTAINER - scripts/dev
echo      1.1 - Khoi dong Ha tang local: MySQL/Redis/Kafka, monitoring, SonarQube
echo      1.2 - Khoi dong Local Dev Profile (Tu dong build JAR + Sync DB)
echo      1.3 - Preset Local Full cu: Apps + MySQL/Redis/Kafka (tool quan sat tuy chon)
echo      1.4 - Giai phong port bi chiem dung (Kill Busy Ports)
echo      1.5 - Cap nhat (Build JAR ^& Reload) toan bo Microservices len Docker Local
echo      1.6 - Reset sach Database (Xoa Volume DB cu + Tai tao va chay lai toan bo Migration)
echo      1.7 - [VPS UPDATE] Chon LOCAL build / GITHUB CI, kiem tra commit + digest + health
echo      1.8 - [LOCAL UPDATE] Chay Apps + MySQL/Redis/Kafka de test tich hop
echo      1.9 - [READ ONLY] Kiem tra release dang chay tren ca 3 VPS
echo.
echo  [2] BIEN DICH VA KIEM THU - scripts/build-test
echo      2.1 - Build toan bo Microservices
echo      2.2 - Chay toan bo Unit va Integration Tests
echo      2.3 - Kiem tra toan dien: Compile, Test va Check
echo      2.4 - Kiem tra file migration doc duoc (khong migrate DB)
echo      2.5 - [READ ONLY] Kiem tra cac to hop Compose
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
echo  [5] QUAN TRI GIT VA KIEM TRA NHANH - scripts/git
echo      5.1 - Kiem tra dong bo tat ca cac nhanh (Branch Sync Audit)
echo      5.2 - Kiem tra chuyen sau Commit va Suc khoe nhanh (Commit Audit)
echo      5.3 - Chuyen nhanh thong minh va an toan (Smart Checkout)
echo.
echo  [0] Thoat
echo.
echo ================================================================
set "opt="
set /p opt=" Nhap lua chon: "
if not defined opt goto MENU

if "%opt%"=="1.0" goto DEV_SYNC_ALL
if "%opt%"=="1.1" goto DEV_INFRA
if "%opt%"=="1.2" goto DEV_LOCAL
if "%opt%"=="1.3" goto DEV_FULL
if "%opt%"=="1.4" goto DEV_KILL
if "%opt%"=="1.5" goto DEV_UPDATE
if "%opt%"=="1.6" goto DEV_RESET_DB
if "%opt%"=="1.7" goto DEV_DEPLOY_VPS
if "%opt%"=="1.8" goto DEV_INTEGRATION
if "%opt%"=="1.9" goto VPS_VERIFY
if "%opt%"=="2.1" goto BT_BUILD
if "%opt%"=="2.2" goto BT_TEST
if "%opt%"=="2.3" goto BT_VERIFY
if "%opt%"=="2.4" goto BT_FLYWAY
if "%opt%"=="2.5" goto BT_COMPOSE
if "%opt%"=="3.1" goto QL_START_SONAR
if "%opt%"=="3.2" goto QL_SCAN
if "%opt%"=="4.1" goto TR_DASHBOARD
if "%opt%"=="4.2" goto TR_MEMBERS
if "%opt%"=="4.3" goto TR_STATUS
if "%opt%"=="4.4" goto TR_BLOCKED
if "%opt%"=="5.1" goto GIT_SYNC
if "%opt%"=="5.2" goto GIT_COMMIT
if "%opt%"=="5.3" goto GIT_CHECKOUT
if "%opt%"=="0" goto EXIT_PROG

echo Lua chon khong hop le!
pause
goto MENU

:DEV_SYNC_ALL
call :ACTION_STATUS "1.0" "Dong bo local"
call "%~dp0scripts\dev\sync-all.bat"
goto MENU

:DEV_INFRA
call :ACTION_STATUS "1.1" "Khoi dong ha tang local"
call "%~dp0scripts\dev\start-infra.bat"
goto MENU

:DEV_LOCAL
call :ACTION_STATUS "1.2" "Khoi dong Local Dev"
call "%~dp0scripts\dev\start-local-dev.bat"
goto MENU

:DEV_FULL
call :ACTION_STATUS "1.3" "Khoi dong Local Full"
call "%~dp0scripts\dev\start-local-full.bat"
goto MENU

:DEV_KILL
call :ACTION_STATUS "1.4" "Kiem tra port"
call "%~dp0scripts\dev\kill-ports.bat"
goto MENU

:DEV_UPDATE
call :ACTION_STATUS "1.5" "Cap nhat app local"
call "%~dp0scripts\dev\update-microservices-docker.bat"
goto MENU

:DEV_RESET_DB
call :ACTION_STATUS "1.6" "Mo cong cu reset database"
call "%~dp0scripts\dev\reset-db.bat"
goto MENU

:DEV_DEPLOY_VPS
call :ACTION_STATUS "1.7" "Mo lua chon deploy VPS"
call "%~dp0scripts\deploy\deploy-vps.bat" & goto MENU

:BT_BUILD
call :ACTION_STATUS "2.1" "Build microservices"
call "%~dp0scripts\build-test\build-all.bat"
goto MENU

:DEV_INTEGRATION
call :ACTION_STATUS "1.8" "Khoi dong local integration"
call "%~dp0scripts\dev\start-local-integration.bat"
goto MENU

:VPS_VERIFY
call :ACTION_STATUS "1.9" "Kiem tra phien ban VPS"
call "%~dp0scripts\deploy\verify-vps-release.bat"
goto MENU

:BT_COMPOSE
call :ACTION_STATUS "2.5" "Kiem tra Compose"
call "%~dp0scripts\build-test\check-compose.bat"
echo Compose check exit code: %ERRORLEVEL%
pause
goto MENU

:BT_TEST
call :ACTION_STATUS "2.2" "Chay tests"
call "%~dp0scripts\build-test\test-all.bat"
goto MENU

:BT_VERIFY
call :ACTION_STATUS "2.3" "Kiem tra toan dien"
call "%~dp0scripts\build-test\verify-all.bat"
goto MENU

:BT_FLYWAY
call :ACTION_STATUS "2.4" "Kiem tra file migration"
call "%~dp0scripts\build-test\db-migrate-dry-run.bat"
goto MENU

:QL_START_SONAR
call :ACTION_STATUS "3.1" "Khoi dong SonarQube"
call "%~dp0scripts\quality\start-sonarqube.bat"
goto MENU

:QL_SCAN
call :ACTION_STATUS "3.2" "Phan tich chat luong code"
call "%~dp0scripts\quality\sonar-scan.bat"
goto MENU

:TR_DASHBOARD
call :ACTION_STATUS "4.1" "Mo dashboard"
start "" "%~dp0docs\trello-team-dashboard.html"
goto MENU

:TR_MEMBERS
call :ACTION_STATUS "4.2" "Doc phan bo task"
call "%~dp0scripts\trello\trello.bat" members
pause
goto MENU

:TR_STATUS
call :ACTION_STATUS "4.3" "Doc trang thai task"
call "%~dp0scripts\trello\trello.bat" status
pause
goto MENU

:TR_BLOCKED
call :ACTION_STATUS "4.4" "Doc task bi chan"
call "%~dp0scripts\trello\trello.bat" blocked
pause
goto MENU

:GIT_SYNC
call :ACTION_STATUS "5.1" "Kiem tra dong bo nhanh"
node "%~dp0scripts\git\branch-sync-audit.js"
pause
goto MENU

:GIT_COMMIT
call :ACTION_STATUS "5.2" "Kiem tra commit"
node "%~dp0scripts\git\commit-deep-audit.js"
pause
goto MENU

:GIT_CHECKOUT
call :ACTION_STATUS "5.3" "Mo cong cu chuyen nhanh"
node "%~dp0scripts\git\checkout-helper.js"
pause
goto MENU

:EXIT_PROG
exit /b 0

:ACTION_STATUS
echo.
echo [DANG XU LY] %~1 - %~2
echo Vui long cho; chi nhap phim khi chuong trinh hien yeu cau.
echo.
exit /b 0
