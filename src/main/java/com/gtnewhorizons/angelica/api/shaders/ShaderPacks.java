package com.gtnewhorizons.angelica.api.shaders;

import com.gtnewhorizons.angelica.config.AngelicaConfig;
import net.coderbot.iris.uniforms.CapturedRenderingState;
import net.coderbot.iris.uniforms.SystemTimeUniforms;
import net.irisshaders.iris.api.v0.IrisApi;
import net.minecraft.block.Block;

public final class ShaderPacks {

    private ShaderPacks() {}

    public static boolean isInUse() {
        return AngelicaConfig.enableIris && IrisApi.getInstance().isShaderPackInUse();
    }

    public static float getFrameTimeCounter() {
        return SystemTimeUniforms.TIMER.getFrameTimeCounter();
    }

    public static void beginBlockEntity(Block block, int metadata) {
        if (!AngelicaConfig.enableIris) return;
        CapturedRenderingState.INSTANCE.pushCurrentBlockEntity();
        CapturedRenderingState.INSTANCE.setCurrentBlockEntity(block, metadata);
    }

    public static void endBlockEntity() {
        if (!AngelicaConfig.enableIris) return;
        CapturedRenderingState.INSTANCE.popCurrentBlockEntity();
    }
}
