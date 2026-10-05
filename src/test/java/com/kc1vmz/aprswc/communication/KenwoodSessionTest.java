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

import static org.assertj.core.api.Assertions.*;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.junit.jupiter.api.Test;

class KenwoodSessionTest {
    private CommunicationConfig config() {
        return new CommunicationConfig(
                UUID.randomUUID(),
                0,
                "KENWOOD_SERIAL",
                null,
                null,
                "N1ABC-10",
                null,
                null,
                "COM9",
                9600,
                null,
                null,
                "WIDE1-1,WIDE2-1");
    }

    static class Radio extends OutputStream implements AutoCloseable {
        final PipedInputStream input = new PipedInputStream(8192);
        final PipedOutputStream received;
        final List<String> commands = new ArrayList<>(), payloads = new ArrayList<>();
        final StringBuilder text = new StringBuilder();
        boolean converse;
        String reject;

        Radio() throws IOException {
            received = new PipedOutputStream(input);
        }

        void receive(String value) throws IOException {
            received.write(value.getBytes(StandardCharsets.US_ASCII));
            received.flush();
        }

        @Override
        public void write(int value) throws IOException {
            if (value == 3) {
                converse = false;
                receive("cmd:");
                return;
            }
            if (value == '\r') {
                String line = text.toString();
                text.setLength(0);
                if (converse) {
                    payloads.add(line);
                    receive("N2ABC>APANC1::NET001   :CI{1\r");
                } else {
                    commands.add(line);
                    if (line.equals(reject)) {
                        receive("?EH\r cmd:");
                        return;
                    }
                    if (line.equals("CONVERS")) converse = true;
                    else receive("cmd:");
                }
            } else text.append((char) value);
        }

        @Override
        public void close() throws IOException {
            received.close();
            input.close();
        }
    }

    @Test
    void initializesSendsPayloadAndReceivesDuringTransmission() throws Exception {
        try (var radio = new Radio();
                var session = new KenwoodSession(radio.input, radio, config())) {
            session.initialize();
            assertThat(radio.commands).startsWith("RESET", "MYCALL N1ABC-10", "UNPROTO APANC2 VIA WIDE1-1,WIDE2-1");
            assertThat(radio.commands).contains("MONITOR ON", "ECHO OFF", "PACLEN 255");
            session.send("APANC2", ":N2ABC    :Hello{7");
            assertThat(radio.payloads).containsExactly(":N2ABC    :Hello{7");
            String packet = session.read();
            assertThat(packet).startsWith("N2ABC>");
            assertThat(packet).endsWith(":NET001   :CI{1");
            radio.receive("N3ABC>APANC1:\r");
            radio.receive("!4258.00N/07127.00W>test\r");
            assertThat(session.read()).startsWith("N3ABC>");
            assertThatThrownBy(() -> session.send("APANC2", "bad\003payload")).isInstanceOf(IOException.class);
        }
    }

    @Test
    void rejectedInitializationFailsAndFragmentsArePreserved() throws Exception {
        try (var radio = new Radio();
                var session = new KenwoodSession(radio.input, radio, config())) {
            radio.reject = "MYCALL N1ABC-10";
            assertThatThrownBy(session::initialize)
                    .isInstanceOf(IOException.class)
                    .hasMessageContaining("rejected");
            assertThat(radio.payloads).isEmpty();
        }
        try (var radio = new Radio();
                var session = new KenwoodSession(radio.input, radio, config())) {
            session.initialize();
            radio.receive("N4ABC>AP");
            assertThat(session.read()).isNull();
            radio.receive("ANC1:>Ready\rN5ABC>APANC1:>Also ready\r");
            assertThat(session.read()).startsWith("N4ABC>");
            assertThat(session.read()).startsWith("N5ABC>");
        }
    }

    @Test
    void transmissionIsAlwaysEnabled() throws Exception {
        try (var radio = new Radio();
                var session = new KenwoodSession(radio.input, radio, config())) {
            session.initialize();
            session.send("APANC2", "hello");
            assertThat(radio.payloads).containsExactly("hello");
        }
    }
}
