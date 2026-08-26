@echo off
setlocal EnableExtensions EnableDelayedExpansion
title JHDS Local Server

cd /d "%~dp0"
echo ========================================
echo   JHDS Smart Agriculture - Local Start
echo ========================================
echo Project: %CD%
echo.

if not exist "pom.xml" (
    echo [ERROR] pom.xml was not found. Put this script in the project root.
    pause
    exit /b 1
)

where java >nul 2>&1
if errorlevel 1 (
    echo [ERROR] Java was not found in PATH.
    echo         Check JAVA_HOME and reopen this window.
    pause
    exit /b 1
)

where mvn >nul 2>&1
if errorlevel 1 (
    echo [ERROR] Maven was not found in PATH.
    pause
    exit /b 1
)

echo [INFO] Java:
java -version
echo [INFO] Maven:
call mvn -version
echo.

set "MYSQL_SERVICE="
for /f "usebackq delims=" %%S in (`powershell -NoProfile -Command "Get-Service ^| Where-Object { $_.Name -match 'mysql' -or $_.DisplayName -match 'mysql' } ^| Select-Object -First 1 -ExpandProperty Name"`) do set "MYSQL_SERVICE=%%S"
if not defined MYSQL_SERVICE (
    echo [ERROR] No MySQL Windows service was found.
    echo         Open services.msc and confirm that MySQL is installed and running.
    pause
    exit /b 1
)
echo [INFO] Detected MySQL service: !MYSQL_SERVICE!
call :ensure_service !MYSQL_SERVICE! "MySQL"
if errorlevel 1 goto :failed

set "REDIS_SERVICE="
sc query Redis >nul 2>&1
if not errorlevel 1 set "REDIS_SERVICE=Redis"
if not defined REDIS_SERVICE (
    sc query Memurai >nul 2>&1
    if not errorlevel 1 set "REDIS_SERVICE=Memurai"
)
if not defined REDIS_SERVICE (
    echo [ERROR] Redis or Memurai Windows service was not found.
    pause
    exit /b 1
)
call :ensure_service !REDIS_SERVICE! "Redis"
if errorlevel 1 goto :failed

echo [INFO] Checking MySQL port 3306...
powershell -NoProfile -Command "if ((Test-NetConnection 127.0.0.1 -Port 3306 -InformationLevel Quiet) -ne $true) { exit 1 }" >nul 2>&1
if errorlevel 1 (
    echo [ERROR] MySQL is not accepting connections on 127.0.0.1:3306.
    pause
    exit /b 1
)

echo [INFO] Checking Redis port 6379...
powershell -NoProfile -Command "if ((Test-NetConnection 127.0.0.1 -Port 6379 -InformationLevel Quiet) -ne $true) { exit 1 }" >nul 2>&1
if errorlevel 1 (
    echo [ERROR] Redis is not accepting connections on 127.0.0.1:6379.
    pause
    exit /b 1
)

if exist ".env.local.bat" (
    echo [INFO] Loading .env.local.bat
    call ".env.local.bat"
) else (
    echo [WARN] .env.local.bat was not found. Required external-service variables may be missing.
)

echo.
echo [INFO] Starting Spring Boot. Keep this window open.
echo [INFO] URL: http://localhost:9117/jhds/
echo.
if defined JAVA_OPTS (
    echo [INFO] JVM options: !JAVA_OPTS!
    call mvn spring-boot:run "-Dspring-boot.run.jvmArguments=!JAVA_OPTS!"
) else (
    call mvn spring-boot:run
)
set "APP_EXIT=%ERRORLEVEL%"
echo.
echo [INFO] Spring Boot stopped with exit code !APP_EXIT!.
pause
exit /b !APP_EXIT!

:ensure_service
set "SERVICE_NAME=%~1"
set "SERVICE_LABEL=%~2"
sc query "%SERVICE_NAME%" | find /I "RUNNING" >nul
if not errorlevel 1 (
    echo [OK] !SERVICE_LABEL! service is already running.
    exit /b 0
)
echo [INFO] Starting !SERVICE_LABEL! service (!SERVICE_NAME!)...
net start "%SERVICE_NAME%"
if errorlevel 1 (
    echo [ERROR] Could not start !SERVICE_LABEL!. Run this script as Administrator.
    exit /b 1
)
exit /b 0

:failed
echo.
echo [ERROR] Startup checks failed.
pause
exit /b 1
