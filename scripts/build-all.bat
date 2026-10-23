@echo off
echo ==========================================
echo  Building SmartParkingHub Microservices
echo ==========================================
cd ..
call gradlew.bat build -x test
pause
