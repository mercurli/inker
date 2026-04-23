@echo off
for /f "tokens=2 delims==" %%i in ('wmic os get LocalDateTime /value') do set dt=%%i
set today=%dt:~0,8%
git add .
git commit -m %today%
git push
pause