@echo off
rem One-click: compile + start server (if not already running) + open 2 client windows
rem 自动定位 JDK 17：优先已知安装路径，都没有则用 PATH 上的 javac/java（各人机器路径不同也不用改）
set "JDK="
if exist "C:\Program Files\Eclipse Adoptium\jdk-17.0.19.10-hotspot\bin\javac.exe" set "JDK=C:\Program Files\Eclipse Adoptium\jdk-17.0.19.10-hotspot\bin"
if not defined JDK if exist "C:\Program Files\Java\jdk-17\bin\javac.exe" set "JDK=C:\Program Files\Java\jdk-17\bin"
if defined JDK (set "PFX=%JDK%\") else (set "PFX=")
cd /d "%~dp0"

echo [1/3] Compiling...
if not exist classes mkdir classes
"%PFX%javac.exe" -encoding UTF-8 -d classes com\cncd\ch04\common\*.java com\cncd\ch04\server\*.java com\cncd\ch04\client\*.java
if not %errorlevel%==0 goto compilefail

echo [2/3] Checking server...
rem two-stage findstr: keep only LISTENING lines, then require literal ":3500 "
netstat -ano | findstr "LISTENING" | findstr /c:":3500 " >nul
if %errorlevel%==0 goto clients

echo Starting server on port 3500 - a separate window opens, KEEP IT OPEN.
start "Chat Server port 3500 - close me to stop" "%PFX%java.exe" -cp classes com.cncd.ch04.server.MainServer 3500
timeout /t 2 /nobreak >nul
goto clients

:clients
echo [3/3] Opening 2 client windows...
start "" "%PFX%javaw.exe" -cp classes com.cncd.ch04.client.ChatClient
start "" "%PFX%javaw.exe" -cp classes com.cncd.ch04.client.ChatClient
timeout /t 2 /nobreak >nul
exit /b 0

:compilefail
echo COMPILE FAILED - fix errors above, nothing started.
pause
exit /b 1
