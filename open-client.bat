@echo off
rem Open ONE more client window (run start-all.bat first if server is not up)
rem 自动定位 JDK 17：优先已知安装路径，都没有则用 PATH 上的 javac/java（各人机器路径不同也不用改）
set "JDK="
if exist "C:\Program Files\Eclipse Adoptium\jdk-17.0.19.10-hotspot\bin\javac.exe" set "JDK=C:\Program Files\Eclipse Adoptium\jdk-17.0.19.10-hotspot\bin"
if not defined JDK if exist "C:\Program Files\Java\jdk-17\bin\javac.exe" set "JDK=C:\Program Files\Java\jdk-17\bin"
if defined JDK (set "PFX=%JDK%\") else (set "PFX=")
cd /d "%~dp0"

if not exist classes\com\cncd\ch04\client\ChatClient.class (
  echo classes not found, compiling first...
  if not exist classes mkdir classes
  "%PFX%javac.exe" -encoding UTF-8 -d classes com\cncd\ch04\common\*.java com\cncd\ch04\server\*.java com\cncd\ch04\client\*.java
  if not %errorlevel%==0 ( echo COMPILE FAILED & pause & exit /b 1 )
)

start "" "%PFX%javaw.exe" -cp classes com.cncd.ch04.client.ChatClient
exit /b 0
