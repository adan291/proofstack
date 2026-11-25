@echo off
rem Runs the Proofstack CLI from the repository folder: .\proofstack <command> ...
rem Uses the jar built from the code if there is one, otherwise a proofstack.jar downloaded next to this file.
rem If neither exists, builds it once with Maven.
setlocal
set "ROOT=%~dp0"
set "JAR=%ROOT%proofstack-cli\target\proofstack.jar"
if not exist "%JAR%" set "JAR=%ROOT%proofstack.jar"
if not exist "%JAR%" (
  echo proofstack.jar no existe todavia. Compilando con Maven, solo esta vez...
  call mvn -q -f "%ROOT%pom.xml" -DskipTests package -pl proofstack-cli -am
  if errorlevel 1 (
    echo No se pudo compilar. Instala Maven, o descarga proofstack.jar de la Release y dejalo junto a este fichero.
    exit /b 2
  )
  set "JAR=%ROOT%proofstack-cli\target\proofstack.jar"
)
java -jar "%JAR%" %*
exit /b %ERRORLEVEL%
