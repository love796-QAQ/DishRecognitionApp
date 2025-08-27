@ECHO OFF
SETLOCAL

SET DIR=%~dp0
SET WRAPPER_DIR=%DIR%gradle\wrapper
SET PROPS_FILE=%WRAPPER_DIR%\gradle-wrapper.properties

FOR /F "tokens=2 delims==" %%A IN ('findstr distributionUrl %PROPS_FILE%') DO SET DISTRIBUTION_URL=%%A

SET ZIP_FILE=%USERPROFILE%\.gradle\wrapper\dists\%DISTRIBUTION_URL:~0,-4%.zip

IF NOT EXIST %ZIP_FILE% (
  ECHO Downloading Gradle from %DISTRIBUTION_URL%
  powershell -Command "& {Invoke-WebRequest '%DISTRIBUTION_URL%' -OutFile '%ZIP_FILE%'}"
)

FOR /D %%D IN ("%USERPROFILE%\.gradle\wrapper\dists\gradle-*") DO SET GRADLE_DIR=%%D

"%GRADLE_DIR%\bin\gradle.bat" %*
ENDLOCAL
