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

@Mixin(Render.class)
public class MixinRenderEntityFlame {
    @Unique
    private static final NamespacedId flameId = new NamespacedId("minecraft", "entity_flame");

    @Inject(method = "renderEntityOnFire", at = @At("HEAD"))
    private void iris$setFlame(Entity entity, double x, double y, double z, float partialTicks, CallbackInfo ci) {
        Object2IntFunction<NamespacedId> entityIdMap = BlockRenderingSettings.INSTANCE.getEntityIds();
        if (entityIdMap != null) {
            CapturedRenderingState.INSTANCE.setCurrentEntity(entityIdMap.applyAsInt(flameId));
        }
    }

    @Inject(method = "renderEntityOnFire", at = @At("RETURN"))
    private void iris$resetFlame(Entity entity, double x, double y, double z, float partialTicks, CallbackInfo ci) {
        CapturedRenderingState.INSTANCE.setCurrentEntity(0);
    }

}
