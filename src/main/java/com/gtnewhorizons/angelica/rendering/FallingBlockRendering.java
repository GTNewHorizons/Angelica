package com.gtnewhorizons.angelica.rendering;

import net.coderbot.iris.block_rendering.BlockRenderingSettings;

public final class FallingBlockRendering {

    public static boolean active;

    private FallingBlockRendering() {
    }

    public static boolean skipDirectionalShading() {
        return active && BlockRenderingSettings.INSTANCE.shouldDisableDirectionalShading();
    }
}
