$ErrorActionPreference = "Continue"

$yolo = "D:\jhdss-tools\yolo-venv311\Scripts\yolo.exe"
$data = "E:\LabelImg资料图片\yolo_dataset\dataset.yaml"
$project = "E:\LabelImg资料图片\yolo_runs"
$run = "black_longhorn_yolo11s"
$last = Join-Path $project "$run\weights\last.pt"
$logDir = Join-Path $project "logs"
New-Item -ItemType Directory -Path $logDir -Force | Out-Null
$stdout = Join-Path $logDir "resume_cache.stdout.log"
$stderr = Join-Path $logDir "resume_cache.stderr.log"
Remove-Item -LiteralPath $stdout,$stderr -Force -ErrorAction SilentlyContinue
$args = @("train","model=$last","data=$data","epochs=100","patience=30","imgsz=1024","batch=2","device=0","workers=0","cache=True","resume=True","project=$project","name=$run","exist_ok=True","plots=True")
Start-Process -FilePath $yolo -ArgumentList $args -RedirectStandardOutput $stdout -RedirectStandardError $stderr -WindowStyle Hidden
Start-Sleep -Seconds 8
Write-Output "launched"
