@echo off
setlocal EnableExtensions EnableDelayedExpansion
set "MAVEN_PROJECTBASEDIR=%~dp0"
set "WRAPPER_JAR=%MAVEN_PROJECTBASEDIR%.mvn\wrapper\maven-wrapper.jar"
set "WRAPPER_PROPERTIES=%MAVEN_PROJECTBASEDIR%.mvn\wrapper\maven-wrapper.properties"
if not exist "%WRAPPER_JAR%" (
  for /f "usebackq tokens=1,* delims==" %%A in (`findstr /b "wrapperUrl=" "%WRAPPER_PROPERTIES%"`) do set "WRAPPER_URL=%%B"
  if not defined WRAPPER_URL (
    echo Could not find wrapperUrl in %WRAPPER_PROPERTIES% 1>&2
    exit /b 1
  )
  echo Downloading Maven Wrapper bootstrap...
  powershell -NoProfile -ExecutionPolicy Bypass -Command "$ProgressPreference='SilentlyContinue'; Invoke-WebRequest -Uri '!WRAPPER_URL!' -OutFile '!WRAPPER_JAR!'"
  if errorlevel 1 exit /b !errorlevel!
)
java -Dmaven.multiModuleProjectDirectory="%MAVEN_PROJECTBASEDIR%." -classpath "%WRAPPER_JAR%" org.apache.maven.wrapper.MavenWrapperMain %*
exit /b %errorlevel%