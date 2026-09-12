$ErrorActionPreference = 'Stop'

$yolo = 'E:\LabelImg璧勬枡鍥剧墖\yolo_runs\black_longhorn_yolo11s\weights\best.pt'
$model = 'D:\jhdss-tools\yolo-models\yolo11s.pt'
$data = Get-ChildItem -LiteralPath 'E:\' -Directory -Force | ForEach-Object {
    $candidate = Join-Path $_.FullName 'yolo_dataset\dataset.yaml'
    if (Test-Path -LiteralPath $candidate) { $candidate }
} | Select-Object -First 1
$data = [string]$data
if ([string]::IsNullOrWhiteSpace($data) -or -not (Test-Path -LiteralPath $data)) {
    throw 'Dataset config not found under an E drive yolo_dataset directory.'
}
$datasetRoot = Split-Path -Parent $data
$project = Join-Path (Split-Path -Parent $datasetRoot) 'yolo_runs'
$errorLog = Join-Path $project 'train_error.log'
$runName = 'black_longhorn_yolo11s'

if (-not (Test-Path -LiteralPath $yolo)) {
    throw "Ultralytics CLI not found: $yolo"
}
New-Item -ItemType Directory -Path $project -Force | Out-Null
$lastCheckpoint = Join-Path $project "$runName\weights\last.pt"
$resume = Test-Path -LiteralPath $lastCheckpoint
if ($resume) {
    $model = $lastCheckpoint
} elseif (-not (Test-Path -LiteralPath $model)) {
    throw "Model weights not found: $model"
}

$trainArgs = @(
    "model=$model"
    "data=$data"
    'epochs=500'
    'patience=100'
    'imgsz=640'
    'batch=8'
    'device=0'
    'workers=4'
    'cache=True'
    'cos_lr=True'
    'close_mosaic=20'
    'warmup_epochs=5'
    'mixup=0.1'
    'scale=0.7'
    'hsv_h=0.02'
    'hsv_s=0.8'
    'hsv_v=0.5'
)
if ($resume) {
    $trainArgs += 'resume=True'
} else {
    $trainArgs += 'pretrained=True'
}
$trainArgs += @(
    "project=$project"
    "name=$runName"
    'exist_ok=True'
    'plots=True'
)

try {
    if ($resume) {
        Write-Host "Resuming from checkpoint: $lastCheckpoint" -ForegroundColor Cyan
    } else {
        Write-Host 'Starting optimized training run.' -ForegroundColor Cyan
    }
    & $yolo train @trainArgs
    if ($LASTEXITCODE -ne 0) {
        throw "Ultralytics exited with code $LASTEXITCODE"
    }
}
catch {
    $_ | Out-File -LiteralPath $errorLog -Encoding UTF8
    Write-Host "Training failed: $errorLog" -ForegroundColor Red
    throw
}
