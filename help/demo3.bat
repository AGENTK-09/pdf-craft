@echo off
setlocal EnableDelayedExpansion
cls

REM ================================
REM Configuration
REM ================================
set JAR=target\pdf-creator-1.0-SNAPSHOT.jar
set BASE_CMD=java -jar "%JAR%"
set LOGFILE=demo-output.log
set TMPFILE=demo-tmp.log
set STEP=0

REM ANSI color
set CMD_COLOR=[96m
set RESET=[0m

echo. > %LOGFILE%

echo =====================================
echo        CLI Demo Runner
echo =====================================
echo Using JAR: %JAR%
echo.

for /f "tokens=1* delims=|" %%A in (commands.txt) do (

    set /a STEP+=1
    set CONTEXT=%%A
    set ARGS=%%B

    set COMMAND=%BASE_CMD% !ARGS!

    echo.
    echo =====================================
    echo STEP !STEP!
    echo !CONTEXT!
    echo.
    echo %CMD_COLOR%!COMMAND!%RESET%
    echo =====================================
    echo.

    set /p input=Press ENTER to execute...

    cmd /c "!COMMAND!" > %TMPFILE% 2>&1

    type %TMPFILE%
    type %TMPFILE% >> %LOGFILE%

    del %TMPFILE%

    echo.
    set /p input=Press ENTER for next step...
)

echo.
echo Demo complete.
pause