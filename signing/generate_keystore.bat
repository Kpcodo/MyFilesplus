@echo off
setlocal
cd /d "%~dp0"
echo Looking for keytool...

set "KEYTOOL_PATH="

REM Check PATH
where keytool >nul 2>nul
if %ERRORLEVEL% EQU 0 set "KEYTOOL_PATH=keytool"

REM Check JAVA_HOME
if not defined KEYTOOL_PATH (
    if defined JAVA_HOME (
        if exist "%JAVA_HOME%\bin\keytool.exe" set "KEYTOOL_PATH=%JAVA_HOME%\bin\keytool.exe"
    )
)

REM Check Common Android Studio Paths
if not defined KEYTOOL_PATH if exist "C:\Program Files\Android\Android Studio\jbr\bin\keytool.exe" set "KEYTOOL_PATH=C:\Program Files\Android\Android Studio\jbr\bin\keytool.exe"
if not defined KEYTOOL_PATH if exist "C:\Program Files\Android\Android Studio\jre\bin\keytool.exe" set "KEYTOOL_PATH=C:\Program Files\Android\Android Studio\jre\bin\keytool.exe"
if not defined KEYTOOL_PATH if exist "C:\Program Files\Android\Android Studio1\jbr\bin\keytool.exe" set "KEYTOOL_PATH=C:\Program Files\Android\Android Studio1\jbr\bin\keytool.exe"
if not defined KEYTOOL_PATH if exist "C:\Program Files\JetBrains\IntelliJ IDEA Community Edition 2023.3.3\jbr\bin\keytool.exe" set "KEYTOOL_PATH=C:\Program Files\JetBrains\IntelliJ IDEA Community Edition 2023.3.3\jbr\bin\keytool.exe"


if not defined KEYTOOL_PATH (
    echo.
    echo [ERROR] Could not find 'keytool.exe'.
    echo Please edit this script and set KEYTOOL_PATH to your JDK bin directory.
    pause
    exit /b 1
)

echo Found keytool at: "%KEYTOOL_PATH%"

if exist "release.jks" (
    echo [WARNING] release.jks already exists!
    echo Delete it/Move it if you want to regenerate.
    pause
    exit /b 1
)

"%KEYTOOL_PATH%" -genkeypair -v -keystore release.jks -keyalg RSA -keysize 2048 -validity 10000 -alias key0 -dname "CN=FileManagerApp, OU=Development, O=Self, L=Unknown, S=Unknown, C=US" -storepass password -keypass password

if %ERRORLEVEL% EQU 0 (
    echo.
    echo [SUCCESS] Keystore generated at release.jks
) else (
    echo [ERROR] Generation failed.
)
pause
