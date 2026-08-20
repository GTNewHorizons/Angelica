package com.gtnewhorizons.angelica.mixins.late.client.openblocks;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.coderbot.iris.layer.GbufferPrograms;
import net.coderbot.iris.uniforms.CapturedRenderingState;
import net.coderbot.iris.uniforms.EntityIdHelper;
import net.minecraft.client.renderer.entity.Render;
import net.minecraft.entity.Entity;
import openblocks.client.renderer.tileentity.TileEntityTrophyRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Trophies render a mob through {@link Render#doRender}, force entity phase here.
 */
@Mixin(value = TileEntityTrophyRenderer.class, remap = false)
public class MixinTileEntityTrophyRenderer {

    @WrapOperation(
        method = "renderTrophy",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/entity/Render;doRender(Lnet/minecraft/entity/Entity;DDDFF)V", remap = true))
    private static void angelica$renderTrophyAsEntity(Render render, Entity entity, double x, double y, double z, float entityYaw, float partialTicks, Operation<Void> original) {
        final int prevEntityId = CapturedRenderingState.INSTANCE.getCurrentRenderedEntity();
        final int prevItemId = CapturedRenderingState.INSTANCE.getCurrentRenderedItem();

        CapturedRenderingState.INSTANCE.setCurrentEntityAndItem(EntityIdHelper.getEntityId(entity), 0);

        final boolean nestedInBlockEntity = GbufferPrograms.beginNestedEntityPhase();

        try {
            original.call(render, entity, x, y, z, entityYaw, partialTicks);
        } finally {
            GbufferPrograms.endNestedEntityPhase(nestedInBlockEntity);
            CapturedRenderingState.INSTANCE.setCurrentEntityAndItem(prevEntityId, prevItemId);
        }
    }
}
