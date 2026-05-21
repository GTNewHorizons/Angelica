package com.gtnewhorizons.angelica.mixins.early.angelica;

import com.gtnewhorizons.angelica.compat.mojang.Camera;
import com.gtnewhorizons.angelica.mixins.interfaces.EntityRendererAccessor;
import com.gtnewhorizons.angelica.rendering.RenderingState;
import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import jss.notfine.core.Settings;
import jss.notfine.gui.options.named.BobviewMode;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.EntityRenderer;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(EntityRenderer.class)
public abstract class MixinEntityRenderer implements EntityRendererAccessor {

    @Invoker
    public abstract float invokeGetNightVisionBrightness(EntityPlayer entityPlayer, float partialTicks);

    @Accessor("lightmapTexture")
    public abstract DynamicTexture getLightmapTexture();

    @Inject(method = "renderWorld", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/ActiveRenderInfo;updateRenderInfo(Lnet/minecraft/entity/player/EntityPlayer;Z)V", shift = At.Shift.AFTER))
    private void angelica$captureCameraMatrix(float partialTicks, long finishTimeNano, CallbackInfo ci) {
        final Minecraft mc = Minecraft.getMinecraft();
        final EntityLivingBase viewEntity = mc.renderViewEntity;

        Camera.INSTANCE.update(viewEntity, partialTicks);

        // Use entity eye position for cameraPosition uniform, not the third-person camera position.
        RenderingState.INSTANCE.setCameraPosition(
            Camera.INSTANCE.getEntityPos().x,
            Camera.INSTANCE.getEntityPos().y,
            Camera.INSTANCE.getEntityPos().z
        );
    }

    @ModifyArg(method = "setupCameraTransform", at = @At(value = "INVOKE", target = "Lorg/lwjgl/util/glu/Project;gluPerspective(FFFF)V", ordinal = 0, remap = false), index = 0)
    private float captureFov(float fov) {
        RenderingState.INSTANCE.setFov(fov);
        return fov;
    }

    @ModifyConstant(method = "hurtCameraEffect", constant = @Constant(floatValue = 14.0F))
    private float angelica$hurtCameraEffect(float orig) {
        int value = (int) Settings.HURT_SHAKE.option.getStore();
        return orig * (value/100F);
    }

    @WrapWithCondition(method = "setupCameraTransform", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/EntityRenderer;setupViewBobbing(F)V"))
    private boolean angelica$cameraBobbing(EntityRenderer entity, float partialTicks) {
        return Settings.BOBVIEW_MODE.option.getStore() != BobviewMode.HAND;
    }

    @WrapWithCondition(method = "renderHand", at= @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/EntityRenderer;setupViewBobbing(F)V"))
    private boolean angelica$handBobbing(EntityRenderer entity, float partialTicks) {
        return Settings.BOBVIEW_MODE.option.getStore() != BobviewMode.CAMERA;
    }
}
