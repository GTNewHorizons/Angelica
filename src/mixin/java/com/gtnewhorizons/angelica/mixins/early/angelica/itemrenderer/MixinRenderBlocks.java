package com.gtnewhorizons.angelica.mixins.early.angelica.itemrenderer;

import com.gtnewhorizon.gtnhlib.client.renderer.TessellatorManager;
import com.gtnewhorizons.angelica.experimental.surround.Surround;
import com.gtnewhorizons.angelica.glsm.GLStateManager;
import com.gtnewhorizons.angelica.rendering.items.BlockRenderListManager;
import com.gtnewhorizons.angelica.rendering.items.DroppedItemInstancer;
import net.minecraft.block.Block;
import net.minecraft.client.renderer.RenderBlocks;
import net.minecraft.client.renderer.Tessellator;
import org.spongepowered.asm.mixin.Mixin;

// Innermost: shaders.MixinRenderBlocks_ItemId must also cover a cache hit
@Mixin(value = RenderBlocks.class, priority = 900)
public abstract class MixinRenderBlocks {

    @Surround(method = "renderBlockAsItem")
    private void angelica$cacheBlockItemRenderer(Block block, int meta, float brightness) {
        //noinspection DataFlowIssue
        final RenderBlocks self = (RenderBlocks) (Object) this;
        final boolean uncacheable = !BlockRenderListManager.rendersPlainItem(self, block, brightness)
            || GLStateManager.isRecordingDisplayList()
            || TessellatorManager.isCurrentlyCapturing()
            || TessellatorManager.shouldInterceptDraw(Tessellator.instance);
        @Surround.Carry
        final long part = uncacheable ? DroppedItemInstancer.RUN : DroppedItemInstancer.directBlock(self, block, meta, brightness);
        @Surround.Carry
        final int list = uncacheable ? 0 : part == DroppedItemInstancer.SKIP ? -1 : BlockRenderListManager.callOrStartCompiling(BlockRenderListManager.getDisplayList(block, meta));
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

    @Surround.Finally
    private void angelica$countDirectBlock(@Surround.Carry long part) {
        DroppedItemInstancer.endPart(part);
    }
}
