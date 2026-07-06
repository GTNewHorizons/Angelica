package com.gtnewhorizons.angelica.glsm.backend;

@FunctionalInterface
public interface DebugMessageHandler {
    void handleMessage(int source, int type, int id, int severity, String message);
}
