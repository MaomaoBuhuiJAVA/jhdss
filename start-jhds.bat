@echo off
setlocal EnableExtensions EnableDelayedExpansion
title JHDS Local Server

cd /d "%~dp0"

fltmc >nul 2>&1
if errorlevel 1 (
    echo [INFO] Requesting administrator privileges for dependency and service setup...
    powershell -NoProfile -ExecutionPolicy Bypass -Command "Start-Process -FilePath '%~f0' -WorkingDirectory '%~dp0' -Verb RunAs"
    exit /b
)
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

if exist ".env.local.bat" (
    echo [INFO] Loading .env.local.bat
    call ".env.local.bat"
) else if exist "env.local.bat" (
    echo [WARN] Loading env.local.bat. Rename it to .env.local.bat for consistency.
    call "env.local.bat"
) else (
    echo [WARN] .env.local.bat or env.local.bat was not found. Required external-service variables may be missing.
)

echo [INFO] Checking and provisioning local dependencies...
powershell -NoProfile -ExecutionPolicy Bypass -File "scripts\bootstrap-runtime.ps1" -ProjectRoot "%CD%"
if errorlevel 1 goto :failed
if not exist ".jhds-runtime\runtime-env.bat" (
    echo [ERROR] Runtime environment file was not generated.
    goto :failed
)
call ".jhds-runtime\runtime-env.bat"

if defined JAVA_HOME if not exist "%JAVA_HOME%\bin\java.exe" (
    echo [WARN] JAVA_HOME does not contain bin\java.exe: %JAVA_HOME%
    set "JAVA_HOME="
)

if not defined JAVA_HOME (
    rem Maven requires JAVA_HOME even when java.exe is already available in PATH.
    for /f "usebackq delims=" %%J in (`powershell -NoProfile -Command "$java = (Get-Command java.exe -ErrorAction SilentlyContinue).Source; if ($java) { Split-Path (Split-Path $java -Parent) -Parent }"`) do set "JAVA_HOME=%%J"
    if defined JAVA_HOME (
        echo [INFO] JAVA_HOME was not set; using !JAVA_HOME!
    )
)

if defined JAVA_HOME set "PATH=%JAVA_HOME%\bin;%PATH%"
if defined MAVEN_HOME set "PATH=%MAVEN_HOME%\bin;%PATH%"

where java >nul 2>&1
if errorlevel 1 (
    echo [ERROR] Java was not found in PATH.
    echo         Install a JDK and set JAVA_HOME to its installation folder.
    pause
    exit /b 1
)

if not defined JAVA_HOME (
    echo [ERROR] JAVA_HOME could not be determined from java.exe.
    echo         Set JAVA_HOME to the JDK installation folder, not its bin folder.
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
sc query MySQL80 >nul 2>&1
if not errorlevel 1 set "MYSQL_SERVICE=MySQL80"
if not defined MYSQL_SERVICE (
    for /f "usebackq delims=" %%S in (`powershell -NoProfile -Command "$service = (Get-Service -ErrorAction SilentlyContinue).Where({ $_.Name -match 'mysql' -or $_.DisplayName -match 'mysql' }, 'First'); if ($null -ne $service) { $service.Name }"`) do set "MYSQL_SERVICE=%%S"
)
if not defined MYSQL_SERVICE (
    echo [ERROR] No MySQL Windows service was found.
    echo         Open services.msc and confirm that MySQL is installed and running.
    pause
    exit /b 1
)
echo [INFO] Detected MySQL service: !MYSQL_SERVICE!
sc query "!MYSQL_SERVICE!" | find /I "RUNNING" >nul
if errorlevel 1 (
    echo [INFO] Starting MySQL service ^(!MYSQL_SERVICE!^)...
    net start "!MYSQL_SERVICE!"
    if errorlevel 1 goto :failed
) else (
    echo [OK] MySQL service is already running.
)

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
sc query "!REDIS_SERVICE!" | find /I "RUNNING" >nul
if errorlevel 1 (
    echo [INFO] Starting Redis service ^(!REDIS_SERVICE!^)...
    net start "!REDIS_SERVICE!"
    if errorlevel 1 goto :failed
) else (
    echo [OK] Redis service is already running.
)

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

echo [INFO] Checking the local jhds database...
powershell -NoProfile -ExecutionPolicy Bypass -File "scripts\initialize-database.ps1" -ProjectRoot "%CD%"
if errorlevel 1 goto :failed

echo [INFO] Starting the bundled software control service...
powershell -NoProfile -ExecutionPolicy Bypass -File "scripts\start-modbus-gateway.ps1" -ProjectRoot "%CD%"
if errorlevel 1 goto :failed

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

:failed
echo.
echo [ERROR] Startup checks failed. Run this script as Administrator if a dependency service could not be started.
pause
exit /b 1
