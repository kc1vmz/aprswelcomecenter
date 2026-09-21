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
package com.kc1vmz.aprswc.communication;

import com.kc1vmz.aprswc.processor.StationPacketQueue;
import java.time.LocalDateTime;
import java.util.concurrent.*;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

/** Owns the reader, bounded writer queue and cancellable transport for a single configuration. */
public class ManagedPacketListener {
    public interface Send {
        void run(CommunicationTransport transport) throws Exception;
    }

    private record Pending(CommunicationTransport connection, Send send, BooleanSupplier permitted, Runnable onSent) {}

    public final CommunicationConfig config;
    private final StationPacketQueue packets;
    private final Supplier<CommunicationTransport> factory;
    private final BlockingQueue<Pending> outgoing = new ArrayBlockingQueue<>(100);
    private final Thread reader;
    private final Thread writer;
    private volatile CommunicationTransport connection;
    private volatile boolean stopping;
    private volatile String health = "CONNECTING";
    private volatile String lastError;
    private volatile LocalDateTime lastPacketTime;
    private volatile long writingSince;
    private volatile CommunicationTransport writingConnection;

    public ManagedPacketListener(
            CommunicationConfig config, StationPacketQueue packets, Supplier<CommunicationTransport> factory) {
        this.config = config;
        this.packets = packets;
        this.factory = factory;
        reader = new Thread(this::readLoop, "Communication-reader-" + config.id());
        writer = new Thread(this::writeLoop, "Communication-writer-" + config.id());
        reader.setDaemon(true);
        writer.setDaemon(true);
    }

    public void start() {
        reader.start();
        writer.start();
    }

    private void readLoop() {
        long delay = 1000;
        while (!stopping) {
            CommunicationTransport transport = factory.get();
            connection = transport;
            try {
                if (stopping) break;
                health = "CONNECTING";
                transport.connect();
                if (stopping) break;
                health = "CONNECTED";
                lastError = null;
                while (!stopping) {
                    var packet = transport.read();
                    if (packet != null) {
                        lastPacketTime = LocalDateTime.now();
                        packets.offer(packet);
                        delay = 1000;
                    }
                }
            } catch (Exception failure) {
                // Do not expose exception messages containing credentials or untrusted peer data.
                lastError = "Connection failed (" + failure.getClass().getSimpleName() + ")";
            } finally {
                health = stopping ? "STOPPING" : "RETRYING";
                transport.close();
                connection = null;
                outgoing.clear();
            }
            if (!stopping)
                try {
                    Thread.sleep(delay);
                    delay = Math.min(30000, delay * 2);
                } catch (InterruptedException ignored) {
                    Thread.currentThread().interrupt();
                    break;
                }
        }
    }

    private void writeLoop() {
        while (!stopping) {
            try {
                Pending pending = outgoing.take();
                if (stopping || !"CONNECTED".equals(health) || pending.connection() != connection) continue;
                try {
                    if (!pending.permitted().getAsBoolean()) continue;
                } catch (RuntimeException failure) {
                    org.slf4j.LoggerFactory.getLogger(getClass())
                            .warn("Dropping message whose send eligibility could not be checked for {}", config.id());
                    continue;
                }
                writingConnection = pending.connection();
                writingSince = System.nanoTime();
                try {
                    pending.send().run(pending.connection());
                    writingSince = 0;
                } catch (Exception failure) {
                    lastError = "Send failed (" + failure.getClass().getSimpleName() + ")";
                    health = "RETRYING";
                    pending.connection().close();
                    continue;
                } finally {
                    writingSince = 0;
                }
                try {
                    pending.onSent().run();
                } catch (RuntimeException failure) {
                    org.slf4j.LoggerFactory.getLogger(getClass())
                            .error("Failed to record completed send for {}", config.id(), failure);
                }
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
                break;
            }
        }
    }

    public boolean send(Send action, Runnable onSent) {
        return send(action, () -> true, onSent);
    }

    public boolean send(Send action, BooleanSupplier permitted, Runnable onSent) {
        CommunicationTransport current = connection;
        return !stopping
                && "CONNECTED".equals(health)
                && current != null
                && outgoing.offer(new Pending(current, action, permitted, onSent));
    }

    public void checkWriteTimeout() {
        long since = writingSince;
        if (since != 0 && System.nanoTime() - since > TimeUnit.SECONDS.toNanos(5)) {
            lastError = "Send timed out";
            health = "RETRYING";
            CommunicationTransport current = writingConnection;
            if (current != null) current.close();
        }
    }

    public boolean stop() {
        stopping = true;
        health = "STOPPING";
        outgoing.clear();
        CommunicationTransport current = connection;
        if (current != null) current.close();
        reader.interrupt();
        writer.interrupt();
        try {
            reader.join(6000);
            writer.join(6000);
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        }
        boolean stopped = !reader.isAlive() && !writer.isAlive();
        health = stopped ? "STOPPED" : "FAILED";
        if (!stopped) lastError = "Worker did not stop; replacement blocked";
        return stopped;
    }

    public String health() {
        return health;
    }

    public String lastError() {
        return lastError;
    }

    public LocalDateTime lastPacketTime() {
        return lastPacketTime;
    }
}
