@ECHO OFF
SETLOCAL
SET GRADLE_VERSION=8.7
SET APP_HOME=%~dp0
IF EXIST "%APP_HOME%gradle\wrapper\gradle-wrapper.jar" (
  java -classpath "%APP_HOME%gradle\wrapper\gradle-wrapper.jar" org.gradle.wrapper.GradleWrapperMain %*
  EXIT /B %ERRORLEVEL%
)
ECHO Le wrapper JAR n'est pas present. Installez Gradle 8.7 ou utilisez gradlew sous Linux/macOS.
EXIT /B 1
