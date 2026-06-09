@echo off
setlocal

cd /d "%~dp0"

set WAR_FILE=target\WebTerm.war

if "%~1"=="" goto :build
if "%~1"=="build" goto :build
if "%~1"=="clean" goto :clean
if "%~1"=="--help" goto :help
if "%~1"=="-h" goto :help
echo Unknown option: %~1
goto :help

:build
echo ============================================
echo  Building WebTerm WAR for external Tomcat
echo ============================================
call mvn -f pom.xml clean package -DskipTests
if errorlevel 1 (
    echo.
    echo Build failed.
    exit /b 1
)
echo.
echo ============================================
echo  External Tomcat deployment artifact
echo ============================================
echo  Upload this file:
echo    %WAR_FILE%
echo.
echo  Tomcat context path:
echo    /WebTerm
echo.
echo  URL after deployment:
echo    https://your-domain/WebTerm
echo ============================================
goto :eof

:clean
echo ============================================
echo  Cleaning external Tomcat WAR build
echo ============================================
call mvn -f pom.xml clean
if errorlevel 1 exit /b 1
echo Clean complete.
goto :eof

:help
echo Usage: %~nx0 [build^|clean]
echo.
echo Commands:
echo   build    Build target\WebTerm.war for external Tomcat (default)
echo   clean    Clean Maven target directory
echo.
echo Note:
echo   Use target\WebTerm.war for Tomcat Manager or webapps upload.
echo   Do not upload target\WebTerm-embedded.war to external Tomcat.
exit /b 1
