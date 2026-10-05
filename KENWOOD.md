# Kenwood Serial

Choose **Kenwood Serial** in Communications. Set the serial device, baud rate, MYCALL and optional digipeater path. The label is derived automatically. Multiple instances are supported; active serial connections cannot share a port with Kenwood, KISS or TNC2.

Use the TM-D710G's packet/command TNC mode. Configure the radio's frequency and packet speed on the radio; serial baud rate is separate. The connection uses 8N1 without flow control. D75/D750 compatibility has not been verified.

Startup automatically sends RESET, MYCALL, UNPROTO and monitor settings. Do not supply custom initialization commands. Received packets continue to be read during transmission. Failed connections reconnect through the existing communication worker.

All RF packets originate from the configured MYCALL. APRS message destinations still select the welcome center or point of interest; object beacons retain their object names. Choose a MYCALL appropriate to the managed welcome center or point of interest. No separate installation-message inbox is added.

Welcome Center's existing ACK/REJ behavior is preserved: incoming numbered commands receive the existing acknowledgment or rejection, on the receiving connection. Received ACK/REJ packets are ignored because Welcome Center does not retry outbound messages. Fixed MYCALL can differ from a logical object's callsign, so a remote client's acknowledgment matching may depend on its implementation.

**Allow transmission: No** keeps reception active while blocking messages and beacons. Existing connections default to allowing transmission. Paused stops the connection entirely. The database migration preserves existing settings and adds the new type and transmission flag.

Automated tests use a simulated command-mode radio. Physical-radio validation is still required.
