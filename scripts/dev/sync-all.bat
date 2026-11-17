@echo off
setlocal
cd /d "%~dp0..\.."
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0sync-all.ps1" -Mode "local-dev" %*
if %ERRORLEVEL% NEQ 0 (
    echo.
    echo [ERROR] Qua trinh dong bo Docker & Database that bai.
)
echo.
pause
