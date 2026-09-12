param(
    [Parameter(Mandatory = $true)]
    [string]$ProjectRoot
)

$ErrorActionPreference = 'Stop'
$project = [IO.Path]::GetFullPath($ProjectRoot)
$runtime = Join-Path $project '.codex-runtime'
$software = [IO.Path]::GetFullPath((Join-Path $project 'software\server.exe'))
$stdout = Join-Path $runtime 'software-server.out.log'
$stderr = Join-Path $runtime 'software-server.err.log'
$pidFile = Join-Path $runtime 'software-server.pid'
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

Set-Content -LiteralPath $stdout -Value '' -Encoding UTF8
Set-Content -LiteralPath $stderr -Value '' -Encoding UTF8
$process = Start-Process -FilePath $software `
    -WorkingDirectory (Split-Path $software -Parent) `
    -WindowStyle Hidden `
    -RedirectStandardOutput $stdout `
    -RedirectStandardError $stderr `
    -PassThru
$process.Id | Set-Content -LiteralPath $pidFile -Encoding ASCII

for ($attempt = 0; $attempt -lt 30; $attempt++) {
    Start-Sleep -Milliseconds 500
    if ($process.HasExited) {
        $detail = if (Test-Path -LiteralPath $stderr) { (Get-Content -LiteralPath $stderr -Raw).Trim() } else { '' }
        Write-Error "Bundled software control service exited with code $($process.ExitCode). $detail"
        exit 1
    }

    $ownsPort = @(Get-ControlListener | Where-Object { $_.OwningProcess -eq $process.Id }).Count -gt 0
    $logText = if (Test-Path -LiteralPath $stderr) { Get-Content -LiteralPath $stderr -Raw } else { '' }
    if ($ownsPort -and $logText -match 'Modbus.*\d{1,3}(?:\.\d{1,3}){3}:\d+') {
        Write-Host "[OK] Bundled software control service is listening on port $gatewayPort and connected to Modbus."
        exit 0
    }
}

Write-Error "Bundled software control service did not become ready. See $stderr"
exit 1
