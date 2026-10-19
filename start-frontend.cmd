@echo off
setlocal
cd /d "%~dp0frontend"
if not exist node_modules (
  call npm ci
  if errorlevel 1 exit /b 1
)
call npm run dev
pause
