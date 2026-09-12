param(
    [Parameter(Mandatory = $true)]
    [string]$ProjectRoot
)

$project = [IO.Path]::GetFullPath($ProjectRoot)
$runtime = Join-Path $project '.codex-runtime'
$pidFile = Join-Path $runtime 'modbus-gateway.pid'
$softwarePidFile = Join-Path $runtime 'software-server.pid'
$escapedProject = [regex]::Escape($project.TrimEnd([char]92))
$legacyPath = [IO.Path]::GetFullPath((Join-Path $project 'software\server.exe'))
$hotspotPath = [IO.Path]::GetFullPath((Join-Path $project 'software\server-hotspot.exe'))
$targets = @()

if (Test-Path -LiteralPath $pidFile) {
    $savedPid = (Get-Content -LiteralPath $pidFile -Raw).Trim()
    if ($savedPid -match '^\d+$') {
        $candidate = Get-CimInstance Win32_Process -Filter "ProcessId = $savedPid" -ErrorAction SilentlyContinue
        if (($null -ne $candidate) -and
                (([string]$candidate.CommandLine) -match 'com\.jhds\.gateway\.JhdsModbusGateway') -and
                (([string]$candidate.CommandLine) -match $escapedProject)) {
            $targets += $candidate
        }
    }
}

if (Test-Path -LiteralPath $softwarePidFile) {
    $savedPid = (Get-Content -LiteralPath $softwarePidFile -Raw).Trim()
    if ($savedPid -match '^\d+$') {
        $candidate = Get-CimInstance Win32_Process -Filter "ProcessId = $savedPid" -ErrorAction SilentlyContinue
        if (($null -ne $candidate) -and $candidate.ExecutablePath) {
            $candidatePath = [IO.Path]::GetFullPath($candidate.ExecutablePath)
            if ($candidatePath -eq $legacyPath -or $candidatePath -eq $hotspotPath) {
                $targets += $candidate
            }
        }
    }
}

$targets += @(Get-CimInstance Win32_Process -Filter "Name = 'java.exe'" -ErrorAction SilentlyContinue | Where-Object {
    (([string]$_.CommandLine) -match 'com\.jhds\.gateway\.JhdsModbusGateway') -and
        (([string]$_.CommandLine) -match $escapedProject)
})
$targets += @(Get-CimInstance Win32_Process -Filter "Name = 'server.exe'" -ErrorAction SilentlyContinue | Where-Object {
    $_.ExecutablePath -and [IO.Path]::GetFullPath($_.ExecutablePath) -eq $legacyPath
})
$targets += @(Get-CimInstance Win32_Process -Filter "Name = 'server-hotspot.exe'" -ErrorAction SilentlyContinue | Where-Object {
    $_.ExecutablePath -and [IO.Path]::GetFullPath($_.ExecutablePath) -eq $hotspotPath
})

$targets | Sort-Object ProcessId -Unique | ForEach-Object {
    Write-Host "[INFO] Stopping control service PID $($_.ProcessId) ($($_.Name))"
    Stop-Process -Id $_.ProcessId -Force -ErrorAction SilentlyContinue
}
Remove-Item -LiteralPath $pidFile -Force -ErrorAction SilentlyContinue
Remove-Item -LiteralPath $softwarePidFile -Force -ErrorAction SilentlyContinue

if ($targets.Count -eq 0) {
    Write-Host '[INFO] No project control service process found.'
} else {
    Start-Sleep -Seconds 1
    Write-Host '[OK] Project control service stopped.'
}
