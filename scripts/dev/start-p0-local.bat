@echo off
setlocal
for %%I in ("%~dp0..\..") do set "P0_REPO=%%~fI"
rem Reuse the running local MySQL/Redis/Kafka and their volumes. Do not use down -v.
rem This helper intentionally reuses images; the retained identity migration
rem ordering and payment lineage must be copy-validated before any image upgrade.
docker compose --project-directory "%P0_REPO%" --env-file "%P0_REPO%\.env" --project-name docker -f "%P0_REPO%\docker\compose\local\compose.yaml" -f "%P0_REPO%\docker\compose\overlays\compose.restore-p0.local.yaml" -f "%P0_REPO%\docker\compose\overlays\compose.schedule.local.yaml" -f "%P0_REPO%\docker\compose\overlays\compose.no-request-rate.local.yaml" up -d --no-build --pull never --no-deps identity-service parking-lot-service booking-service payment-service notification-service api-gateway ai-service
if errorlevel 1 exit /b %errorlevel%
powershell.exe -NoProfile -File "%~dp0Wait-P0Local.ps1"
exit /b %errorlevel%
