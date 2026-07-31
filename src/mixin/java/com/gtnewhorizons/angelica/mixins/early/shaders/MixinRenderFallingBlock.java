package com.gtnewhorizons.angelica.mixins.early.shaders;

import com.gtnewhorizons.angelica.rendering.FallingBlockRendering;
import com.llamalad7.mixinextras.sugar.Share;
import com.llamalad7.mixinextras.sugar.ref.LocalIntRef;
import net.coderbot.iris.uniforms.CapturedRenderingState;
import net.coderbot.iris.uniforms.ItemIdManager;
import net.minecraft.client.renderer.entity.RenderFallingBlock;
import net.minecraft.entity.item.EntityFallingBlock;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Sets the currentRenderedItemId for falling blocks and enables per-face normal emission.
 */
@Mixin(RenderFallingBlock.class)
public class MixinRenderFallingBlock {

    @Inject(
        method = "doRender(Lnet/minecraft/entity/item/EntityFallingBlock;DDDFF)V",
        at = @At("HEAD")
    )
    private void iris$setFallingBlockId(EntityFallingBlock entity, double x, double y, double z, float entityYaw, float partialTicks, CallbackInfo ci,
                                        @Share("angelica$prevItemId") LocalIntRef prevItemId,
                                        @Share("angelica$prevBlockEntityId") LocalIntRef prevBlockEntityId) {
        FallingBlockRendering.active = true;

        final int prev = ItemIdManager.getItemId();
        prevItemId.set(prev);
        prevBlockEntityId.set(CapturedRenderingState.INSTANCE.getCurrentRenderedBlockEntity());

        if (prev > 0) return;

        CapturedRenderingState.INSTANCE.setCurrentBlockEntity(1);
        ItemIdManager.setBlockId(entity.func_145805_f(), entity.field_145814_a);
    }

    @Inject(
        method = "doRender(Lnet/minecraft/entity/item/EntityFallingBlock;DDDFF)V",
        at = @At("RETURN")
    )
    private void iris$restoreFallingBlockId(EntityFallingBlock entity, double x, double y, double z, float entityYaw, float partialTicks, CallbackInfo ci,
                                            @Share("angelica$prevItemId") LocalIntRef prevItemId,
                                            @Share("angelica$prevBlockEntityId") LocalIntRef prevBlockEntityId) {
        FallingBlockRendering.active = false;

        ItemIdManager.setItemIdRaw(prevItemId.get());
        CapturedRenderingState.INSTANCE.setCurrentBlockEntity(prevBlockEntityId.get());
    }
}
