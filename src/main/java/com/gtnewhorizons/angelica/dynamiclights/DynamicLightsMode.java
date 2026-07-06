package com.gtnewhorizons.angelica.dynamiclights;

import java.util.Objects;

public enum DynamicLightsMode
{
    OFF("Off", 0),
    FASTEST("Fastest", 500),
    FAST("Fast", 250),
    FANCY("Fancy", 50),
    REALTIME("Realtime", 0);

    private final String name;
    private final int delay;

    public boolean hasDelay() {
        return delay > 0;
    }

    public int getDelay(){
        return delay;
    }

    public String getName() {
        return name;
    }

    DynamicLightsMode(String name, int delay) {
        this.name = name;
        this.delay = delay;
    }

    public boolean isEnabled() {
        return !Objects.equals(this, DynamicLightsMode.OFF);
    }
}
