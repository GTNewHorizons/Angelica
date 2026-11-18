package com.gtnewhorizons.angelica.tracy;

import java.io.IOException;
import java.net.ServerSocket;

final class TracyPorts {
    private static final int RANGE_START = 8086;
    private static final int RANGE_END = 8105;

    private TracyPorts() {}

    static int pick() {
        for (int port = RANGE_START; port <= RANGE_END; port++) {
            try (ServerSocket socket = new ServerSocket(port)) {
                return port;
            } catch (IOException ignored) {}
        }
        return 0;
    }

    static int parsePort(String value) {
        try {
            final int port = Integer.parseInt(value.trim());
            return port >= 1 && port <= 65535 ? port : 0;
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}
