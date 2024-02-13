package com.gtnewhorizons.angelica.mixins.early.shaders;

import net.coderbot.iris.layer.GbufferPrograms;
import net.coderbot.iris.uniforms.CapturedRenderingState;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(RenderManager.class)
public class MixinRenderManager {

    @Unique
    private static final String DO_RENDER = "Lnet/minecraft/client/renderer/entity/Render;doRender(Lnet/minecraft/entity/Entity;DDDFF)V";

    @Inject(method = "func_147939_a", at = @At(value = "INVOKE", target = DO_RENDER))
    private void iris$onRenderEntityPre(Entity entity, double x, double y, double z, float yaw, float partialTicks, boolean rebuild, CallbackInfoReturnable<Boolean> cir) {
        CapturedRenderingState.INSTANCE.setCurrentEntity(entity.getEntityId());
        GbufferPrograms.beginEntities();
    }

    @Inject(method = "func_147939_a", at = @At(value = "INVOKE", target = DO_RENDER, shift = At.Shift.AFTER))
    private void iris$onRenderEntityPost(Entity entity, double x, double y, double z, float yaw, float partialTicks, boolean rebuild, CallbackInfoReturnable<Boolean> cir) {
        CapturedRenderingState.INSTANCE.setCurrentEntity(-1);
        GbufferPrograms.endEntities();
    }
}
