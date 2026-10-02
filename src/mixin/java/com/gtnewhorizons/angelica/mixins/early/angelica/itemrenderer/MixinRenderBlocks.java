package com.gtnewhorizons.angelica.mixins.early.angelica.itemrenderer;

import com.gtnewhorizon.gtnhlib.client.renderer.TessellatorManager;
import com.gtnewhorizons.angelica.experimental.surround.Surround;
import com.gtnewhorizons.angelica.glsm.GLStateManager;
import com.gtnewhorizons.angelica.rendering.items.BlockRenderListManager;
import net.minecraft.block.Block;
import net.minecraft.client.renderer.RenderBlocks;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.util.IIcon;
import net.minecraft.world.IBlockAccess;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

// Innermost: shaders.MixinRenderBlocks_ItemId must also cover a cache hit
@Mixin(value = RenderBlocks.class, priority = 900)
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

    @Surround(method = "renderBlockAsItem")
    private void angelica$cacheBlockItemRenderer(Block block, int meta, float brightness) {
        final boolean uncacheable = BlockRenderListManager.isISBRH(block.getRenderType())
            || enableAO
            || this.overrideBlockTexture != null
            || brightness != 1.0F
            || !this.useInventoryTint
            || this.blockAccess != null
            || (uvRotateEast | uvRotateWest | uvRotateSouth | uvRotateNorth | uvRotateTop | uvRotateBottom) != 0
            || GLStateManager.isRecordingDisplayList()
            || TessellatorManager.isCurrentlyCapturing()
            || TessellatorManager.shouldInterceptDraw(Tessellator.instance);
        @Surround.Carry
        final int list = uncacheable ? 0 : BlockRenderListManager.callOrStartCompiling(BlockRenderListManager.getDisplayList(block, meta));
        @Surround.Skip
        final boolean cached = list < 0;
    }

    @Surround.Return
    private void angelica$endBlockItemRenderer(Block block, int meta, float brightness, @Surround.Carry int list) {
        BlockRenderListManager.endCompiling(list, block, meta);
    }

    @Surround.Catch
    private void angelica$abortBlockItemRenderer(Throwable error, @Surround.Carry int list) {
        BlockRenderListManager.abortCompiling(list);
    }
}
