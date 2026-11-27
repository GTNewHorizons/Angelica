package com.gtnewhorizons.angelica.api.shaders;

import com.gtnewhorizons.angelica.config.AngelicaConfig;
import net.coderbot.iris.block_rendering.BlockMaterialMapping;
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

    /**
     * Makes shaderpacks treat {@code alias} as {@code target} (same block.properties material IDs and render layer),
     * e.g. a modded water block aliased to {@code Blocks.water}. Packs that map {@code alias} explicitly take
     * precedence. Register during init, before the first shaderpack load.
     */
    public static void registerBlockAlias(Block alias, Block target) {
        BlockMaterialMapping.registerAlias(alias, target);
    }
}
