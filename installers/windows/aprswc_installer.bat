@echo off
rem APRS Welcome Center Installer
rem Copyright (c) 2026 John Rokicki KC1VMZ
rem 
rem This program is free software: you can redistribute it and/or modify
rem it under the terms of the GNU General Public License as published by
rem the Free Software Foundation, either version 3 of the License, or
rem (at your option) any later version.
rem 
rem This program is distributed in the hope that it will be useful,
rem but WITHOUT ANY WARRANTY; without even the implied warranty of
rem MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
rem GNU General Public License for more details.
rem 
rem You should have received a copy of the GNU General Public License
rem along with this program.  If not, see <https://www.gnu.org/licenses/>.
rem
rem http://www.kc1vmz.com
rem 

setlocal enabledelayedexpansion

echo Welcome to the APRS Welcome Center installer for Microsoft Windows.
echo Copyright (c) 2026 John Rokicki KC1VMZ - Licensed under GPL v3
echo https://www.kc1vmz.com
echo.
echo This installer expects pre-requisite software to be installed - Java 21+
echo Please install these pre-requisites prior to installation.
echo Java can be found at https://www.java.com/en/download/manual.jsp
echo.
pause
SET WC_INSTALL_DIR=%USERPROFILE%\APRSWelcomeCenter
SET WC_TEMP_DIR=%TEMP%\APRSWelcomeCenter
SET WC_DB_DIR=%WC_TEMP_DIR%\db
SET WC_VERSION=1.0.5
SET WC_PORT=8080

REM Prompt the user for input
SET /P "WC_VERSION=What version of APRS Welcome Center? (Default: %WC_VERSION%): "
SET /P "WC_INSTALL_DIR=Where should APRS Welcome Center be installed? (Default: %WC_INSTALL_DIR%): "
SET /P "WC_TEMP_DIR=Where should APRS Welcome Center database space be located? (Default: %WC_DB_DIR%): "
SET /P "WC_TEMP_DIR=Where should APRS Welcome Center temp space be located? (Default: %WC_TEMP_DIR%): "
SET /P "WC_PORT=What port should the HTTP service listen on? (Default: %WC_PORT%): "

SET APRSWC_TEMP_DIR=%WC_TEMP_DIR%
pushd .

echo Checking for pre-requisite - Java 21
rem Step 1: Check if Java is installed at all
where java >nul 2>&1
if %errorlevel% neq 0 (
    echo [ERROR] Java is not installed or not added to your system PATH.
    goto done
)
rem Step 2: Extract the major version number
rem This runs 'java -version', grabs the first line, and parses the version string.
for /f "tokens=3" %%g in ('java -version 2^>^&1 ^| findstr /i "version"') do (
    set "java_version=%%g"
)
rem Remove surrounding quotes from the version string (e.g., "17.0.1" -> 17.0.1)
set "java_version=%java_version:"=%"
rem Extract the main major version number before the first dot
for /f "delims=." %%v in ("!java_version!") do (
    set "major_version=%%v"
)
rem Handle legacy naming convention (e.g., Java 8 outputs "1.8", so major is 1)
if "!major_version!"=="1" (
    for /f "tokens=2 delims=." %%v in ("!java_version!") do (
        set "major_version=%%v"
    )
)
rem Step 3: Validate if version is 21 or greater
if !major_version! lss 21 (
    echo [ERROR] Java version is too old. Required: 21 or greater.
    goto done
)

echo Starting installation

echo Creating directories
mkdir %WC_INSTALL_DIR%
mkdir %WC_TEMP_DIR%

cd %WC_INSTALL_DIR%

echo Downloading built components for version %WC_VERSION% from Github
SET WC_SRC_URL_ROOT=https://github.com/kc1vmz/aprswelcomecenter/releases/download/v%WC_VERSION%
curl -L -o aprs-welcome-center-%WC_VERSION%.jar %WC_SRC_URL_ROOT%\aprs-welcome-center-%WC_VERSION%.jar
SET WC_SRC_URL_ROOT=

echo Creating startup script
echo @ECHO OFF>> aprswc_start.bat
echo pushd .>> aprswc_start.bat
echo cd %WC_INSTALL_DIR%>> aprswc_start.bat
echo REM Set Environment Variables>> aprswc_start.bat
echo SET APRSWC_DB_DIR=%APRSWC_DB_DIR%>> aprswc_start.bat
echo SET APRSWC_TEMP_DIR=%APRSWC_TEMP_DIR%>> aprswc_start.bat
echo SET SERVER_PORT=%APRSWC_PORT%>> aprswc_start.bat
echo SET APRSWC_INSTALL_DIR=%APRSWC_INSTALL_DIR%>> aprswc_start.bat
echo REM Start Processes>> aprswc_start.bat
echo start java -jar aprs-welcome-center-%WC_VERSION%.jar>> aprswc_start.bat
echo echo APRS Welcome Center started>> aprswc_start.bat
echo popd>> aprswc_start.bat

echo Finishing up
popd

SET WC_INSTALL_DIR=
SET WC_TEMP_DIR=
SET WC_VERSION=
SET WC_TEMP_DIR=

echo.
echo Installation complete.
echo.
echo Run 'aprswc_start.bat' to start APRS Welcome Center.
exit /b 0

:done
exit /b 1