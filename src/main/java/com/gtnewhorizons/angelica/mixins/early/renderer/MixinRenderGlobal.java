package com.gtnewhorizons.angelica.mixins.early.renderer;

import net.minecraft.client.renderer.RenderGlobal;
import net.minecraft.client.renderer.culling.ICamera;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.MovingObjectPosition;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.gtnewhorizons.angelica.client.Shaders;

@Mixin(RenderGlobal.class)
public class MixinRenderGlobal {

    @Inject(
            method = "renderEntities(Lnet/minecraft/entity/EntityLivingBase;Lnet/minecraft/client/renderer/culling/ICamera;F)V",
            at = @At(
                    value = "INVOKE_STRING",
                    target = "Lnet/minecraft/profiler/Profiler;endStartSection(Ljava/lang/String;)V",
                    args = "ldc=entities"))
    private void angelica$beginEntities(EntityLivingBase entity, ICamera camera, float p_147589_3_, CallbackInfo ci) {
        Shaders.beginEntities();
    }

    @Inject(
            method = "renderEntities(Lnet/minecraft/entity/EntityLivingBase;Lnet/minecraft/client/renderer/culling/ICamera;F)V",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/entity/RenderManager;renderEntitySimple(Lnet/minecraft/entity/Entity;F)Z",
                    ordinal = 1,
                    shift = At.Shift.AFTER))
    private void angelica$nextEntity(EntityLivingBase entity, ICamera camera, float p_147589_3_, CallbackInfo ci) {
        Shaders.nextEntity();
    }

    @Inject(
            method = "renderEntities(Lnet/minecraft/entity/EntityLivingBase;Lnet/minecraft/client/renderer/culling/ICamera;F)V",
            at = @At(
                    value = "INVOKE_STRING",
                    target = "Lnet/minecraft/profiler/Profiler;endStartSection(Ljava/lang/String;)V",
                    args = "ldc=blockentities",
                    shift = At.Shift.AFTER))
    private void angelica$blockEntities(EntityLivingBase entity, ICamera camera, float p_147589_3_, CallbackInfo ci) {
        Shaders.endEntities();
        Shaders.beginBlockEntities();
    }

    @Inject(
            method = "Lnet/minecraft/client/renderer/RenderGlobal;renderEntities(Lnet/minecraft/entity/EntityLivingBase;Lnet/minecraft/client/renderer/culling/ICamera;F)V",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/EntityRenderer;disableLightmap(D)V",
                    shift = At.Shift.AFTER))
    private void angelica$endBlockEntities(EntityLivingBase entity, ICamera camera, float p_147589_3_,
            CallbackInfo ci) {
        Shaders.endBlockEntities();
    }

    // Texture 2D
    @Inject(
            method = "sortAndRender(Lnet/minecraft/entity/EntityLivingBase;ID)I",
            at = @At(
                    value = "INVOKE",
                    target = "Lorg/lwjgl/opengl/GL11;glDisable(I)V",
                    remap = false,
                    ordinal = 0,
                    shift = At.Shift.AFTER),
            expect = 1)
    private void angelica$sortandRenderDisableTexture2D(EntityLivingBase p_72719_1_, int p_72719_2_, double p_72719_3_,
            CallbackInfoReturnable<Integer> cir) {
        Shaders.disableTexture2D();
    }

    @Inject(
            method = "drawSelectionBox(Lnet/minecraft/entity/player/EntityPlayer;Lnet/minecraft/util/MovingObjectPosition;IF)V",
            at = @At(
                    value = "INVOKE",
                    target = "Lorg/lwjgl/opengl/GL11;glDisable(I)V",
                    remap = false,
                    ordinal = 0,
                    shift = At.Shift.AFTER),
            expect = 1)
    private void angelica$drawSelectionBoxDisableTexture2D(CallbackInfo ci) {
        Shaders.disableTexture2D();
    }

    @Inject(
            method = "sortAndRender(Lnet/minecraft/entity/EntityLivingBase;ID)I",
            at = @At(
                    value = "INVOKE",
                    target = "Lorg/lwjgl/opengl/GL11;glEnable(I)V",
                    remap = false,
                    ordinal = 0,
                    shift = At.Shift.AFTER))
    private void angelica$sortAndRenderEnableTexture2D(CallbackInfoReturnable<Integer> cir) {
        Shaders.enableTexture2D();
    }

    @Inject(
            method = "drawSelectionBox(Lnet/minecraft/entity/player/EntityPlayer;Lnet/minecraft/util/MovingObjectPosition;IF)V",
            at = @At(
                    value = "INVOKE",
                    target = "Lorg/lwjgl/opengl/GL11;glEnable(I)V",
                    remap = false,
                    ordinal = 1,
                    shift = At.Shift.AFTER),
            expect = 1)
    private void angelica$drawSelectionBoxEnableTexture2D(EntityPlayer p_72731_1_, MovingObjectPosition p_72731_2_,
            int p_72731_3_, float p_72731_4_, CallbackInfo ci) {
        Shaders.enableTexture2D();
    }

    // Fog
    @Inject(
            method = "sortAndRender(Lnet/minecraft/entity/EntityLivingBase;ID)I",
            at = @At(
                    value = "INVOKE",
                    target = "Lorg/lwjgl/opengl/GL11;glDisable(I)V",
                    remap = false,
                    ordinal = 3,
                    shift = At.Shift.AFTER))
    private void angelica$disableFog(CallbackInfoReturnable<Integer> cir) {
        Shaders.disableFog();
    }

    @Inject(
            method = "sortAndRender(Lnet/minecraft/entity/EntityLivingBase;ID)I",
            at = @At(
                    value = "INVOKE",
                    target = "Lorg/lwjgl/opengl/GL11;glEnable(I)V",
                    remap = false,
                    ordinal = 2,
                    shift = At.Shift.AFTER))
    private void angelica$enableFog(CallbackInfoReturnable<Integer> cir) {
        Shaders.enableFog();
    }

}
