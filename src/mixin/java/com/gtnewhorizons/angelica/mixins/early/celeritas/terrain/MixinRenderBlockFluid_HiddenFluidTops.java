package com.gtnewhorizons.angelica.mixins.early.celeritas.terrain;

import com.gtnewhorizon.gtnhlib.client.renderer.TessellatorManager;
import com.gtnewhorizons.angelica.experimental.surround.Surround;
import com.gtnewhorizons.angelica.proxy.ClientProxy;
import com.gtnewhorizons.angelica.rendering.StateAwareTessellator;
import com.gtnewhorizons.angelica.rendering.celeritas.HiddenFluidTops;
import net.minecraft.block.Block;
import net.minecraft.world.IBlockAccess;
import net.minecraftforge.fluids.RenderBlockFluid;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(value = RenderBlockFluid.class, remap = false)
public class MixinRenderBlockFluid_HiddenFluidTops {

    @Surround(method = "renderWorldBlock",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/world/IBlockAccess;getBlock(III)Lnet/minecraft/block/Block;", ordinal = 0, remap = true),
        remap = false)
    private void angelica$checkFluidTop() {}

    @Surround.Return
    private Block angelica$hideFluidTop(Block blockOnTop, IBlockAccess world, int x, int topY, int z,
                                        @Surround.Local(argsOnly = true) Block fluid, @Surround.Local(argsOnly = true, ordinal = 1) int y) {
        if (blockOnTop != fluid && ClientProxy.options().performance.useHiddenFluidCulling
            && ((StateAwareTessellator) TessellatorManager.get()).angelica$isCeleritasMeshing()
            && HiddenFluidTops.isTopHidden(world, fluid, x, y, z, topY - y)) {
            return fluid;
        }
        return blockOnTop;
    }
}
