package com.gtnewhorizons.angelica.transform.compat.handlers;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;

import java.util.List;
import java.util.Map;

public class ThaumicHorizonsCompatHandler implements CompatHandler {

    @Override
    public Map<String, List<String>> getHUDCachingEarlyReturn() {
        return ImmutableMap
            .of("com.kentington.thaumichorizons.client.lib.RenderEventHandler", ImmutableList.of("renderOverlay"));
    }
}
