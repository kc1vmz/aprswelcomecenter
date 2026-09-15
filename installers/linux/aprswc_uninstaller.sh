#!/bin/bash
WC_OS=$(uname)
if [ "$WC_OS" = "Linux" ]; then
  echo "Welcome to the APRS Welcome Center UNinstaller for Linux"

  # default environment variables
  WC_CONFIRM_UNINSTALL=Y

  read -e -i $WC_CONFIRM_UNINSTALL -p "Do you want to uninstall APRS Welcome Center (Y/n)?: " WC_CONFIRM_UNINSTALL

  if [[ "$WC_CONFIRM_UNINSTALL" =~ ^[Yy]$ ]]; then
    # fall through
    echo Starting uninstall of APRS Welcome Center
  else
    #any other answer - exit
    exit 1
  fi

  echo Backing up /etc/environment
  sudo cp /etc/environment /etc/environment.pre_aprswc_uninstall

  echo Removing environment variables from /etc/environment
  sudo sed -i '/APRSWC_INSTALL_DIR/d' /etc/environment

  echo Removing services
  sudo systemctl stop aprs-welcome-center
  sudo systemctl disable aprs-welcome-center
  sudo rm /etc/systemd/system/aprs-welcome-center.service
  sudo systemctl daemon-reload

  if [ -n "${APRSWC_INSTALL_DIR+x}" ]; then
    echo Deleting APRS Welcome Center application
    sudo rm aprs-welcome-center.jar
    echo Not deleting ${APRSWC_INSTALL_DIR} - delete independently.
  else
    echo APRS Welcome Center installation directory unknown - delete independently.
  fi

  if [ -n "${APRSWC_TEMP_DIR+x}" ]; then
    echo Not deleting ${APRSWC_TEMP_DIR} - delete independently.
  else
    echo APRS Welcome Center temporary directory unknown - delete independently.
  fi

  WC_CONFIRM_UNINSTALL=

elif [ "$WC_OS" = "Darwin" ]; then
  echo "This is a Mac Machine - not yet supported"
  exit 1
else
  echo "Unsupported OS -" $WC_OS
  exit 1
fi

echo "APRS Welcome Center uninstallation complete."