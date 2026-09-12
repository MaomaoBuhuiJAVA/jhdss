@echo off
setlocal

set "LABELIMG_ENV=D:\jhdss-tools\labelimg-venv311"
set "LABELIMG_PYTHONW=%LABELIMG_ENV%\Scripts\pythonw.exe"
set "LABELIMG_LOCALE=zh-CN"

rem Force the Qt software renderer on machines where the graphics driver causes
rem Qt5Core/Qt5Gui to terminate while drawing or switching images.
set "QT_OPENGL=software"
set "QT_QUICK_BACKEND=software"
set "QT_AUTO_SCREEN_SCALE_FACTOR=0"
set "QT_SCALE_FACTOR_ROUNDING_POLICY=PassThrough"

if not exist "%LABELIMG_PYTHONW%" (
    echo [ERROR] LabelImg Python environment was not found:
    echo         %LABELIMG_PYTHONW%
    echo Please reinstall the environment or update LABELIMG_ENV in this file.
    pause
    exit /b 1
)

echo Starting LabelImg (Python 3.11 module, Qt software rendering)...
echo Environment: %LABELIMG_ENV%
if "%~1"=="" (
    start "LabelImg" "%LABELIMG_PYTHONW%" -m labelImg.labelImg
) else (
    start "LabelImg" "%LABELIMG_PYTHONW%" -m labelImg.labelImg %*
)
exit /b 0
