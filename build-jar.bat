@echo off
rem Build runnable jars for LAN deployment: chat-server.jar / chat-client.jar
rem Target machines only need a JRE (double-click to run).
set JDK=C:\Program Files\Java\jdk-17\bin
cd /d "%~dp0"

if not exist classes mkdir classes
"%JDK%\javac.exe" -encoding UTF-8 -d classes com\cncd\ch04\common\*.java com\cncd\ch04\server\*.java com\cncd\ch04\client\*.java
if not %errorlevel%==0 ( echo COMPILE FAILED & pause & exit /b 1 )

"%JDK%\jar.exe" cfe chat-server.jar com.cncd.ch04.server.MainServer -C classes com
"%JDK%\jar.exe" cfe chat-client.jar com.cncd.ch04.client.ChatClient -C classes com

echo.
echo Done: chat-server.jar / chat-client.jar
echo   server machine: double-click chat-server.jar (allow firewall on first run)
echo   other machines: copy chat-client.jar, double-click, click "Scan LAN"
pause
