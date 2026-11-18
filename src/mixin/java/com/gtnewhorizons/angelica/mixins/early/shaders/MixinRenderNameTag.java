package com.gtnewhorizons.angelica.mixins.early.shaders;

import com.gtnewhorizons.angelica.experimental.surround.Surround;
import net.coderbot.iris.shaderpack.materialmap.NamespacedId;
import net.coderbot.iris.uniforms.CapturedRenderingState;
import net.minecraft.client.renderer.entity.Render;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

/**
 * Mixin to set the entity ID to "name_tag" when rendering entity name tags.
 * This allows shaders to apply materials to name tags separately from entities.
 * This is not an actual entity but from our perspective, it looks like it is.
 */
@Mixin(Render.class)
public class MixinRenderNameTag {
    @Unique
    private static final NamespacedId NAME_TAG_ID = new NamespacedId("minecraft", "name_tag");

    /**
     * func_147906_a is the method that renders entity name tags.
     */
    @Surround(method = "func_147906_a")
    private void iris$setNameTagEntityId(Entity entity, String name, double x, double y, double z, int maxDistance) {
        CapturedRenderingState.INSTANCE.pushCurrentEntityAndItem();
        CapturedRenderingState.INSTANCE.setCurrentNamedEntity(NAME_TAG_ID);
    }

    @Surround.Finally
    private void iris$restoreEntityId() {
        CapturedRenderingState.INSTANCE.popCurrentEntityAndItem();
    }
}
