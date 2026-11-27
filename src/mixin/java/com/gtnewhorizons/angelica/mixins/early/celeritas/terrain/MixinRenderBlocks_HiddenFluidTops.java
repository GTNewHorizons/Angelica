package com.gtnewhorizons.angelica.mixins.early.celeritas.terrain;

import com.gtnewhorizon.gtnhlib.client.renderer.TessellatorManager;
import com.gtnewhorizons.angelica.experimental.surround.Surround;
import com.gtnewhorizons.angelica.proxy.ClientProxy;
import com.gtnewhorizons.angelica.rendering.StateAwareTessellator;
import com.gtnewhorizons.angelica.rendering.celeritas.HiddenFluidTops;
import net.minecraft.block.Block;
import net.minecraft.client.renderer.RenderBlocks;
import net.minecraft.world.IBlockAccess;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(RenderBlocks.class)
public class MixinRenderBlocks_HiddenFluidTops {

    @Surround(method = "renderBlockLiquid", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/block/Block;shouldSideBeRendered(Lnet/minecraft/world/IBlockAccess;IIII)Z", ordinal = 0))
    private void angelica$checkFluidTop() {}

    @Surround.Return
    private boolean angelica$hideFluidTop(boolean renderTop, Block fluid, IBlockAccess world, int x, int y, int z, int side) {
        if (renderTop && ClientProxy.options().performance.useHiddenFluidCulling
            && ((StateAwareTessellator) TessellatorManager.get()).angelica$isCeleritasMeshing()
            && HiddenFluidTops.isTopHidden(world, fluid, x, y - 1, z, 1)) {
            return false;
        }
        return renderTop;
    }
}
