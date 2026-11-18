package com.gtnewhorizons.angelica.api.debug;

public interface DebugScreenDisplayer {

    DebugFact line(DebugGroupHandle group);

    DebugFact fact(DebugGroupHandle group, String name);
}
