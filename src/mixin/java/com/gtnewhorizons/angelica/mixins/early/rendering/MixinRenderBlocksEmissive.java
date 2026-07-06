package com.gtnewhorizons.angelica.mixins.early.rendering;

import com.gtnewhorizons.angelica.render.EmissiveTextureHelper;
import net.minecraft.block.Block;
import net.minecraft.client.renderer.RenderBlocks;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.world.IBlockAccess;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(RenderBlocks.class)
public class MixinRenderBlocksEmissive {

    @Inject(method = "renderStandardBlock", at = @At("TAIL"))
    private void angelica$renderEmissiveOverlay(Block block, int x, int y, int z, CallbackInfoReturnable<Boolean> cir) {
        RenderBlocks self = (RenderBlocks)(Object)this;
        IBlockAccess world = self.blockAccess;
        if (world == null) return;

        int meta = world.getBlockMetadata(x, y, z);

        boolean hasEmissive = false;
        for (int side = 0; side < 6; side++) {
            if (EmissiveTextureHelper.getEmissiveIcon(block, side, meta) != null) {
                hasEmissive = true;
                break;
            }
        }
        if (!hasEmissive) return;

        Tessellator tess = Tessellator.instance;
        tess.setBrightness(0xF000F0);

        EmissiveTextureHelper.renderEmissiveBlockOverlay(self, block, x, y, z, meta);
    }
}
