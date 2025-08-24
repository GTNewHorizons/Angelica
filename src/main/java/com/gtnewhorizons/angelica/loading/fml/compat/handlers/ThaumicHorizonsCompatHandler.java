package com.gtnewhorizons.angelica.loading.fml.compat.handlers;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.gtnewhorizons.angelica.loading.fml.compat.ICompatHandler;

import java.util.List;
import java.util.Map;

public class ThaumicHorizonsCompatHandler implements ICompatHandler {

    @Override
    public Map<String, List<String>> getHUDCachingEarlyReturn() {
        return ImmutableMap
            .of("com.kentington.thaumichorizons.client.lib.RenderEventHandler", ImmutableList.of("renderOverlay"));
    }
}
