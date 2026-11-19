@echo off
setlocal
cd /d "%~dp0..\.."
python "%~dp0verify-vps-release.py" %*
set "VERIFY_EXIT=%ERRORLEVEL%"
echo Verification exit code: %VERIFY_EXIT%
pause
exit /b %VERIFY_EXIT%
