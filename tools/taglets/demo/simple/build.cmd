@echo off
REM Build command to create the JavaDoc of the simple demo.

REM Prevent propagation of environment variables.
IF "%OS%"=="Windows_NT" @setlocal

SET TAGLET_CLASS=net.sourceforge.taglets.Taglets
SET TAGLET_PATH=..\..\taglets.jar

SET TAGLETS=-taglet %TAGLET_CLASS% -tagletpath %TAGLET_PATH%

del /s /q doc
javadoc %TAGLETS% -d doc -sourcepath src @packages.txt

IF "%OS%"=="Windows_NT" @endlocal
