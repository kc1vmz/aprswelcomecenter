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
package com.kc1vmz.aprswc.processor.aprs.is;

import com.kc1vmz.aprswc.accessor.ApplicationSettingsAccessor;
import com.kc1vmz.aprswc.constants.ApplicationToCallConstant;
import com.kc1vmz.aprswc.object.ApplicationSettings;
import com.kc1vmz.aprswc.object.StationPacket;
import com.kc1vmz.aprswc.processor.StationPacketQueue;
import com.kc1vmz.aprswc.utils.APRSTime;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.time.LocalDateTime;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class APRSInternetServerListenerAccessor {
    private static final Logger logger = LogManager.getLogger(APRSInternetServerListenerAccessor.class);

    private PrintWriter sender = null;
    private static final String PACKET_PROCESSOR_ID = "APRS_IS";

    @Autowired
    private ApplicationSettingsAccessor applicationSettingsAccessor;

    @Autowired
    private APRSInternetServerListenerState aprsInternetServerListenerState;

    @Autowired
    private APRSUtilityAccessor aprsUtilityAccessor;

    @Autowired
    private StationPacketQueue stationPacketQueue;

    private final ReadWriteLock readWriteLock = new ReentrantReadWriteLock();
    private final Lock writeLock = readWriteLock.writeLock();

    public List<String> getPacketProcessorIds() {
        List<String> ret = new ArrayList<>();
        ret.add(PACKET_PROCESSOR_ID);
        return ret;
    }

    public void sendQuery(String callsignFrom, String callsignTo, String queryType) {
        sendMessage(callsignFrom, callsignTo, String.format("?%-5s", queryType));
    }

    public void sendMessage(String callsignFrom, String callsignTo, String messageText) {
        String aprsMessage = String.format(
                "%s>%s,TCPIP*::%-9s:%s\r\n",
                callsignFrom, ApplicationToCallConstant.TOCALL_NC1, callsignTo, messageText);

        writeLock.lock();
        try {
            if (sender != null) {
                sender.write(aprsMessage);
                sender.flush();
            } else {
                logger.error("Sender is null - cannot send to APRS-IS server");
            }
        } finally {
            writeLock.unlock();
        }
    }

    public void sendBulletin(String callsignFrom, String bulletinId, String messageText) {
        String aprsMessage = callsignFrom + ">" + ApplicationToCallConstant.TOCALL_NC1 + ",TCPIP*::" + bulletinId
                + "     :" + messageText + "\r\n";

        writeLock.lock();
        try {
            if (sender != null) {
                sender.write(aprsMessage);
                sender.flush();
            } else {
                logger.error("Sender is null - cannot send to APRS-IS server");
            }
        } finally {
            writeLock.unlock();
        }
    }

    public void sendObject(
            String objectName,
            String messageText,
            boolean alive,
            String lat,
            String lon,
            String symbolTableId,
            String symbolTableCode) {
        String aprsMessage = null;

        String ud = "";
        if (alive) {
            ud = "*";
        } else {
            ud = "_";
        }

        // time[7]lat[8]sym[/]lon[9]sym[>]meta[7]comment[36]
        String time = APRSTime.convertZonedDateTimeToDDHHMM(ZonedDateTime.now());

        aprsMessage = String.format(
                "%s>%s,TCPIP*:;%s%s%s%s%s%s%s%s\r\n",
                objectName,
                ApplicationToCallConstant.TOCALL_NC1,
                String.format("%-9s", objectName),
                ud,
                time,
                lat,
                symbolTableId,
                lon,
                symbolTableCode,
                messageText);
        writeLock.lock();
        try {
            if (sender != null) {
                sender.write(aprsMessage);
                sender.flush();
            } else {
                logger.error("Sender is null - cannot send to APRS-IS server");
            }
        } finally {
            writeLock.unlock();
        }
    }

    public void connectAndListen() throws InterruptedException {
        boolean loop = true;
        while (loop) {
            try {
                aprsInternetServerListenerState.setRestart(false);
                boolean startAPRSIS = false;
                ApplicationSettings applicationSettings = null;
                List<ApplicationSettings> settingsList =
                        applicationSettingsAccessor.findAll().collectList().block();
                if ((settingsList != null) && (settingsList.size() > 0)) {
                    applicationSettings = settingsList.getFirst();
                    startAPRSIS = applicationSettings.isUsingInternetServer();
                }
                if (startAPRSIS) {
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
        if (!applicationSettings.isUsingInternetServer()) {
            return;
        }
        APRSServer server = new APRSServer(
                applicationSettings.getInternetServerAddress(),
                Integer.parseInt(applicationSettings.getInternetServerPort()));
        APRSAuthenticationInfo auth = new APRSAuthenticationInfo(
                applicationSettings.getInternetServerUsername(), applicationSettings.getInternetServerPasscode());
        String query = applicationSettings.getFilter();

        boolean loop = true;
        while (loop) {
            try {
                if (!aprsInternetServerListenerState.isActive()) {
                    // reset this to active
                    aprsInternetServerListenerState.setActive(true);
                }
                connectAndListen(server, auth, query);
                if (aprsInternetServerListenerState.isRestart()) {
                    aprsInternetServerListenerState.setRestart(false);
                    return; // this will cause the upper loop to go again
                }
            } catch (InterruptedException e) {
                loop = false;
                break;
            } catch (Exception e) {
                logger.error("Exception caught in listener loop", e);
            }
        }
    }

    /**
     * connect to the APRS-IS server and process messages
     *
     * @param server
     * @param auth
     * @param query
     */
    private void connectAndListen(APRSServer server, APRSAuthenticationInfo auth, String query)
            throws InterruptedException {
        try {
            // Send Auth
            String authStr = aprsUtilityAccessor.generateAuthStr(auth, query);

            Socket sock = new Socket(server.getAddress(), server.getPort());
            BufferedReader in = new BufferedReader(new InputStreamReader(sock.getInputStream()));
            PrintWriter out = new PrintWriter(sock.getOutputStream());
            sender = out;

            out.println(authStr);
            out.flush();

            try {
                Thread.sleep(3000); // 3 sec
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
            }

            // read response
            String authenticationResponse = in.readLine();
            logger.info(authenticationResponse);

            // read response
            try {
                while (aprsInternetServerListenerState.isActive()) {
                    String line = null;
                    writeLock.lock();
                    try {
                        line = in.readLine();
                    } finally {
                        writeLock.unlock();
                    }

                    if (line == null) {
                        logger.error("Socket closed; line is null");
                        aprsInternetServerListenerState.setActive(false);
                    } else {
                        if (!line.startsWith("#")) {
                            StationPacket packet = new StationPacket(
                                    UUID.randomUUID(), PACKET_PROCESSOR_ID, null, LocalDateTime.now(), line, null);
                            stationPacketQueue.offer(packet);
                        }
                    }
                    if (aprsInternetServerListenerState.isRestart()) {
                        break;
                    }
                }
            } catch (Exception e) {
                logger.error("Exception caught in packet read loop", e);
            }

            // close socket
            try {
                in.close();
                out.close();
                sock.close();
            } catch (IOException e) {
                logger.error("IOException caught", e);
            }

        } catch (IOException e) {
            logger.error("IOException caught creating connection", e);
        }
    }
}
