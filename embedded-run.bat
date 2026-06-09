@echo off
setlocal enabledelayedexpansion

cd /d "%~dp0"

set SERVER_PORT=8088
set CONTEXT_PATH=/WebTerm
if "%JAVA_OPTS%"=="" set JAVA_OPTS=-Xms256m -Xmx512m
set WAR_FILE=target\WebTerm-embedded.war
set EXTRACT_DIR=target\embedded-webapp

:parse_args
if "%~1"=="" goto :run
if "%~1"=="--port" (
    set SERVER_PORT=%~2
    shift
    shift
    goto :parse_args
)
if "%~1"=="-p" (
    set SERVER_PORT=%~2
    shift
    shift
    goto :parse_args
)
if "%~1"=="--context" (
    set CONTEXT_PATH=%~2
    shift
    shift
    goto :parse_args
)
if "%~1"=="-c" (
    set CONTEXT_PATH=%~2
    shift
    shift
    goto :parse_args
)
if "%~1"=="--help" goto :show_help
if "%~1"=="-h" goto :show_help
echo Unknown option: %~1
exit /b 1

:run
if defined JAVA_HOME (
    set JAVA_CMD=%JAVA_HOME%\bin\java.exe
    set JAR_CMD=%JAVA_HOME%\bin\jar.exe
) else (
    set JAVA_CMD=java
    set JAR_CMD=jar
)

"%JAVA_CMD%" -version >nul 2>&1
if errorlevel 1 (
    echo ERROR: java not found. Set JAVA_HOME or add java to PATH.
    exit /b 1
)

if not exist "%WAR_FILE%" (
    echo ERROR: WAR file not found: %WAR_FILE%
    echo.
    echo Build first:
    echo   embedded-build.bat
    exit /b 1
)

REM Refresh extracted WAR before running so rebuilt static assets are served.
echo Extracting WAR...
if exist "%EXTRACT_DIR%" rmdir /s /q "%EXTRACT_DIR%"
mkdir "%EXTRACT_DIR%"
pushd "%EXTRACT_DIR%"
"%JAR_CMD%" xf "..\WebTerm-embedded.war"
popd
echo Extract complete.

REM Build classpath: WEB-INF/classes + all JARs in WEB-INF/lib
set CP=%EXTRACT_DIR%\WEB-INF\classes
for %%f in (%EXTRACT_DIR%\WEB-INF\lib\*.jar) do set "CP=!CP!;%%f"

echo ============================================
echo  WebTerm - Embedded Tomcat
echo ============================================
echo  Port    : %SERVER_PORT%
echo  Context : %CONTEXT_PATH%
echo  JVM     : %JAVA_OPTS%
echo ============================================
echo.
echo  URL: http://localhost:%SERVER_PORT%%CONTEXT_PATH%
echo.
echo  Stop: Ctrl+C
echo ============================================

"%JAVA_CMD%" %JAVA_OPTS% ^
    -Dserver.port=%SERVER_PORT% ^
    -Dserver.contextPath=%CONTEXT_PATH% ^
    -Dwebapp.base=%EXTRACT_DIR% ^
    -Dfile.encoding=UTF-8 ^
    -cp "%CP%" ^
    com.ithows.EmbeddedApplication

goto :eof

:show_help
echo Usage: %~nx0 [OPTIONS]
echo.
echo Options:
echo   -p, --port PORT        HTTP port (default: 8088)
echo   -c, --context PATH     Context path (default: /WebTerm)
echo   -h, --help             Show help
echo.
echo Environment:
echo   JAVA_HOME              JDK path
echo   JAVA_OPTS              JVM options (default: -Xms256m -Xmx512m)
goto :eof
