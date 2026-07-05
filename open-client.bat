@echo off
rem Open ONE more client window (run start-all.bat first if server is not up)
set JDK=C:\Program Files\Java\jdk-17\bin
cd /d "%~dp0"

if not exist classes\com\cncd\ch04\client\ChatClient.class (
  echo classes not found, compiling first...
  if not exist classes mkdir classes
  "%JDK%\javac.exe" -encoding UTF-8 -d classes com\cncd\ch04\common\*.java com\cncd\ch04\server\*.java com\cncd\ch04\client\*.java
  if not %errorlevel%==0 ( echo COMPILE FAILED & pause & exit /b 1 )
)

start "" "%JDK%\javaw.exe" -cp classes com.cncd.ch04.client.ChatClient
exit /b 0
