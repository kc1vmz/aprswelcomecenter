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

import com.fazecast.jSerialComm.SerialPortTimeoutException;
import com.kc1vmz.aprswc.constants.ApplicationToCallConstant;
import java.io.*;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.*;

/** TM-D710G command/converse protocol. One reader preserves packets during command exchanges. */
final class KenwoodSession implements AutoCloseable {
    private final InputStream input;
    private final OutputStream output;
    private final CommunicationConfig config;
    private final BlockingQueue<String> packets = new ArrayBlockingQueue<>(1000);
    private final Object responses = new Object();
    private long prompts;
    private boolean rejected;
    private volatile IOException failure;
    private volatile boolean closed;
    private Thread reader;
    private String header;

    KenwoodSession(InputStream input, OutputStream output, CommunicationConfig config) {
        this.input = input;
        this.output = output;
        this.config = config;
    }

    void initialize() throws IOException {
        reader = Thread.ofPlatform()
                .daemon()
                .name("kenwood-reader-" + config.id())
                .start(this::receive);
        command("\003", 3000);
        command("RESET\r", 10000);
        command("MYCALL " + config.username() + "\r", 3000);
        command(unproto(ApplicationToCallConstant.TOCALL_NC2), 3000);
        for (String setting : new String[] {
            "ECHO OFF",
            "MONITOR ON",
            "MCOM OFF",
            "MRPT ON",
            "MSTAMP OFF",
            "FLOW OFF",
            "DIGIPEAT OFF",
            "CONOK OFF",
            "PACLEN 255",
            "CR OFF"
        }) command(setting + "\r", 3000);
    }

    private String unproto(String destination) {
        return "UNPROTO " + destination
                + (config.digiPath() == null || config.digiPath().isBlank() ? "" : " VIA " + config.digiPath()) + "\r";
    }

    private void command(String text, long timeout) throws IOException {
        synchronized (responses) {
            long previous = prompts;
            rejected = false;
            write(text);
            long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeout);
            while (prompts == previous && !closed && failure == null) {
                long remaining = deadline - System.nanoTime();
                if (remaining <= 0) throw new IOException("Kenwood command timed out");
                try {
                    TimeUnit.NANOSECONDS.timedWait(responses, remaining);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new IOException("Kenwood interrupted", e);
                }
            }
            if (failure != null) throw failure;
            if (closed || rejected) throw new IOException("Kenwood command rejected or connection closed");
        }
    }

    synchronized void send(String destination, String information) throws IOException {
        // Control characters would execute commands or prematurely terminate a packet.
        if (information.length() > 255 || information.chars().anyMatch(c -> c < 32 || c > 126))
            throw new IOException("Kenwood payload must be at most 255 printable ASCII characters");
        validateAddress(destination);
        command(unproto(destination), 3000);
        write("CONVERS\r");
        pause();
        write(information + "\r");
        pause();
        command("\003", 3000);
    }

    private void pause() throws IOException {
        try {
            Thread.sleep(500);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException(e);
        }
    }

    private void write(String text) throws IOException {
        if (closed || failure != null) throw new IOException("Kenwood connection closed", failure);
        output.write(text.getBytes(StandardCharsets.US_ASCII));
        output.flush();
    }

    String read() throws IOException {
        if (failure != null) throw failure;
        if (closed) throw new EOFException();
        try {
            return packets.poll(500, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException(e);
        }
    }

    private void receive() {
        StringBuilder line = new StringBuilder();
        boolean oversized = false;
        try {
            while (!closed) {
                int value;
                try {
                    value = input.read();
                } catch (SerialPortTimeoutException | SocketTimeoutException e) {
                    continue;
                }
                if (value < 0) throw new EOFException("Kenwood disconnected");
                if (value == 3) continue;
                if (value == '\r' || value == '\n') {
                    if (!oversized) accept(line.toString());
                    line.setLength(0);
                    oversized = false;
                    continue;
                }
                if (line.length() >= 1024) oversized = true;
                if (!oversized) line.append((char) value);
                if (line.toString().matches("(?i)[*\\s]*cmd:")) {
                    synchronized (responses) {
                        prompts++;
                        responses.notifyAll();
                    }
                    line.setLength(0);
                }
            }
        } catch (IOException e) {
            failure = e;
            synchronized (responses) {
                responses.notifyAll();
            }
        }
    }

    private void accept(String line) throws IOException {
        if (line.isBlank()) return;
        if (line.startsWith("?")) {
            synchronized (responses) {
                rejected = true;
            }
            return;
        }
        String value = line.replaceFirst("^\\*+", "");
        // Some TNC monitor modes put the information on the following line.
        if (header != null) {
            value = header + value;
            header = null;
        }
        if (value.matches("[A-Za-z0-9-]+>[^:]+:")) {
            header = value;
            return;
        }
        try {
            if (!value.matches("[A-Za-z0-9-]+>[^:]+:.+")) return;
            String packet = value;
            if (!packets.offer(packet)) throw new IOException("Kenwood receive queue full");
        } catch (IllegalArgumentException ignored) {
            /* Command echoes and banners are not packets. */
        }
    }

    static void validateAddress(String address) {
        if (address == null || !address.matches("[A-Z0-9]{1,6}(-([0-9]|1[0-5]))?"))
            throw new IllegalArgumentException("Invalid AX.25 callsign");
    }

    public void close() {
        closed = true;
        if (reader != null) reader.interrupt();
        synchronized (responses) {
            responses.notifyAll();
        }
    }
}
