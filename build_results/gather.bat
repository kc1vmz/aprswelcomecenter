@echo off
rd /s/q target
mkdir target
echo Gathering files for upload to Github

copy ..\README.md target
copy ..\installers\linux\aprswc_installer.sh target
copy ..\installers\linux\aprswc_uninstaller.sh target
copy ..\installers\windows\aprswc_installer.bat target
copy ..\target\aprs-welcome-center-1.0.2.jar target
