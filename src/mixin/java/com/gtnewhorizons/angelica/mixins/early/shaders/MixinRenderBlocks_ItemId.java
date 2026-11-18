package com.gtnewhorizons.angelica.mixins.early.shaders;

import com.gtnewhorizon.gtnhlib.client.renderer.TessellatorManager;
import com.gtnewhorizons.angelica.experimental.surround.Surround;
import net.coderbot.iris.uniforms.CapturedRenderingState;
import net.coderbot.iris.uniforms.ItemIdManager;
import net.minecraft.block.Block;
import net.minecraft.client.renderer.RenderBlocks;
import org.spongepowered.asm.mixin.Mixin;

/**
 * Sets the currentRenderedItem ID for blocks drawn outside of terrain.
 */
@Mixin(RenderBlocks.class)
public class MixinRenderBlocks_ItemId {

    // renderBlockAsItem is shared with GUI and HUD item rendering
    @Surround(method = "renderBlockAsItem")
    private void iris$blockItemId(Block block, int metadata, float brightness) {
        @Surround.Carry
        final boolean active = ItemIdManager.isWorldRenderActive() && TessellatorManager.isOnMainThread();

        if (active) {
            final int prevItemId = ItemIdManager.getItemId();
            ItemIdManager.pushItemId();
            CapturedRenderingState.INSTANCE.pushCurrentBlockEntity();

            CapturedRenderingState.INSTANCE.setCurrentBlockEntity(1);

            if (prevItemId <= 0) {
                ItemIdManager.setBlockId(block, metadata);
            }
        }
    }

    @Surround.Finally
    private void iris$blockItemIdRestore(@Surround.Carry boolean active) {
        if (active) {
            ItemIdManager.popItemId();
            CapturedRenderingState.INSTANCE.popCurrentBlockEntity();
        }
    }
}
