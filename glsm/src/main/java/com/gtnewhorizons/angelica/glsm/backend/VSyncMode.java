package com.gtnewhorizons.angelica.glsm.backend;

public enum VSyncMode {
    OFF,
    ON,
    MAILBOX;

    public boolean tearFree() {
        return this != OFF;
    }
}
