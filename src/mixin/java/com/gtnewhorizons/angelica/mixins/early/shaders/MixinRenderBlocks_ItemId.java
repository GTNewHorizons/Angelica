package com.gtnewhorizons.angelica.mixins.early.shaders;

import com.gtnewhorizon.gtnhlib.client.renderer.TessellatorManager;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.coderbot.iris.uniforms.CapturedRenderingState;
import net.coderbot.iris.uniforms.ItemIdManager;
import net.minecraft.block.Block;
import net.minecraft.client.renderer.RenderBlocks;
import org.spongepowered.asm.mixin.Mixin;

/**
 * Sets the currentRenderedItem ID for blocks drawn outside of terrain.
 */
@Mixin(value = RenderBlocks.class, priority = 1500)
public class MixinRenderBlocks_ItemId {

    @WrapMethod(method = "renderBlockAsItem")
    private void iris$blockItemId(Block block, int metadata, float brightness, Operation<Void> original) {
        // renderBlockAsItem is shared with GUI and HUD item rendering
        if (!ItemIdManager.isWorldRenderActive() || !TessellatorManager.isOnMainThread()) {
            original.call(block, metadata, brightness);
            return;
        }

        final int prevItemId = ItemIdManager.getItemId();
        final int prevBlockEntityId = CapturedRenderingState.INSTANCE.getCurrentRenderedBlockEntity();

        CapturedRenderingState.INSTANCE.setCurrentBlockEntity(1);

        if (prevItemId <= 0) {
            ItemIdManager.setBlockId(block, metadata);
        }

        try {
            original.call(block, metadata, brightness);
        } finally {
            ItemIdManager.setItemIdRaw(prevItemId);
            CapturedRenderingState.INSTANCE.setCurrentBlockEntity(prevBlockEntityId);
        }
    }
}
