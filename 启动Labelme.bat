@echo off
setlocal

set "LABELME_ENV=D:\jhdss-tools\labelimg-venv311-fresh"
set "LABELME_PYTHON=%LABELME_ENV%\Scripts\python.exe"

set "QT_OPENGL=software"
set "QT_QUICK_BACKEND=software"

if not exist "%LABELME_PYTHON%" (
    echo [ERROR] labelme Python environment not found:
    echo         %LABELME_PYTHON%
    pause
    exit /b 1
)

echo labelme - YOLO TXT auto-save enabled
echo Ctrl+S saves both JSON and .txt

start "labelme" "%LABELME_PYTHON%" -m labelme
exit /b 0
