# APRS Welcome Center (APRSWC)



APRS Welcome Center is an APRS-based messaging app that can provide timely information to other APRS-enabled operators in an area.



## Terminology



A Welcome Center is a callsign (real or tactical) that will be used for communicating with other APRS stations within the Regions you define.



A Region is a rectangular or circlular area that the Welcome Center should concern itself.



A Communication Plan is the set of Communication Policies used to govern communication of information by a Welcome Center.



A Communication Policy defines what information is sent and when, such as when stations enter or leave the Welcome Center's regions.



A Station is an APRS-enabled device that has identified itself to APRSWC.





## Main Screen



The main web page has a header, followed by two columns - the list of Welcome Centers created and list of Stations heard.





### Header



The header contains multiple buttons:

- New Welcome Center

- Packet list (radio beacon icon)

- Applicaton Configuration (gear icon)

- Light / Dark Mode toggle (sun or moon icon)

- About (information icon)



#### New Welcome Center



To create a new Welcome Center, press the New Welcome Center button.



You will be asked for information specific to the Welcome Center.  The callsign (including -SSID) must be unique as it will be used for APRS communication.



Once created, you may specify regions and communication plans.  You may also see the weather reports it has collected from weather stations in its regions, the stations within its regions, and the messages sent by the welcome center.



### Packet list



Pressing this button will show a list of packets heard by APRS Welcome Center application.

You can delete all of the heard packets from this dialog.



### Application Configuration



APRS Welcome Center must be configured to access APRS-related information, whether it be via radio with KISS, or via internet from an APRS-IS server.

Press the Configuration button in the top right corner (gear icon) to configure your APRS information access.



#### Communication instances

Use **Add connection** to configure an APRS-IS, KISS TCP, or KISS Serial connection. You can run multiple connections of each type. Each connection has its own settings and UUID; its displayed label is derived from the endpoint or serial device.

New connections default to **Active**. **Pause** disconnects a connection; **Resume** starts it again. Saving an edit stops the previous worker before starting its replacement. Deleting a connection stops it and preserves received packet history. Changes take effect after saving, without restarting the application. Stale edits and deletes are rejected; refresh and review the current configuration before trying again.

The list shows the desired state and current connection health separately. Active connections retry connection failures with a delay. Use **Refresh status** to update the health display. Two active serial connections cannot share a device; duplicate TCP endpoints produce a warning. An empty passcode while editing retains the existing passcode. Passcodes are not returned by the API or displayed in the list.

Each KISS connection has its own digipeater path and each serial connection has its own baud rate and optional initialization commands. Map preferences remain application-wide.

Directed messages use their explicit communication instance, or the destination station's most recently received packet when no instance is specified. Unavailable routes and full outgoing queues drop messages. Object beacons and bulletins go to all active, connected instances. A sent timestamp means the transport write completed; it does not imply an APRS acknowledgement.

#### Packet and position retention

Set **Retain packets and positions for (days)** to a whole number greater than zero (default: 1). A dedicated background worker deletes packets received and positions created before the current date/time minus this number of days, once after startup and then every 24 hours. It reads the current setting each run, so changes require no restart. Stations and messages have separate retention periods. Packet-based last-heard times and automatic message routing are no longer available for stations whose packets have all expired.

#### Station and message retention

**Retain inactive stations for (days)** defaults to 10. Stations expire when their saved last-activity timestamp is older than the current time minus this period. Packet reception updates this timestamp, which survives packet cleanup. Station deletion removes its remaining position reports; packets and messages follow their own retention settings.

**Retain messages for (days)** defaults to 10. Messages expire based on sent time, or creation time when unsent. Both settings require positive whole numbers. A dedicated worker runs after startup and every 24 hours, reading the current configuration each run. Existing stations use available packet/position history for their initial activity timestamp; those without history, and unsent messages without an age, start aging from the upgrade.

#### Map tile URL



By default, map tile information is retrieved from the specified URL.  If you wish to use another OSM-compliant tile server, specify the full tile URL pattern here.



### Addditional configuration options



#### Stations



The Application Configuration also allow you to delete all heard stations. To delete all heard stations, press the "Delete Stations" button.



#### Ignored Stations



The Application Configuration also allow you to ignore packets from stations by callsign.  Those callsigns can also opt-out of messages and will show up in the list of ignored stations.





### About



The About dialog provides information about APRS Welcome Center including its version, copyright and license information.







## Welcome Centers



Each welcome center is listed on the left side of the main page.  The entry includes the callsign and name of the welcome center, as well as its current state (Open or Closed).



