@echo off
setlocal
set SCRIPT_DIR=%~dp0

if "%1"=="" goto CHECKOUT
if /i "%1"=="checkout" goto CHECKOUT_ARG
if /i "%1"=="audit" goto AUDIT
if /i "%1"=="commit" goto COMMIT
if /i "%1"=="ps" goto PS
goto CHECKOUT_ARG

:CHECKOUT
node "%SCRIPT_DIR%checkout-helper.js"
goto END

:CHECKOUT_ARG
shift
node "%SCRIPT_DIR%checkout-helper.js" %1 %2 %3
goto END

:AUDIT
node "%SCRIPT_DIR%branch-sync-audit.js" %2 %3
goto END

:COMMIT
node "%SCRIPT_DIR%commit-deep-audit.js" %2 %3
goto END

:PS
powershell -NoProfile -ExecutionPolicy Bypass -File "%SCRIPT_DIR%branch-sync.ps1"
goto END

:END
