@echo off
setlocal EnableDelayedExpansion
cls

REM ================================
REM Configuration
REM ================================
set JAR=target\pdf-creator-1.0-SNAPSHOT.jar
set APP=java -jar "%JAR%"

REM Color settings
set CMD_COLOR=[96m
set RESET=[0m

echo =====================================
echo        PDF Creator CLI Demo
echo =====================================
echo Using JAR: %JAR%
echo.

call :runCmd %APP% --config-id dark --title "Dark Report" --text "This is body text on a dark background." --output dark.pdf
call :runCmd %APP% --config-id report --title "Analysis" --text "See the chart below." --image ./chart.png --output analysis.pdf

echo.
echo Demo complete.
pause
exit /b


REM ================================
REM Function: runCmd
REM ================================
:runCmd

echo.
echo =====================================

echo %CMD_COLOR%Next command:%RESET%
echo %CMD_COLOR%%*%RESET%

echo =====================================
echo.

set /p input=Press ENTER to execute this command...

%*

echo.
set /p input=Press ENTER to continue to the next command...
echo.
exit /b