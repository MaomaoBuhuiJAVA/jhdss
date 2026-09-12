$ErrorActionPreference = 'Stop'

$yolo = 'D:\jhdss-tools\yolo-venv311\Scripts\yolo.exe'
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
$weights = Join-Path $project 'black_longhorn_yolo11s\weights\best.pt'
$source = Join-Path $datasetRoot 'camera_test'

if (-not (Test-Path -LiteralPath $yolo)) {
    throw "Ultralytics CLI not found: $yolo"
}
if (-not (Test-Path -LiteralPath $weights)) {
    throw "Trained weights not found: $weights"
}

& $yolo predict `
    "model=$weights" `
    "source=$source" `
    imgsz=1024 `
    conf=0.25 `
    device=0 `
    "project=$project" `
    name=camera_test `
    exist_ok=True `
    save=True `
    save_txt=True
