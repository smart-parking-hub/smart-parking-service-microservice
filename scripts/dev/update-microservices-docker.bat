@echo off
setlocal
cd /d "%~dp0..\.."
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0sync-all.ps1" %*
if %ERRORLEVEL% NEQ 0 (
    echo.
    echo [ERROR] Qua trinh cap nhat Docker that bai.
)
echo.
pause
