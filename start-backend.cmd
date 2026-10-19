@echo off
setlocal
cd /d "%~dp0backend"
call mvn spring-boot:run
pause
