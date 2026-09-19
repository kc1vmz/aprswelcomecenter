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

Changes made to these settings may take up to 30 seconds to be recogniced by the application. For immediate response, stop and start the application.

#### Internet Access

You may check the "Use Internet Server" checkbox to connect to an APRS-IS server. Provide the necessary server information and credentials.

#### KISS Access

You may check the "User KISS" checkbox to use KISS to send and receive packets.  You can choose to use serial or TCP/IP communications. Provide the requested information to access the KISS interface of choice.

#### Digi path

All packets need a path to instruct local digipeaters how to repeat packets properly.  Set this to a reasonable value (HOP2-1 for example).

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

Each welcome center is listed on the left side of the main page.  The entry includes the callsign and name of the welcome center.

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


## Welcome Center status

Welcome Centers have an Open or Closed status, selectable in the create/edit dialog. New centers default to Open. Closing a center stops new active-object beacons, ignores inbound messages to its callsign (including acknowledgments and command responses), and suppresses automatic region-entry/exit messages. Pending commands and automatic outgoing messages recheck the current status and are discarded while Closed. Already queued object beacons may finish. Position tracking continues, and reopening does not replay discarded notifications. Manually initiated outgoing messages and bulletins remain available.

Status edits are committed before lifecycle actions run. Implement `onWelcomeCenterClosed(previous, updated)` and `onWelcomeCenterOpened(previous, updated)` in `WelcomeCenterLifecycle` to send object down/up packets and closure/opening bulletins. These methods are intentionally empty. They receive immutable snapshots, including old and new callsigns and coordinates. Creation and unchanged-status edits do not invoke the transition hooks. A simultaneous rename/status change invokes only the transition hook; a rename while remaining Open sends the existing down/up pair after commit. Lifecycle errors are logged without undoing or misreporting the committed edit; these hooks do not provide durable retry delivery.

The API uses `OPEN` and `CLOSED`. POST defaults an omitted status to `OPEN`; PUT requires a valid status.

## Database migration to Liquibase

Schema changes are now owned by Liquibase; Hibernate validates the result. Before upgrading an existing installation, stop APRSWC and back up its H2 database files. Do not run previous and new application versions against the same database simultaneously.

On startup, Liquibase validates the existing schema against the frozen H2 schema used by repository releases v0.1 through v1.0.2: required tables, column types/sizes/nullability, primary/unique keys, and foreign keys. Matching legacy databases are adopted automatically without deleting their data. Empty databases receive the baseline schema. Incompatible or partial legacy schemas stop startup before being baselined; they need a reviewed migration rather than a manually marked baseline.

The status changeset initializes existing centers to `OPEN`, then enforces the allowed values and a non-null default. Applied changesets are recorded in `DATABASECHANGELOG`; subsequent restarts preserve `CLOSED` values. Beacon scheduling begins only after successful application startup. Automatic adoption is for the configured H2 PUBLIC schema, not arbitrary database engines or unrecognized historical layouts. Do not edit applied changelog files; add changesets for future schema changes.

Verification: `mvn verify` includes fresh-install, legacy-upgrade, restart, incompatible-schema, status-transition, and communication-suppression tests. Station-list JavaScript tests run with `node --test src/test/js/station-list.test.cjs`.
