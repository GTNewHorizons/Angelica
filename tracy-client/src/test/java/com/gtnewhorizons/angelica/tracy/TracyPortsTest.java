package com.gtnewhorizons.angelica.tracy;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.ServerSocket;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TracyPortsTest {

    @Test
    void autoPickSkipsAnOccupiedPort() throws IOException {
        for (int port = 8086; port <= 8105; port++) {
            try (ServerSocket held = new ServerSocket(port)) {
                final int picked = TracyPorts.pick();
                assertNotEquals(port, picked);
                assertTrue(picked > 0);
                return;
            } catch (IOException ignored) {}
        }
    }

    @Test
    void parsePortAcceptsTheValidRange() {
        assertEquals(1, TracyPorts.parsePort("1"));
        assertEquals(65535, TracyPorts.parsePort("65535"));
        assertEquals(9000, TracyPorts.parsePort("9000"));
    }

    @Test
    void parsePortRejectsOutOfRangeAndGarbage() {
        assertEquals(0, TracyPorts.parsePort("0"));
        assertEquals(0, TracyPorts.parsePort("65536"));
        assertEquals(0, TracyPorts.parsePort("-1"));
        assertEquals(0, TracyPorts.parsePort("not a port"));
        assertEquals(0, TracyPorts.parsePort(""));
    }
}