### Open / Close



Can set the state of the Welcome Center.  Closed Welcome Centers will not send messages nor respond to commands.



### Details



The details of the welcome center can be shown and modified here.



### Regions



The set of welcome regions assigned to the welcome center are listed here.  You may add, edit, or delete regions.

Regions can be defined knowing the shape and coordinates, or use the map to establish the region visually.



### Communications Plan



The set of communication policies in effect for the welcome center are listed here. You may add, edit, or delete communication policies.

Each comunication policy establishes what information is provided to an APRS device in a welcome center region, whether on entry or exit to the region.

Different types of information can be provided automatically.



### Weather



APRS weather station reports in the welcome station areas are collected and averaged, and then made available to stations.



### Stations



The set of stations found within the regions of a welcome center are listed or mapped here.



### Messages



The set of messages sent by a welcome center to APRS devices are found here.





## Stations



The list of heard stations are shown in a column to the right in the main UI page.

Each entry only shows its callsign.

You may click on the "Details" button for more information about the station.



### Sorting and filtering stations



Use **Sort & filter** beside the Stations heading to sort by callsign (A-Z) or last heard (newest first). Filter to All, the last hour, or the last 3, 6, 24, or 48 hours. Apply refreshes the list, and your browser remembers the selected options.



Last heard is the latest received time in stored packet history, displayed in your browser's local time. Stations without packet history appear under All with an Unknown time. Deleting packet history also removes that history from the last-heard calculation.



### Station Details



The station details include the callsign, the list of welcome centers that the callsign is positioned.

You can also see all of the messages sent to this callsign by APRS Welcome Center and all the position packets heard.

This station can also be added to the "Ignore Station" list.

The station can also be deleted, but will be readed if heard again and not being ignored.





## Station actions



An APRS-enabled station is capable of performing or triggering several activities with APRS Welcome Center.



### Entering a Welcome Center region



If an APRS-enabled station is moving and enters a Welcome Center region, the Communication Plan for the Welcome Center will define what messages will be sent to it.



### Exiting a Welcome Center region



If an APRS-enabled station is moving and exits a Welcome Center region, the Communication Plan for the Welcome Center will define what messages will be sent to it.



### Station-initiated commands



Any station knowing the callsign of a Welcome Center can send it messages for more information.

Usually the Welcome Center Communication Plan will have a welcome message sent to a station entering the Welcome Center's region, so the station would then know the callsign.



#### Help



Sending the message "HELP" to the welcome center's callsign will trigger the welcome center to respond with a help message on what commands are available.



#### Info



Sending the message "INFO" to the welcome center's callsign will trigger the welcome center to respond with information about the welcome center.



#### Stop



Sending the message "STOP" to the welcome center's callsign will trigger the welcome center to add the sender's callsign to the Ignored Stations list.



#### Start



Sending the message "START" to the welcome center's callsign will trigger the welcome center to remove the sender's callsign from the Ignored Stations list.



#### Weather



Sending the message "WEATHER" to the welcome center's callsign will trigger the welcome center to respond with a weather summary message.



#### Others



Sending the message "OTHERS" to the welcome center's callsign will trigger the welcome center to respond with callsigns of other stations within the regions of the welcome center.



#### Comm



Sending the message "COMM" to the welcome center's callsign will trigger the welcome center to respond with information about local commercial radio, as defined in the Communication Plan.



#### Voice



Sending the message "VOICE" to the welcome center's callsign will trigger the welcome center to respond with information about local amateur radio voice infrastructure, as defined in the Communication Plan.



#### Clubs



Sending the message "CLUBS" to the welcome center's callsign will trigger the welcome center to respond with information about local amateur radio clubs, as defined in the Communication Plan.



#### Events



Sending the message "EVENTS" to the welcome center's callsign will trigger the welcome center to respond with information about local events, as defined in the Communication Plan.



#### Warnings



Sending the message "WARNINGS" to the welcome center's callsign will trigger the welcome center to respond with information about local warnings, as defined in the Communication Plan.



## Installation



APRS Welcome Center does not need an installation, but installation scripts are provided in GitHub to easily download and configure it to run on Linux and Windows.



See aprswc_installer.bat for Microsoft Windows and aprswc_installer.sh for Linux.  Both are located included in each release, and are located in source in .\installers\windows and ./installers/linux.



## Run



```shell

java -jar aprs-welcome-center-1.0.2.jar

```



Open `http://localhost:8080`. The durable database is created below `./data`.


