@echo off
setlocal
set "APP_HOME=%~dp0"
set "PROPS=%APP_HOME%gradle\wrapper\gradle-wrapper.properties"
for /f "tokens=1,* delims==" %%A in ('findstr /b "distributionUrl=" "%PROPS%"') do set "DIST_URL=%%B"
set "DIST_URL=%DIST_URL:\:=%"
for %%F in ("%DIST_URL%") do set "DIST_NAME=%%~nxF"
for /f "tokens=2 delims=-" %%V in ("%DIST_NAME%") do set "VERSION=%%V"
set "CACHE=%USERPROFILE%\.gradle\wrapper\manual\gradle-%VERSION%"
if exist "%CACHE%\bin\gradle.bat" goto run
set "TMP=%TEMP%\gradle-%VERSION%.zip"
set "UNPACK=%TEMP%\gradle-%VERSION%-unpack"
echo Downloading %DIST_URL%
powershell -NoProfile -ExecutionPolicy Bypass -Command "Invoke-WebRequest -Uri '%DIST_URL%' -OutFile '%TMP%'"
if errorlevel 1 exit /b 1
powershell -NoProfile -ExecutionPolicy Bypass -Command "Expand-Archive -Force '%TMP%' '%UNPACK%'"
if errorlevel 1 exit /b 1
if not exist "%CACHE%\.." mkdir "%USERPROFILE%\.gradle\wrapper\manual"
move "%UNPACK%\gradle-%VERSION%" "%CACHE%" >nul
del "%TMP%"
:run
call "%CACHE%\bin\gradle.bat" %*
exit /b %ERRORLEVEL%
