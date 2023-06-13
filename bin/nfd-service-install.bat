::
:: generate service xml file
::

@echo off
pushd %~dp0
set MY_DIR=%~dp0
set MY_DIR=%MY_DIR:~0,-1%

for /f "delims=X" %%i in ('dir /b %MY_DIR%\netdisk-fast-download-*.jar') do (
    set LAUNCH_JAR=%MY_DIR%\%%i
)

(for /f "delims=" %%a in (nfd-service-template.xml) do (
set "str=%%a"
setlocal enabledelayedexpansion
set "str=!str:${dd}=%MY_DIR%!"
set "str=!str:${jar}=%LAUNCH_JAR%!"
echo,!str!
endlocal
))>"nfd-service.xml"


sc delete netdisk-fast-download
nfd-service install
sc start netdisk-fast-download
pause
