package com.gtnewhorizons.angelica.mixins.early.angelica.itemrenderer;

import com.gtnewhorizon.gtnhlib.client.renderer.TessellatorManager;
import com.gtnewhorizons.angelica.glsm.GLStateManager;
import com.gtnewhorizons.angelica.rendering.items.BlockRenderListManager;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.block.Block;
import net.minecraft.client.renderer.RenderBlocks;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.util.IIcon;
import net.minecraft.world.IBlockAccess;
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

    @Shadow public IBlockAccess blockAccess;

    @Shadow public int uvRotateEast;
    @Shadow public int uvRotateWest;
    @Shadow public int uvRotateSouth;
    @Shadow public int uvRotateNorth;
    @Shadow public int uvRotateTop;
    @Shadow public int uvRotateBottom;

    @WrapMethod(method = "renderBlockAsItem")
    private void angelica$cacheBlockItemRenderer(Block block, int meta, float brightness, Operation<Void> original) {
        if (BlockRenderListManager.isISBRH(block.getRenderType())
            || enableAO
            || this.overrideBlockTexture != null
            || brightness != 1.0F
            || !this.useInventoryTint
            || this.blockAccess != null
            || (uvRotateEast | uvRotateWest | uvRotateSouth | uvRotateNorth | uvRotateTop | uvRotateBottom) != 0
            || GLStateManager.isRecordingDisplayList()
            || TessellatorManager.isCurrentlyCapturing()
            || TessellatorManager.shouldInterceptDraw(Tessellator.instance)
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
