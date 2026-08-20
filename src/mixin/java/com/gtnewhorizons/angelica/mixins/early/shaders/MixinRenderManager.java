package com.gtnewhorizons.angelica.mixins.early.shaders;

import com.gtnewhorizons.angelica.rendering.tesr.TesrAttribution;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.coderbot.iris.gbuffer_overrides.matching.SpecialCondition;
import net.coderbot.iris.layer.GbufferPrograms;
import net.coderbot.iris.uniforms.CapturedRenderingState;
import net.coderbot.iris.uniforms.EntityIdHelper;
import net.minecraft.client.renderer.entity.Render;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Mixin to handle entity ID and item ID for entities rendered through renderEntityWithPosYaw and renderEntityStatic.
 * Sets the entity ID for all entities and resets item ID to prevent inheritance from previous renders.
 * Specific entities (EntityItem, armor, etc.) can override the item ID in their renderer mixins.
 */
@Mixin(RenderManager.class)
public class MixinRenderManager {

    @Redirect(
        method = "func_147939_a",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/entity/Render;doRenderShadowAndFire(Lnet/minecraft/entity/Entity;DDDFF)V")
    )
    private void iris$wrapShadowAndFire(Render render, Entity entity, double x, double y, double z, float entityYaw, float partialTicks) {
        final Boolean previous = GbufferPrograms.beginTranslucencyDeclaration(null);

        try {
            render.doRenderShadowAndFire(entity, x, y, z, entityYaw, partialTicks);
        } finally {
            GbufferPrograms.endTranslucencyDeclaration(previous);
        }
    }

    @WrapOperation(
        method = "func_147939_a",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/entity/Render;doRender(Lnet/minecraft/entity/Entity;DDDFF)V")
    )
    private void iris$wrapDoRender(Render render, Entity entity, double x, double y, double z, float entityYaw, float partialTicks, Operation<Void> original) {
        final int prevEntityId = CapturedRenderingState.INSTANCE.getCurrentRenderedEntity();
        final int prevItemId = CapturedRenderingState.INSTANCE.getCurrentRenderedItem();
        final Class<?> prevRenderable = TesrAttribution.currentRenderable;

        CapturedRenderingState.INSTANCE.setCurrentEntityAndItem(EntityIdHelper.getEntityId(entity), 0);
        TesrAttribution.currentRenderable = entity != null ? entity.getClass() : null;
        final boolean lightning = EntityIdHelper.isLightningBolt(entity);
        if (lightning) {
            GbufferPrograms.setupSpecialRenderCondition(SpecialCondition.LIGHTNING);
        }
        final boolean nestedInBlockEntity = GbufferPrograms.beginNestedEntityPhase();

        try {
            original.call(render, entity, x, y, z, entityYaw, partialTicks);
        } finally {
            GbufferPrograms.endNestedEntityPhase(nestedInBlockEntity);
            if (lightning) {
                GbufferPrograms.teardownSpecialRenderCondition();
            }
            CapturedRenderingState.INSTANCE.setCurrentEntityAndItem(prevEntityId, prevItemId);
            TesrAttribution.currentRenderable = prevRenderable;
        }
    }
}
