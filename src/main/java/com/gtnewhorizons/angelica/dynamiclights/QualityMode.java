package com.gtnewhorizons.angelica.dynamiclights;

public enum QualityMode
{
    OFF("Off"),
    SLOW("Slow"),
    FAST("Fast"),
    REALTIME("Realtime");

    private final String name;

    QualityMode(String name) {
        this.name = name;
    }
}
