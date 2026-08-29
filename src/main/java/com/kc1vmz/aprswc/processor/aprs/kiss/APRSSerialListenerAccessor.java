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

import com.kc1vmz.aprswc.constants.ApplicationToCallConstant;
import com.kc1vmz.aprswc.object.ApplicationSettings;
import com.kc1vmz.aprswc.object.StationPacket;
import com.kc1vmz.aprswc.processor.StationPacketQueue;
import com.kc1vmz.aprswc.utils.APRSTime;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.stream.Collectors;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class APRSSerialListenerAccessor {
    private static final Logger logger = LogManager.getLogger(APRSSerialListenerAccessor.class);

    @Autowired
    private APRSKISSListenerState aprsKISSListenerState;

    @Autowired
    private StationPacketQueue stationPacketQueue;

    private static final String PACKET_PROCESSOR_ID = "APRS_KISS_SERIAL";
    private KISSSerialClient client = null;
    private String digiPath = "";

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

    private List<String> getDigipeaterList() {
        List<String> ret = new ArrayList<>();
        if (digiPath == null) {
            digiPath = "";
        }
        String digipeaters = digiPath;
        if (digipeaters.contains(",")) {
            String[] digiArray = digipeaters.split(",");
            if (digiArray != null) {
                for (String digi : digiArray) {
                    ret.add(digi);
                }
            }
        } else {
            ret.add(digipeaters);
        }
        return ret;
    }

    public void sendMessage(String callsignFrom, String callsignTo, String messageText) {
        KISSPacket packet = new KISSPacket();
        packet.setCallsignFrom(callsignFrom);
        packet.setCallsignTo(callsignTo);
        packet.setApplicationName(ApplicationToCallConstant.TOCALL_NC1);
        packet.setDigipeaters(getDigipeaterList());
        packet.setData(messageText);
        packet.setValid(true);
        byte[] packetBytesAX = AX25PacketBuilder.buildPacket(packet, ":");
        byte[] packetBytes = KissPacketBuilder.build(packetBytesAX, (byte) 0);

        writeLock.lock();
        try {
            if (client != null) {
                client.write(packetBytes);
            } else {
                logger.error("client is null - cannot send");
            }
        } catch (Exception e) {
            logger.error("Exception caught sending message", e);
        } finally {
            writeLock.unlock();
        }
    }

    public void sendBulletin(String callsignFrom, String bulletinId, String messageText) {
        String callsignTo = bulletinId;
        KISSPacket packet = new KISSPacket();
        packet.setCallsignFrom(callsignFrom);
        packet.setCallsignTo(callsignTo);
        packet.setApplicationName(ApplicationToCallConstant.TOCALL_NC1);
        packet.setDigipeaters(getDigipeaterList());
        packet.setData(messageText);
        packet.setValid(true);
        byte[] packetBytesAX = AX25PacketBuilder.buildPacket(packet, ":");
        byte[] packetBytes = KissPacketBuilder.build(packetBytesAX, (byte) 0);

        writeLock.lock();
        try {
            if (client != null) {
                client.write(packetBytes);
            } else {
                logger.error("client is null - cannot send");
            }
        } catch (Exception e) {
            logger.error("Exception caught sending message", e);
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
        logger.info(String.format("Sending object %s: %s", objectName, messageText));
        KISSPacket packet = new KISSPacket();
        packet.setCallsignFrom(objectName);
        packet.setApplicationName(ApplicationToCallConstant.TOCALL_NC1);
        packet.setDigipeaters(getDigipeaterList());

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
                ";%s%s%s%s%s%s%s%s",
                String.format("%-9s", objectName), ud, time, lat, symbolTableId, lon, symbolTableCode, messageText);

        packet.setData(aprsMessage);
        packet.setValid(true);

        byte[] packetBytesAX = AX25PacketBuilder.buildObjectPacket(packet);
        byte[] packetBytes = KissPacketBuilder.build(packetBytesAX, (byte) 0);

        writeLock.lock();
        try {
            if (client != null) {
                client.write(packetBytes);
            } else {
                logger.error("client is null - cannot send");
            }
        } catch (Exception e) {
            logger.error("Exception caught sending message", e);
        } finally {
            writeLock.unlock();
        }
    }

    public void connectAndListen(ApplicationSettings applicationSettings) throws InterruptedException {
        if (applicationSettings != null) {
            digiPath = applicationSettings.getDigiPath();
        }

        boolean loop = true;
        while (loop) {
            try {
                if (!aprsKISSListenerState.isActive()) {
                    // reset this to active
                    aprsKISSListenerState.setActive(true);
                }
                connectAndListenMain(applicationSettings);
                if (aprsKISSListenerState.isRestart()) {
                    aprsKISSListenerState.setRestart(false);
                    return; // this will cause the upper loop to go again
                }
            } catch (InterruptedException e) {
                loop = false;
                break;
            } catch (Exception e) {
                logger.error("Exception caught in connectAndListen", e);
            }
        }
    }

    /**
     * connect to the KISS TNC and process messages
     *
     * @param server
     * @param auth
     * @param query
     */
    private void connectAndListenMain(ApplicationSettings applicationSettings) throws InterruptedException {
        logger.info("ListenerSerialAccessor connecting");
        boolean readLockEnabled = false;

        try {
            client = new KISSSerialClient();
            List<String> initCommands = getInitCommands(applicationSettings);
            client.connect(
                    applicationSettings.getKissPort(),
                    Integer.parseInt(applicationSettings.getKissBaudRate()),
                    initCommands);

            enableReception(client);

            // read response
            try {
                while (aprsKISSListenerState.isActive()) {
                    if (readLockEnabled) {
                        writeLock.lock();
                        try {
                            KISSPacket packet = client.listen();
                            if (packet != null) {
                                StationPacket stationPacket = new StationPacket(
                                        UUID.randomUUID(),
                                        PACKET_PROCESSOR_ID,
                                        packet.getCallsignFrom(),
                                        LocalDateTime.now(),
                                        buildData(packet),
                                        convertBytesToHexString(packet.getHeaderBytes()));
                                stationPacketQueue.offer(stationPacket);
                            }
                        } catch (IOException | NullPointerException e) {
                            // too noisy for logging
                            logger.error("Exception caught in KISS read loop", e);
                        } finally {
                            writeLock.unlock();
                        }
                    } else {
                        try {
                            KISSPacket packet = client.listen();
                            if (packet != null) {
                                StationPacket stationPacket = new StationPacket(
                                        UUID.randomUUID(),
                                        PACKET_PROCESSOR_ID,
                                        packet.getCallsignFrom(),
                                        LocalDateTime.now(),
                                        buildData(packet),
                                        convertBytesToHexString(packet.getHeaderBytes()));
                                stationPacketQueue.offer(stationPacket);
                            }
                        } catch (IOException | NullPointerException e) {
                            // too noisy for logging
                            logger.error("Exception caught in KISS read loop", e);
                        }
                    }
                    if (aprsKISSListenerState.isRestart()) {
                        break;
                    }
                }
            } catch (Exception e) {
                logger.info("Exception caught in packet read loop", e);
            }

            // close client
            try {
                client.disconnect();
            } catch (Exception e) {
                logger.error("Exception caught", e);
            }

        } catch (Exception e) {
            logger.error("Exception caught creating TNC connection", e);
        }
    }

    private String buildData(KISSPacket packet) {
        // need to massage the packet for upcall
        String digis = "";
        if ((packet.getDigipeaters() != null) && (!packet.getDigipeaters().isEmpty())) {
            digis = packet.getDigipeaters().stream().map(String::toUpperCase).collect(Collectors.joining(","));
        }
        return String.format("%s>%s:%s", packet.getCallsignFrom(), digis, packet.getData());
    }

    private List<String> getInitCommands(ApplicationSettings applicationSettings) {
        List<String> ret = new ArrayList<>();
        String initCommand1 = applicationSettings.getKissInitCommand1();
        if ((initCommand1 != null) && (!initCommand1.isEmpty())) {
            ret.add(initCommand1 + "\r");
        }
        String initCommand2 = applicationSettings.getKissInitCommand2();
        if ((initCommand2 != null) && (!initCommand2.isEmpty())) {
            ret.add(initCommand2 + "\r");
        }
        return ret;
    }

    private String convertBytesToHexString(byte[] bytes) {
        return HexFormat.of().withUpperCase().formatHex(bytes);
    }

    private static void enableReception(KISSSerialClient client) {
        try {
            client.enableReception();
        } catch (IOException e) {
            logger.error("Exception caught", e);
        }
    }
}
