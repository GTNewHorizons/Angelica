package com.gtnewhorizons.angelica.mixins.early.angelica.itemrenderer;

import com.gtnewhorizons.angelica.glsm.GLStateManager;
import com.gtnewhorizons.angelica.rendering.items.BlockRenderListManager;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.block.Block;
import net.minecraft.client.renderer.RenderBlocks;
import net.minecraft.util.IIcon;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(RenderBlocks.class)
public abstract class MixinRenderBlocks {


    @Shadow
    public boolean useInventoryTint;

    @Shadow
    public boolean enableAO;

    @Shadow
    public IIcon overrideBlockTexture;

    @WrapMethod(method = "renderBlockAsItem")
    private void angelica$cacheBlockItemRenderer(Block block, int meta, float brightness, Operation<Void> original) {
        if (BlockRenderListManager.isISBRH(block.getRenderType())
            || enableAO
            || this.overrideBlockTexture != null
            || brightness != 1.0F
            || !this.useInventoryTint
        ) {
            // Do not cache those
            original.call(block, meta, brightness);
            return;
        }
        int list = BlockRenderListManager.getDisplayList(block, meta);
        if (list == 0) {
            list = BlockRenderListManager.startCompiling();
            original.call(block, meta, brightness);
            BlockRenderListManager.endCompiling(list, block, meta);
        }
        GLStateManager.glCallList(list);
    }
}
