package com.gtnewhorizons.angelica.rendering;

import net.coderbot.iris.block_rendering.BlockRenderingSettings;
import net.minecraft.world.IBlockAccess;

public final class FallingBlockRendering {

    public static boolean active;

    private static final FallingBlockMetaAccess META_ACCESS = new FallingBlockMetaAccess();

    private FallingBlockRendering() {
    }

    public static FallingBlockMetaAccess metaAccess(IBlockAccess world, int x, int y, int z, int metadata) {
        return META_ACCESS.set(world, x, y, z, metadata);
    }

    public static boolean isActive() {
        return active && BlockMaterialAttribute.shadersActive();
    }

    public static boolean skipDirectionalShading() {
        return isActive() && BlockRenderingSettings.INSTANCE.shouldDisableDirectionalShading();
    }
}
