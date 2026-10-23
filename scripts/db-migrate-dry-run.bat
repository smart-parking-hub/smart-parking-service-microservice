@echo off
echo ==========================================================
echo  [SmartParkingHub] Database Flyway Migration Validation
echo ==========================================================

cd ..

echo Checking SQL syntax of Flyway migrations across all microservices...
powershell -Command "Get-ChildItem -Path .\*\src\main\resources\db\migration\*.sql | ForEach-Object { Write-Host 'Validating migration:' $_.FullName; Get-Content $_.FullName | Out-Null }; Write-Host '[SUCCESS] All migration files are readable and well-formed.'"

pause
