package com.gtnewhorizons.angelica.glsm.backend;

public interface GLDebugMessageListener {
    void onMessage(int source, int type, int id, int severity, int length, long message, long userParam);
}
