package com.gtnewhorizons.angelica.mixins.early.angelica;

import com.gtnewhorizons.angelica.rendering.RenderingState;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import jss.notfine.core.Settings;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.EntityRenderer;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.world.WorldProvider;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(EntityRenderer.class)
public abstract class MixinEntityRenderer {
    @Inject(method = "setupCameraTransform", at = @At(value = "TAIL"))
    private void angelica$captureCameraMatrix(float partialTicks, int startTime, CallbackInfo ci) {
        final Minecraft mc = Minecraft.getMinecraft();
        final EntityLivingBase viewEntity = mc.renderViewEntity;

        RenderingState.INSTANCE.setCameraPosition(
            viewEntity.lastTickPosX + (viewEntity.posX - viewEntity.lastTickPosX) * partialTicks,
            viewEntity.lastTickPosY + (viewEntity.posY - viewEntity.lastTickPosY) * partialTicks,
            viewEntity.lastTickPosZ + (viewEntity.posZ - viewEntity.lastTickPosZ) * partialTicks
        );
    }

    @WrapOperation(method = "setupFog", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/WorldProvider;getWorldHasVoidParticles()Z"))
    private boolean angelica$wrapVoidFog(WorldProvider provider, Operation<Boolean> original){
        return ((boolean)Settings.VOID_FOG.option.getStore()) ? original.call(provider) : false;
    }
}
