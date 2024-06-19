@echo off && @chcp 65001 > nul

:: 需要JDK11及以上版本和Windows环境变量已配置jdk的路径
pushd %~dp0
set LIB_DIR=%~dp0
for /f "delims=X" %%i in ('dir /b %LIB_DIR%\netdisk-fast-download.jar') do (
    set LAUNCH_JAR=%LIB_DIR%%%i
)

set "JAVA_HOME=D:\App\dragonwell-17.0.3.0.3+7-GA"
"%JAVA_HOME%\bin\java.exe" -Xmx512M -Dfile.encoding=utf8 -jar %LAUNCH_JAR% %*

pause
