package com.gtnewhorizons.angelica.mixins.early.shaders;

import it.unimi.dsi.fastutil.objects.Object2IntFunction;
import net.coderbot.iris.block_rendering.BlockRenderingSettings;
import net.coderbot.iris.shaderpack.materialmap.NamespacedId;
import net.coderbot.iris.uniforms.CapturedRenderingState;
import net.minecraft.client.renderer.entity.Render;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Mixin to set the entity ID to "name_tag" when rendering entity name tags.
 * This allows shaders to apply materials to name tags separately from entities.
 * This is not an actual entity but from our perspective, it looks like it is.
 */
@Mixin(Render.class)
public class MixinRenderNameTag {
    @Unique
    private static final NamespacedId NAME_TAG_ID = new NamespacedId("minecraft", "name_tag");

    @Unique
    private int angelica$previousEntityId = -1;

    /**
     * Inject at the HEAD of func_147906_a to set the special name_tag entity ID before rendering.
     * func_147906_a is the method that renders entity name tags.
     */
    @Inject(
        method = "func_147906_a",
        at = @At("HEAD")
    )
    private void iris$setNameTagEntityId(Entity entity, String name, double x, double y, double z, int maxDistance, CallbackInfo ci) {
        Object2IntFunction<NamespacedId> entityIdMap = BlockRenderingSettings.INSTANCE.getEntityIds();
        if (entityIdMap != null) {
            angelica$previousEntityId = CapturedRenderingState.INSTANCE.getCurrentRenderedEntity();

            // Set the special name_tag entity ID
            int nameTagId = entityIdMap.applyAsInt(NAME_TAG_ID);
            CapturedRenderingState.INSTANCE.setCurrentEntity(nameTagId);
        }
    }

    /**
     * Inject at the RETURN of func_147906_a to restore the previous entity ID after rendering.
     */
    @Inject(
        method = "func_147906_a",
        at = @At("RETURN")
    )
    private void iris$restoreEntityId(Entity entity, String name, double x, double y, double z, int maxDistance, CallbackInfo ci) {
        if (angelica$previousEntityId != -1) {
            CapturedRenderingState.INSTANCE.setCurrentEntity(angelica$previousEntityId);
            angelica$previousEntityId = -1;
        }
    }
}
