package com.gtnewhorizons.angelica.mixins.early.angelica.entity;

import com.gtnewhorizons.angelica.rendering.tesr.ModelPartBatcher;
import net.minecraft.client.renderer.entity.Render;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Render.class)
public abstract class MixinRender_ShadowBatch {

    @Shadow
    protected float shadowSize;

    @Shadow
    protected RenderManager renderManager;

    @Inject(method = "renderShadow(Lnet/minecraft/entity/Entity;DDDFF)V", at = @At("HEAD"), cancellable = true)
    private void angelica$batchShadow(Entity entity, double x, double y, double z, float shadowAlpha, float partialTicks, CallbackInfo ci) {
        if (ModelPartBatcher.recordShadow(renderManager.worldObj, entity, x, y, z, shadowAlpha, partialTicks, shadowSize)) {
            ci.cancel();
        }
    }
}
