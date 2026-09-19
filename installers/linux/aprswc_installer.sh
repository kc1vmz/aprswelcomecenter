#!/bin/bash
# APRS Welcome Center Installer
# Copyright (c) 2026 John Rokicki KC1VMZ
# 
# This program is free software: you can redistribute it and/or modify
# it under the terms of the GNU General Public License as published by
# the Free Software Foundation, either version 3 of the License, or
# (at your option) any later version.
# 
# This program is distributed in the hope that it will be useful,
# but WITHOUT ANY WARRANTY; without even the implied warranty of
# MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
# GNU General Public License for more details.
# 
# You should have received a copy of the GNU General Public License
# along with this program.  If not, see <https://www.gnu.org/licenses/>.
#
# http://www.kc1vmz.com
# 
WC_UPGRADE=N
WC_OS=$(uname)
if [ "$WC_OS" = "Linux" ]; then
  echo "Welcome to the APRS Welcome Center installer for Linux"
  echo "Copyright (c) 2026 John Rokicki KC1VMZ - Licensed under GPL v3"
  echo "https://www.kc1vmz.com"

  if grep -q APRSWC_INSTALL_DIR /etc/environment; then
    echo "A previous version of APRS Welcome Center was detected."
    WC_UPGRADE=Y
    read -e -i $WC_UPGRADE -p "Do you wish to upgrade APRS Welcome Center?: " WC_UPGRADE
    if [[ "$WC_UPGRADE" =~ ^[Nn]$ ]]; then
      echo "APRS Welcome Center installation will now exit."
      exit 1
    fi
  fi

  # default environment variables
  WC_VERSION=1.0.3
  WC_INSTALL_DIR=~/aprswelcomecenter
  WC_DB_DIR=~/aprswelcomecenter/db
  WC_TEMP_DIR=~/aprswelcomecenter/tmp
  WC_PORT=8080
  WC_INSTALL_SERVICES=Y

  if [[ "$WC_INSTALL_SERVICES" =~ ^[Yy]$ ]]; then
    read -e -i $WC_VERSION -p "What version of APRS Welcome Center?: " WC_VERSION
    if [[ "$WC_UPGRADE" =~ ^[Nn]$ ]]; then
      read -e -i $WC_INSTALL_DIR -p "Where should APRS Welcome Center be installed?: " WC_INSTALL_DIR
      read -e -i $WC_DB_DIR -p "Where should APRS Welcome Center database space be located?: " WC_DB_DIR
      read -e -i $WC_TEMP_DIR -p "Where should APRS Welcome Center temp space be located?: " WC_TEMP_DIR
      read -e -i $WC_PORT -p "What port should the HTTP service listen on?: " WC_PORT
      read -e -i $WC_INSTALL_SERVICES -p "Do you want APRS Welcome Center to be configured as services and started at boot time (Y/n)?: " WC_INSTALL_SERVICES
    else
      APRSWC_INSTALL_DIR=$(grep "^APRSWC_INSTALL_DIR=" /etc/environment | sed 's/^APRSWC_INSTALL_DIR=//' | tr -d '"')
      WC_INSTALL_DIR=$APRSWC_INSTALL_DIR
    fi
  fi

  sudo cp /etc/environment /etc/environment.pre_aprswc_install

  JAVA_VER=$(java -version 2>&1 | awk -F '"' '/version/ {print $2}' | awk -F '.' '{sub("^$", "0", $2); print $1$2}')
  if [ "$JAVA_VER" -ge 21 ]; then
    # fall through
    echo "Java 21+ verified"
  else
    echo "Java 21 or greater not installed - exiting."
    sudo rm /etc/environment.pre_aprswc_install
    exit 1
  fi

  APRSWC_INSTALL_DIR=$WC_INSTALL_DIR
  if [[ "$WC_UPGRADE" =~ ^[Nn]$ ]]; then
    echo "APRSWC_INSTALL_DIR=$APRSWC_INSTALL_DIR" | sudo tee -a /etc/environment >  /dev/null
    mkdir $WC_INSTALL_DIR
    sudo mkdir $WC_TEMP_DIR
    sudo chown $USER $WC_TEMP_DIR
    sudo mkdir $WC_DB_DIR
    sudo chown $USER $WC_DB_DIR
  fi

  echo "Retrieving APRS Welcome Center binary"
  cd $WC_INSTALL_DIR
  WC_SRC_URL_ROOT=https://github.com/kc1vmz/aprswelcomecenter/releases/download/v$WC_VERSION
  wget -q -O aprs-welcome-center-$WC_VERSION.jar $WC_SRC_URL_ROOT/aprs-welcome-center-$WC_VERSION.jar
  WC_SRC_URL_ROOT=

  chmod +x aprs-welcome-center-$WC_VERSION.jar

  if [[ "$WC_UPGRADE" =~ ^[Nn]$ ]]; then
    APRSWC_DB_DIR=$WC_DB_DIR
    APRSWC_TEMP_DIR=$WC_TEMP_DIR
    APRSWC_PORT=$WC_PORT
  else
    if [ -n "/etc/systemd/system/aprs-welcome-center.service+x" ]; then
      WC_INSTALL_SERVICES=Y
    else
      WC_INSTALL_SERVICES=N
    fi
  fi

  if [[ "$WC_INSTALL_SERVICES" =~ ^[Yy]$ ]]; then
    if [[ "$WC_UPGRADE" =~ ^[Nn]$ ]]; then
      echo '[Unit]' | sudo tee -a /etc/systemd/system/aprs-welcome-center.service >  /dev/null
      echo 'Description=Net Central Server' | sudo tee -a /etc/systemd/system/aprs-welcome-center.service >  /dev/null
      echo '[Service]' | sudo tee -a /etc/systemd/system/aprs-welcome-center.service >  /dev/null
      echo 'User='$LOGNAME | sudo tee -a /etc/systemd/system/aprs-welcome-center.service >  /dev/null
      echo 'WorkingDirectory='$WC_INSTALL_DIR | sudo tee -a /etc/systemd/system/aprs-welcome-center.service >  /dev/null
      echo 'Type=simple' | sudo tee -a /etc/systemd/system/aprs-welcome-center.service >  /dev/null
      echo 'Restart=always' | sudo tee -a /etc/systemd/system/aprs-welcome-center.service >  /dev/null
      echo 'SuccessExitStatus=143' | sudo tee -a /etc/systemd/system/aprs-welcome-center.service >  /dev/null
      echo 'RemainAfterExit=yes' | sudo tee -a /etc/systemd/system/aprs-welcome-center.service >  /dev/null
      echo 'ExecStart=java -jar '$WC_INSTALL_DIR'/aprs-welcome-center-'$WC_VERSION'.jar' | sudo tee -a /etc/systemd/system/aprs-welcome-center.service >  /dev/null
      echo 'Environment=APRSWC_DB_DIR='$APRSWC_DB_DIR | sudo tee -a /etc/systemd/system/aprs-welcome-center.service >  /dev/null
      echo 'Environment=APRSWC_TEMP_DIR='$APRSWC_TEMP_DIR | sudo tee -a /etc/systemd/system/aprs-welcome-center.service >  /dev/null
      echo 'Environment=SERVER_PORT='$APRSWC_PORT | sudo tee -a /etc/systemd/system/aprs-welcome-center.service >  /dev/null
      echo 'Environment=APRSWC_INSTALL_DIR='$APRSWC_INSTALL_DIR | sudo tee -a /etc/systemd/system/aprs-welcome-center.service >  /dev/null
      echo '[Install]' | sudo tee -a /etc/systemd/system/aprs-welcome-center.service >  /dev/null
      echo 'WantedBy=multi-user.target' | sudo tee -a /etc/systemd/system/aprs-welcome-center.service >  /dev/null
      sudo systemctl daemon-reload
      sudo systemctl enable aprs-welcome-center
      sudo systemctl start aprs-welcome-center
      echo "APRS Welcome Center running as background service."
    else
      sudo systemctl stop aprs-welcome-center
      sudo sed -i 's/aprs-welcome-center-.*.jar/aprs-welcome-center-'$WC_VERSION'.jar/g' /etc/systemd/system/aprs-welcome-center.service
      sudo systemctl daemon-reload
      sudo systemctl start aprs-welcome-center
      echo "APRS Welcome Center restarted."
    fi
  else
    echo "Environment variables have been added to /etc/environment.  Make sure to reboot or run 'source /etc/environment' to add them into any new terminal."
    echo "You can run APRS Welcome Center using the  'java -jar $WC_INSTALL_DIR/aprs-welcome-center-$WC_VERSION.jar' command"
  fi

  # cleanup environment
  WC_INSTALL_DIR=
  WC_TEMP_DIR=
  WC_VERSION=
  WC_INSTALL_SERVICES=
  WC_DB_DIR=
  WC_PORT=
  WC_UPGRADE=
elif [ "$WC_OS" = "Darwin" ]; then
  echo "This is a Mac Machine - not yet supported"
  exit 1
else
  echo "Unsupported OS -" $WC_OS
  exit 1
fi

echo "APRS Welcome Center installation complete."