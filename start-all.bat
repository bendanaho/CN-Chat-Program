@echo off
rem One-click: compile + start server (if not already running) + open 2 client windows
set JDK=C:\Program Files\Java\jdk-17\bin
cd /d "%~dp0"

echo [1/3] Compiling...
if not exist classes mkdir classes
"%JDK%\javac.exe" -encoding UTF-8 -d classes com\cncd\ch04\common\*.java com\cncd\ch04\server\*.java com\cncd\ch04\client\*.java
if not %errorlevel%==0 goto compilefail

echo [2/3] Checking server...
rem two-stage findstr: keep only LISTENING lines, then require literal ":3500 "
netstat -ano | findstr "LISTENING" | findstr /c:":3500 " >nul
if %errorlevel%==0 goto clients

echo Starting server on port 3500 - a separate window opens, KEEP IT OPEN.
start "Chat Server port 3500 - close me to stop" "%JDK%\java.exe" -cp classes com.cncd.ch04.server.MainServer 3500
timeout /t 2 /nobreak >nul
goto clients

:clients
echo [3/3] Opening 2 client windows...
start "" "%JDK%\javaw.exe" -cp classes com.cncd.ch04.client.ChatClient
start "" "%JDK%\javaw.exe" -cp classes com.cncd.ch04.client.ChatClient
timeout /t 2 /nobreak >nul
exit /b 0

:compilefail
echo COMPILE FAILED - fix errors above, nothing started.
pause
exit /b 1
