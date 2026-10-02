package com.gtnewhorizons.angelica.mixins.early.shaders;

import com.gtnewhorizons.angelica.experimental.surround.Surround;
import net.coderbot.iris.shaderpack.materialmap.NamespacedId;
import net.coderbot.iris.uniforms.CapturedRenderingState;
import net.minecraft.client.renderer.entity.Render;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(Render.class)
public class MixinRenderEntityFlame {
    @Unique
    private static final NamespacedId flameId = new NamespacedId("minecraft", "entity_flame");
    // Shader devs use entity_flame to target this effect.

    // This runs after the initial entity has finished being rendered.
    // The flame is not a real entity but from the shader's perspective, it is.
    @Surround(method = "renderEntityOnFire")
    private void iris$setFlame(Entity entity, double x, double y, double z, float partialTicks) {
        CapturedRenderingState.INSTANCE.pushCurrentEntityAndItem();
        CapturedRenderingState.INSTANCE.setCurrentNamedEntity(flameId);
    }

    @Surround.Finally
    private void iris$resetFlame() {
        CapturedRenderingState.INSTANCE.popCurrentEntityAndItem();
    }

}
