@echo off
chcp 65001 >nul
cd /d "%~dp0"
where py >nul 2>nul
if not errorlevel 1 (
    py -3 -X utf8 tools\activation_manager.py
) else (
    python -X utf8 tools\activation_manager.py
)
if errorlevel 1 (
    echo.
    echo Could not start activation manager. Install Python 3 with tkinter, then retry.
    pause
)
