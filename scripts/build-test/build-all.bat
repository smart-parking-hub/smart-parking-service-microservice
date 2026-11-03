@echo off
echo ==========================================
echo  Building SmartParkingHub Microservices
echo ==========================================
cd /d "%~dp0..\.."
call gradlew.bat build -x test
pause
