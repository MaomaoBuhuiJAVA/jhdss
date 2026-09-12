@echo off
setlocal

set "LABELME_ENV=D:\jhdss-tools\labelimg-venv311-fresh"
set "LABELME_PYTHON=%LABELME_ENV%\Scripts\python.exe"

if "%~1"=="" (
    echo Usage: %~nx0 ^<json_dir^>
    echo Example: %~nx0 D:\beetle-dataset\labels_json
    pause
    exit /b 1
)

echo Exporting labelme JSON to YOLO format...
"%LABELME_PYTHON%" -m labelme.cli.json_to_dataset -o "%~1_yolo" --dataset-format yolo "%~1"
echo Done!
pause
