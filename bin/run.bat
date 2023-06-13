@echo off && @chcp 65001 > nul
pushd %~dp0
set LIB_DIR=%~dp0
for /f "delims=X" %%i in ('dir /b %LIB_DIR%\netdisk-fast-download-*.jar') do (
    set LAUNCH_JAR=%LIB_DIR%%%i
)

"%JAVA_HOME%\bin\java.exe" -Xmx512M -Dfile.encoding=utf8 -jar %LAUNCH_JAR% %*

pause
