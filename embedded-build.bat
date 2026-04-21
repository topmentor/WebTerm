@echo off
setlocal enabledelayedexpansion

cd /d "%~dp0"

if "%SERVER_PORT%"=="" set SERVER_PORT=8080
if "%CONTEXT_PATH%"=="" set CONTEXT_PATH=/SSF2026
if "%JAVA_OPTS%"=="" set JAVA_OPTS=-Xms256m -Xmx512m

set WAR_FILE=target\SSF2026-embedded.war
set EXTRACT_DIR=target\embedded-webapp

if "%~1"=="" goto :build
if "%~1"=="build" goto :build
if "%~1"=="run" goto :run
if "%~1"=="dev" goto :dev
if "%~1"=="clean" goto :clean
echo Usage: %~nx0 [build^|run^|dev^|clean]
exit /b 1

:build
echo ============================================
echo  Building SSF2026 (Embedded Tomcat)
echo ============================================
if exist "%EXTRACT_DIR%" (
    echo Removing previous extraction...
    rmdir /s /q "%EXTRACT_DIR%" 2>nul
)
call mvn -f pom-embedded.xml clean package -DskipTests
if errorlevel 1 (
    echo Build failed!
    exit /b 1
)
echo.
echo Build complete: %WAR_FILE%
goto :eof

:run
if not exist "%WAR_FILE%" (
    echo WAR not found. Building first...
    call :build
    if errorlevel 1 exit /b 1
)

if defined JAVA_HOME (
    set JAVA_CMD=%JAVA_HOME%\bin\java.exe
    set JAR_CMD=%JAVA_HOME%\bin\jar.exe
) else (
    set JAVA_CMD=java
    set JAR_CMD=jar
)

if not exist "%EXTRACT_DIR%\WEB-INF\web.xml" (
    echo Extracting WAR...
    if exist "%EXTRACT_DIR%" rmdir /s /q "%EXTRACT_DIR%"
    mkdir "%EXTRACT_DIR%"
    pushd "%EXTRACT_DIR%"
    "%JAR_CMD%" xf "..\SSF2026-embedded.war"
    popd
    echo Extract complete.
)

set CP=%EXTRACT_DIR%\WEB-INF\classes
for %%f in (%EXTRACT_DIR%\WEB-INF\lib\*.jar) do set "CP=!CP!;%%f"

echo ============================================
echo  Starting SSF2026
echo  Port    : %SERVER_PORT%
echo  Context : %CONTEXT_PATH%
echo ============================================
"%JAVA_CMD%" %JAVA_OPTS% ^
    -Dserver.port=%SERVER_PORT% ^
    -Dserver.contextPath=%CONTEXT_PATH% ^
    -Dwebapp.base=%EXTRACT_DIR% ^
    -Dfile.encoding=UTF-8 ^
    -cp "%CP%" ^
    com.ithows.EmbeddedApplication
goto :eof

:dev
echo ============================================
echo  Compile for dev mode...
echo ============================================
call mvn -f pom-embedded.xml compile -DskipTests
if errorlevel 1 (
    echo Compile failed!
    exit /b 1
)

if not exist "target\dependency" (
    echo Copying dependencies...
    call mvn -f pom-embedded.xml dependency:copy-dependencies -DoutputDirectory=target\dependency -DskipTests
)

set CP=target\classes
for %%f in (target\dependency\*.jar) do set "CP=!CP!;%%f"
for %%f in (lib\*.jar) do set "CP=!CP!;%%f"

echo ============================================
echo  Starting SSF2026 (Dev Mode)
echo  Port    : %SERVER_PORT%
echo  Context : %CONTEXT_PATH%
echo ============================================
java %JAVA_OPTS% -Dserver.port=%SERVER_PORT% -Dserver.contextPath=%CONTEXT_PATH% -Dfile.encoding=UTF-8 -Dwebapp.base=web -cp "%CP%" com.ithows.EmbeddedApplication
goto :eof

:clean
if exist "%EXTRACT_DIR%" (
    rmdir /s /q "%EXTRACT_DIR%" 2>nul
)
call mvn -f pom-embedded.xml clean
echo Clean complete.
goto :eof
