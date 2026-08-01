package com.gtnewhorizons.angelica.mixins.early.shaders;

import com.gtnewhorizons.angelica.rendering.FallingBlockRendering;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.coderbot.iris.uniforms.CapturedRenderingState;
import net.coderbot.iris.uniforms.ItemIdManager;
import net.minecraft.client.renderer.entity.RenderFallingBlock;
import net.minecraft.entity.item.EntityFallingBlock;
import org.spongepowered.asm.mixin.Mixin;

/**
 * Sets the currentRenderedItemId for falling blocks and enables per-face normal emission.
 */
@Mixin(RenderFallingBlock.class)
public class MixinRenderFallingBlock {

    @WrapMethod(method = "doRender(Lnet/minecraft/entity/item/EntityFallingBlock;DDDFF)V")
    private void iris$fallingBlockId(EntityFallingBlock entity, double x, double y, double z, float entityYaw, float partialTicks, Operation<Void> original) {
        final int prevItemId = ItemIdManager.getItemId();
        final int prevBlockEntityId = CapturedRenderingState.INSTANCE.getCurrentRenderedBlockEntity();
        FallingBlockRendering.active = true;

        CapturedRenderingState.INSTANCE.setCurrentBlockEntity(1);

        if (prevItemId <= 0) {
            ItemIdManager.setBlockId(entity.func_145805_f(), entity.field_145814_a);
        }

        try {
            original.call(entity, x, y, z, entityYaw, partialTicks);
        } finally {
            FallingBlockRendering.active = false;

            ItemIdManager.setItemIdRaw(prevItemId);
            CapturedRenderingState.INSTANCE.setCurrentBlockEntity(prevBlockEntityId);
        }
    }
}
