@echo off
setlocal

set DIR=%~dp0
set APP_HOME=%DIR%
set CLASSPATH=%APP_HOME%gradle\wrapper\gradle-wrapper.jar

if not defined JAVA_HOME goto execute
set JAVA_EXE=%JAVA_HOME%\bin\java.exe
if exist "%JAVA_EXE%" goto execute
echo ERROR: JAVA_HOME is set to an invalid directory: %JAVA_HOME% 1>&2
exit /b 1

:execute
if defined JAVA_HOME goto runJava
set JAVA_EXE=java.exe

:runJava
"%JAVA_EXE%" -classpath "%CLASSPATH%" org.gradle.wrapper.GradleWrapperMain %*
exit /b %ERRORLEVEL%

