@echo off
setlocal
set "SPOOL_HOME=%~dp0.."
set "JAVA=java"
if defined JAVA_HOME set "JAVA=%JAVA_HOME%\bin\java.exe"
if exist "%SPOOL_HOME%\jre\bin\java.exe" set "JAVA=%SPOOL_HOME%\jre\bin\java.exe"
"%JAVA%" -cp "%SPOOL_HOME%\lib\*" software.spool.cli.Spool %*
exit /b %ERRORLEVEL%
