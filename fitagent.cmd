@echo off
rem Runs the app on Windows: "fitagent" opens the window, and "fitagent help" lists the CLI commands.
rem Build the jar first with: mvn package
if not exist "%~dp0target\fitagent.jar" (
    echo target\fitagent.jar is missing. Build it first with: mvn package
    exit /b 1
)
java -jar "%~dp0target\fitagent.jar" %*
