package com.gtnewhorizons.angelica.transform.compat.handlers;

import com.google.common.collect.ImmutableMap;

import java.util.Map;

public class ExtraUtilsCompatHandler implements CompatHandler {

    @Override
    public Map<String, Boolean> getThreadSafeISBRHAnnotations() {
        return ImmutableMap.of(
            "com.rwtema.extrautils.block.render.RenderBlockColor",
            false,
            "com.rwtema.extrautils.block.render.RenderBlockConnectedTextures",
            true,
            "com.rwtema.extrautils.block.render.RenderBlockConnectedTexturesEthereal",
            true,
            "com.rwtema.extrautils.block.render.RenderBlockFullBright",
            false,
            "com.rwtema.extrautils.block.render.RenderBlockSpike",
            false);
    }

}
