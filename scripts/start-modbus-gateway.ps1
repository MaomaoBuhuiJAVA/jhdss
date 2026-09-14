param(
    [Parameter(Mandatory = $true)]
    [string]$ProjectRoot,
    [switch]$Monitor
)

$ErrorActionPreference = 'Stop'
$project = [IO.Path]::GetFullPath($ProjectRoot)
$runtime = Join-Path $project '.codex-runtime'
$software = [IO.Path]::GetFullPath((Join-Path $project 'software\server.exe'))
$stdout = Join-Path $runtime 'software-server.out.log'
$stderr = Join-Path $runtime 'software-server.err.log'
$pidFile = Join-Path $runtime 'software-server.pid'
$monitorPidFile = Join-Path $runtime 'software-server-monitor.pid'
$gatewayPort = if ($env:MODBUS_GATEWAY_PORT) { [int]$env:MODBUS_GATEWAY_PORT } else { 8999 }
$escapedProject = [regex]::Escape($project.TrimEnd([char]92))

function Get-ControlListener {
    return @(Get-NetTCPConnection -State Listen -LocalPort $gatewayPort -ErrorAction SilentlyContinue)
}

function Test-BundledSoftware([object]$candidate) {
    if ($null -eq $candidate -or -not $candidate.ExecutablePath) { return $false }
    return [IO.Path]::GetFullPath($candidate.ExecutablePath) -eq $software
}

if (-not (Test-Path -LiteralPath $software)) {
    Write-Error "Bundled control service was not found: $software"
    exit 1
}

New-Item -ItemType Directory -Path $runtime -Force | Out-Null

function Start-SoftwareAttempt {
    Set-Content -LiteralPath $stdout -Value '' -Encoding UTF8
    $process = Start-Process -FilePath $software `
        -WorkingDirectory (Split-Path $software -Parent) `
        -WindowStyle Hidden `
        -RedirectStandardOutput $stdout `
        -RedirectStandardError $stderr `
        -PassThru
    $process.Id | Set-Content -LiteralPath $pidFile -Encoding ASCII
    return $process
}

function Start-SoftwareMonitor {
    Write-Host "[INFO] Software control monitor started; Modbus connection will retry in the background."
    while ($true) {
        $running = @(Get-CimInstance Win32_Process -Filter "Name = 'server.exe'" -ErrorAction SilentlyContinue | Where-Object {
            Test-BundledSoftware $_
        })
        if ($running.Count -gt 0) {
            Start-Sleep -Seconds 3
            continue
        }

        $attempt = $null
        try {
            $attempt = Start-SoftwareAttempt
            $ready = $false
            for ($probe = 0; $probe -lt 30; $probe++) {
                Start-Sleep -Milliseconds 500
                if ($attempt.HasExited) { break }
                $ready = @(Get-ControlListener | Where-Object { $_.OwningProcess -eq $attempt.Id }).Count -gt 0
                if ($ready) { break }
            }
            if ($ready) {
                Write-Host "[OK] Bundled software control service is listening on port $gatewayPort."
                Start-Sleep -Seconds 5
                continue
            }

            $exitCode = if ($attempt.HasExited) { $attempt.ExitCode } else { 'not-ready' }
            Add-Content -LiteralPath $stderr -Value "[RETRY] Modbus/software connection is not ready (result=$exitCode). Retrying in 10 seconds."
        } catch {
            Add-Content -LiteralPath $stderr -Value "[RETRY] Unable to start bundled software: $($_.Exception.Message). Retrying in 10 seconds."
        }
        Start-Sleep -Seconds 10
    }
}

if ($Monitor) {
    Start-SoftwareMonitor
    exit 0
}

$existingMonitor = $null
if (Test-Path -LiteralPath $monitorPidFile) {
    $savedMonitorPid = (Get-Content -LiteralPath $monitorPidFile -Raw).Trim()
    if ($savedMonitorPid -match '^\d+$') {
        $candidateMonitor = Get-CimInstance Win32_Process -Filter "ProcessId = $savedMonitorPid" -ErrorAction SilentlyContinue
        if (($null -ne $candidateMonitor) -and
                (([string]$candidateMonitor.CommandLine) -match 'start-modbus-gateway\.ps1') -and
                (([string]$candidateMonitor.CommandLine) -match [regex]::Escape($project))) {
            $existingMonitor = $candidateMonitor
        }
    }
}
if ($null -ne $existingMonitor) {
    Write-Host "[OK] Bundled software control monitor is already running (PID $($existingMonitor.Id))."
    exit 0
}

$listeners = Get-ControlListener
foreach ($listener in $listeners) {
    $owner = Get-CimInstance Win32_Process -Filter "ProcessId = $($listener.OwningProcess)" -ErrorAction SilentlyContinue
    if (Test-BundledSoftware $owner) {
        $owner.ProcessId | Set-Content -LiteralPath $pidFile -Encoding ASCII
        Write-Host "[OK] Bundled software control service is already running on port $gatewayPort."
        exit 0
    }
}

# The compatibility gateway and bundled server cannot share port 8999 or the
# controller's single Modbus TCP connection. The bundled service is the
# field-tested path for forward/backward motion and the foliar pump.
$fallbacks = @(Get-CimInstance Win32_Process -Filter "Name = 'java.exe'" -ErrorAction SilentlyContinue | Where-Object {
    (([string]$_.CommandLine) -match 'com\.jhds\.gateway\.JhdsModbusGateway') -and
        (([string]$_.CommandLine) -match $escapedProject)
})
foreach ($fallback in $fallbacks) {
    Write-Host "[INFO] Stopping compatibility gateway PID $($fallback.ProcessId) before starting bundled software."
    Stop-Process -Id $fallback.ProcessId -Force -ErrorAction Stop
}
if ($fallbacks.Count -gt 0) { Start-Sleep -Seconds 1 }

$listeners = Get-ControlListener
if ($listeners.Count -gt 0) {
    $owners = ($listeners | Select-Object -ExpandProperty OwningProcess -Unique) -join ', '
    Write-Error "Port $gatewayPort is occupied by PID $owners. Run stop-jhds.bat before starting again."
    exit 1
}

$staleSoftware = @(Get-CimInstance Win32_Process -Filter "Name = 'server.exe'" -ErrorAction SilentlyContinue | Where-Object {
    Test-BundledSoftware $_
})
foreach ($stale in $staleSoftware) {
    Write-Host "[INFO] Stopping stale bundled software PID $($stale.ProcessId)."
    Stop-Process -Id $stale.ProcessId -Force -ErrorAction SilentlyContinue
}
if ($staleSoftware.Count -gt 0) { Start-Sleep -Seconds 1 }

$monitorProcess = Start-Process -FilePath 'powershell.exe' `
    -ArgumentList @('-NoProfile', '-ExecutionPolicy', 'Bypass', '-File', ('"{0}"' -f $PSCommandPath), '-ProjectRoot', ('"{0}"' -f $project), '-Monitor') `
    -WorkingDirectory $project `
    -WindowStyle Hidden `
    -PassThru
$monitorProcess.Id | Set-Content -LiteralPath $monitorPidFile -Encoding ASCII
Write-Host "[OK] Bundled software control monitor is running (PID $($monitorProcess.Id)); Modbus will connect automatically when available."
exit 0
