@echo off
SETLOCAL

set plugin=DaisySRV
set version=1.2
set target="C:\Users\Amari\Desktop\DaisyTests\plugins"
set source=.\build\libs\%plugin%-%version%-shaded.jar
set destination=%target%\%plugin%-%version%.jar

:: Sanitize date and time variables
for /f "tokens=2 delims==" %%I in ('"wmic os get localdatetime /value | findstr LocalDateTime"') do set datetime=%%I
set date=%datetime:~0,8%
set time=%datetime:~8,6%
set time=%time:~0,2%-%time:~2,2%-%time:~4,2%

echo Building %plugin% plugin...
call .\gradlew shadowJar
if %ERRORLEVEL% neq 0 (
    echo Build failed! Check the errors above.
    exit /b %ERRORLEVEL%
)

echo Copying JAR to server plugins folder...
copy /y "%source%" "%destination%"
if %ERRORLEVEL% neq 0 (
    echo Failed to copy JAR file!
    exit /b %ERRORLEVEL%
)

echo Backing up old configuration file...
if exist %target%\%plugin%\config.yml (
    move /y %target%\%plugin%\config.yml %target%\%plugin%\config_backup_%date%_%time%.yml
)

echo Cleaning up configuration files...
del /f %target%\%plugin%\config.yml

echo.
echo Build completed successfully!
echo JAR location: %destination%
for %%I in ("%source%") do echo JAR size: %%~zI bytes

ENDLOCAL