$ErrorActionPreference = 'Continue'

$repo = Split-Path -Parent $MyInvocation.MyCommand.Path
$trainScript = Join-Path $repo 'train_yolo11s.ps1'
$run = 'E:\LabelImg资料图片\yolo_runs\black_longhorn_yolo11s'
$results = Join-Path $run 'results.csv'
$errorLog = Join-Path (Split-Path $run) 'train_error.log'
$supervisorLog = Join-Path (Split-Path $run) 'train_supervisor.log'
$datasetRoots = @('E:\LabelImg资料图片', 'E:\LabelImg')
$supervisorStart = Get-Date

function Get-LatestEpoch {
    if (-not (Test-Path -LiteralPath $results)) { return -1 }
    $epochs = @(
        Get-Content -LiteralPath $results -ErrorAction SilentlyContinue |
            Select-Object -Skip 1 |
            ForEach-Object {
                $parts = $_ -split ','
                $value = 0
                if ($parts.Count -gt 0 -and [int]::TryParse($parts[0].Trim(), [ref]$value)) { $value }
            }
    )
    if ($epochs.Count -eq 0) { return -1 }
    return ($epochs | Measure-Object -Maximum).Maximum
}

function Get-YoloProcess {
    Get-CimInstance Win32_Process -ErrorAction SilentlyContinue |
        Where-Object {
            $_.Name -eq 'yolo.exe' -and
            $_.CommandLine -and
            $_.CommandLine -match 'black_longhorn_yolo11s'
        }
}

function Remove-TrainingCaches {
    foreach ($root in $datasetRoots) {
        if (Test-Path -LiteralPath $root) {
            Get-ChildItem -LiteralPath $root -Recurse -File -Include '*.npy','*.cache' -ErrorAction SilentlyContinue |
                Remove-Item -Force -ErrorAction SilentlyContinue
        }
    }
}

function Write-SupervisorLog([string]$message) {
    $line = "$(Get-Date -Format 'yyyy-MM-dd HH:mm:ss') $message"
    Add-Content -LiteralPath $supervisorLog -Value $line -Encoding UTF8
    Write-Host $line
}

if (-not (Test-Path -LiteralPath $trainScript)) {
    Write-SupervisorLog "训练脚本不存在: $trainScript"
    exit 2
}

Write-SupervisorLog "监督器启动，等待现有训练进程。"
$attempt = 0
while ($true) {
    $active = @(Get-YoloProcess)
    $latest = Get-LatestEpoch
    if ($active.Count -gt 0) {
        Write-SupervisorLog "训练进行中，最新已记录 epoch=$latest。"
        Start-Sleep -Seconds 60
        continue
    }

    $best = Join-Path $run 'weights\best.pt'
    $last = Join-Path $run 'weights\last.pt'
    $errorIsNew = (Test-Path -LiteralPath $errorLog) -and ((Get-Item -LiteralPath $errorLog).LastWriteTime -gt $supervisorStart)
    if ($latest -ge 99 -and (Test-Path -LiteralPath $best) -and -not $errorIsNew) {
        Write-SupervisorLog "检测到训练已完成，latest epoch=$latest，best.pt 存在。监督器退出。"
        exit 0
    }

    $attempt++
    if ($errorIsNew) {
        Write-SupervisorLog "检测到训练异常退出，第 $attempt 次修复：清理数据集缓存后从 last.pt 重试。"
        Remove-TrainingCaches
    } elseif (-not (Test-Path -LiteralPath $last)) {
        Write-SupervisorLog '没有找到 last.pt，无法自动恢复。'
        exit 3
    } else {
        Write-SupervisorLog "训练进程已结束但未达到 100 轮，继续从 last.pt 恢复，latest epoch=$latest。"
    }

    & powershell.exe -NoProfile -ExecutionPolicy Bypass -File $trainScript *>> $supervisorLog
    if ($LASTEXITCODE -eq 0) {
        Write-SupervisorLog '训练脚本正常退出，继续核验结果。'
    } else {
        Write-SupervisorLog "训练脚本退出码=$LASTEXITCODE，将在 15 秒后重试。"
        Start-Sleep -Seconds 15
    }
}
