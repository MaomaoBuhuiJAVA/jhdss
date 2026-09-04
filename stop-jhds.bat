@echo off
setlocal EnableExtensions EnableDelayedExpansion
title JHDS Local Server - Stop

cd /d "%~dp0"
echo ========================================
echo   JHDS Smart Agriculture - Local Stop
echo ========================================
echo Project: %CD%
echo.

if not exist "pom.xml" (
    echo [ERROR] pom.xml was not found. Put this script in the project root.
    pause
    exit /b 1
)

echo [INFO] Looking for this project's Spring Boot process...
powershell -NoProfile -ExecutionPolicy Bypass -Command ^
  "$project = [IO.Path]::GetFullPath((Get-Location).Path); $escaped = [regex]::Escape($project.TrimEnd([char]92)); $targets = @(Get-CimInstance Win32_Process | Where-Object { $line = [string]$_.CommandLine; ($line -match 'spring-boot:run') -and (($line -match $escaped) -or ($line -match 'com\.jhds\.JhdsApplication')) }); if ($targets.Count -eq 0) { Write-Host '[INFO] No JHDS Spring Boot process found.'; exit 0 }; $targets | Sort-Object @{Expression={ if ($_.Name -ieq 'cmd.exe') { 0 } else { 1 } }} | ForEach-Object { Write-Host ('[INFO] Stopping PID ' + $_.ProcessId + ' (' + $_.Name + ')'); Stop-Process -Id $_.ProcessId -Force -ErrorAction SilentlyContinue }; Start-Sleep -Seconds 2"

if errorlevel 1 (
    echo [WARN] Process scan failed. Check the window output above.
) else (
    echo [OK] JHDS stop command completed.
)

powershell -NoProfile -Command ^
  "$listeners = @(Get-NetTCPConnection -LocalPort 9117 -State Listen -ErrorAction SilentlyContinue); if ($listeners.Count -eq 0) { Write-Host '[OK] Port 9117 is free.' } else { $listeners | ForEach-Object { Write-Host ('[WARN] Port 9117 is still used by PID ' + $_.OwningProcess) } }"

echo.
pause
exit /b 0
