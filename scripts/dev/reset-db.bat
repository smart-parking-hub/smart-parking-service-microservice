@echo off
setlocal
echo ================================================================
echo   [CANH BAO] DANG THUC HIEN RESET SACH TOAN BO DATABASE
echo   Toan bo du lieu cu trong MySQL se bi xoa de migrate lai tu dau!
echo ================================================================
set /p confirm="Ban co chac chan muon xoa sach Database khong? (Y/N): "
if /i "%confirm%" NEQ "Y" (
    echo Huy thao tac.
    pause
    exit /b 0
)

cd /d "%~dp0..\.."
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0sync-all.ps1" -CleanDb
if %ERRORLEVEL% NEQ 0 (
    echo.
    echo [ERROR] Qua trinh Reset Database that bai.
)
echo.
pause
