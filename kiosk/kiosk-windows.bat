@echo off
REM vescreenflow — kiosk en Windows
REM Doble clic o: kiosk-windows.bat

set URL=https://vescreenflow.com/play
if not "%VESCREENFLOW_URL%"=="" set URL=%VESCREENFLOW_URL%

set CHROME=
if exist "%ProgramFiles%\Google\Chrome\Application\chrome.exe" set CHROME=%ProgramFiles%\Google\Chrome\Application\chrome.exe
if exist "%ProgramFiles(x86)%\Google\Chrome\Application\chrome.exe" set CHROME=%ProgramFiles(x86)%\Google\Chrome\Application\chrome.exe
if exist "%LocalAppData%\Google\Chrome\Application\chrome.exe" set CHROME=%LocalAppData%\Google\Chrome\Application\chrome.exe

if "%CHROME%"=="" (
  echo No se encontro Google Chrome.
  echo Instala Chrome desde https://www.google.com/chrome/
  pause
  exit /b 1
)

start "" "%CHROME%" --kiosk --fullscreen --noerrdialogs --disable-infobars --autoplay-policy=no-user-gesture-required "%URL%"
