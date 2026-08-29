/*
 *
 * APRSWelcomeCenter
 * Copyright (c) 2026 John Rokicki KC1VMZ
 *
 * This program is free software: you can redistribute it and/or modify it under the terms of the GNU General Public
 * License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any later
 * version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied
 * warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with this program. If not, see
 * https://www.gnu.org/licenses/.
 *
 * http://www.kc1vmz.com
 */
package com.kc1vmz.aprswc.processor.aprs.kiss;

import com.kc1vmz.aprswc.accessor.ApplicationSettingsAccessor;
import com.kc1vmz.aprswc.object.ApplicationSettings;
import java.util.ArrayList;
import java.util.List;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class APRSKISSListenerAccessor {
    private static final Logger logger = LogManager.getLogger(APRSKISSListenerAccessor.class);

    @Autowired
    private ApplicationSettingsAccessor applicationSettingsAccessor;

    @Autowired
    private APRSSerialListenerAccessor aprsSerialListenerAccessor;

    @Autowired
    private APRSTCPIPListenerAccessor aprsTCPIPListenerAccessor;

    @Autowired
    private APRSKISSListenerState aprsKISSListenerState;

    private int communicationType = 0;
    private static final int TYPE_UNKNOWN = 0;
    private static final int TYPE_SERIAL = 1;
    private static final int TYPE_TCPIP = 2;

    public List<String> getPacketProcessorIds() {
        List<String> ret = new ArrayList<>();
        ret.addAll(aprsSerialListenerAccessor.getPacketProcessorIds());
        ret.addAll(aprsTCPIPListenerAccessor.getPacketProcessorIds());
        return ret;
    }

    public void sendQuery(String callsignFrom, String callsignTo, String queryType) {
        switch (communicationType) {
            case 1:
                aprsSerialListenerAccessor.sendQuery(callsignFrom, callsignTo, queryType);
                break;
            case 2:
                aprsTCPIPListenerAccessor.sendQuery(callsignFrom, callsignTo, queryType);
                break;
            default:
                break;
        }
    }

    public void sendMessage(String callsignFrom, String callsignTo, String messageText) {
        switch (communicationType) {
            case 1:
                aprsSerialListenerAccessor.sendMessage(callsignFrom, callsignTo, messageText);
                break;
            case 2:
                aprsTCPIPListenerAccessor.sendMessage(callsignFrom, callsignTo, messageText);
                break;
            default:
                break;
        }
    }

    public void sendBulletin(String callsignFrom, String bulletinId, String messageText) {
        switch (communicationType) {
            case 1:
                aprsSerialListenerAccessor.sendBulletin(callsignFrom, bulletinId, messageText);
                break;
            case 2:
                aprsTCPIPListenerAccessor.sendBulletin(callsignFrom, bulletinId, messageText);
                break;
            default:
                break;
        }
    }

    public void sendObject(
            String objectName,
            String messageText,
            boolean alive,
            String lat,
            String lon,
            String symbolTableId,
            String symbolTableCode) {}

    public void connectAndListen() throws InterruptedException {
        boolean loop = true;
        while (loop) {
            try {
                aprsKISSListenerState.setRestart(false);
                communicationType = TYPE_UNKNOWN;
                boolean startKISS = false;
                ApplicationSettings applicationSettings = null;
                List<ApplicationSettings> settingsList =
                        applicationSettingsAccessor.findAll().collectList().block();
                if ((settingsList != null) && (settingsList.size() > 0)) {
                    applicationSettings = settingsList.getFirst();
                    startKISS = applicationSettings.isUsingKISS();
                }
                if (startKISS) {
                    connectAndListen(applicationSettings);
                } else {
                    Thread.sleep(30000); // sleep for 30 seconds - check again
                }
            } catch (InterruptedException e) {
                loop = false;
                break;
            } catch (Exception e) {
                logger.error("Exception caught in upper listener loop", e);
            }
        }
    }

    public void connectAndListen(ApplicationSettings applicationSettings) throws InterruptedException {
        if (!applicationSettings.isUsingKISS()) {
            communicationType = TYPE_UNKNOWN;
            return;
        }
        if ((applicationSettings.getKissHost() == null)
                || (applicationSettings.getKissHost().isEmpty())) {
            // assume serial communications
            communicationType = TYPE_SERIAL;
            aprsSerialListenerAccessor.connectAndListen(applicationSettings);
        } else {
            communicationType = TYPE_TCPIP;
            aprsTCPIPListenerAccessor.connectAndListen(applicationSettings);
        }
    }
}
