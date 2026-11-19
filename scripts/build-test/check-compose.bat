@echo off
setlocal
cd /d "%~dp0..\.."
python "%~dp0check-compose.py" %*
exit /b %ERRORLEVEL%
