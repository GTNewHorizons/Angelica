package com.gtnewhorizons.angelica.rendering;

import com.gtnewhorizon.gtnhlib.client.renderer.TessellatorManager;
import net.coderbot.iris.block_rendering.BlockRenderingSettings;
import net.irisshaders.iris.api.v0.IrisApi;

public final class FallingBlockRendering {

    public static boolean active;

    private FallingBlockRendering() {
    }

    public static boolean isActive() {
        return active && TessellatorManager.isOnMainThread() && IrisApi.getInstance().isShaderPackInUse();
    }

    public static boolean skipDirectionalShading() {
        return isActive() && BlockRenderingSettings.INSTANCE.shouldDisableDirectionalShading();
    }
}
