package com.gtnewhorizons.angelica.mixins.early.renderer;

import jss.notfine.core.Settings;
import jss.notfine.render.RenderStars;
import net.minecraft.client.renderer.RenderGlobal;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.culling.ICamera;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.MovingObjectPosition;

import org.lwjgl.opengl.GL11;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.gtnewhorizon.mixinextras.injector.wrapoperation.Operation;
import com.gtnewhorizon.mixinextras.injector.wrapoperation.WrapOperation;
import com.gtnewhorizons.angelica.client.Shaders;

@Mixin(RenderGlobal.class)
public class MixinRenderGlobal {

    @Inject(
            method = "renderEntities(Lnet/minecraft/entity/EntityLivingBase;Lnet/minecraft/client/renderer/culling/ICamera;F)V",
            at = @At(value = "INVOKE_STRING", target = "Lnet/minecraft/profiler/Profiler;endStartSection(Ljava/lang/String;)V", args = "ldc=entities"))
    private void angelica$beginEntities(EntityLivingBase entity, ICamera camera, float p_147589_3_, CallbackInfo ci) {
        Shaders.beginEntities();
    }

    @Inject(
            method = "renderEntities(Lnet/minecraft/entity/EntityLivingBase;Lnet/minecraft/client/renderer/culling/ICamera;F)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/entity/RenderManager;renderEntitySimple(Lnet/minecraft/entity/Entity;F)Z", ordinal = 1, shift = At.Shift.AFTER))
    private void angelica$nextEntity(EntityLivingBase entity, ICamera camera, float p_147589_3_, CallbackInfo ci) {
        Shaders.nextEntity();
    }

    @Inject(
            method = "renderEntities(Lnet/minecraft/entity/EntityLivingBase;Lnet/minecraft/client/renderer/culling/ICamera;F)V",
            at = @At(value = "INVOKE_STRING", target = "Lnet/minecraft/profiler/Profiler;endStartSection(Ljava/lang/String;)V", args = "ldc=blockentities", shift = At.Shift.AFTER))
    private void angelica$endEntitiesAndBeginBlockEntities(EntityLivingBase entity, ICamera camera, float p_147589_3_,
            CallbackInfo ci) {
        Shaders.endEntities();
        Shaders.beginBlockEntities();
    }

    @Inject(
            method = "renderEntities(Lnet/minecraft/entity/EntityLivingBase;Lnet/minecraft/client/renderer/culling/ICamera;F)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/EntityRenderer;disableLightmap(D)V", shift = At.Shift.AFTER))
    private void angelica$endBlockEntities(EntityLivingBase entity, ICamera camera, float p_147589_3_,
            CallbackInfo ci) {
        Shaders.endBlockEntities();
    }

    // Texture 2D
    @Inject(
            method = "sortAndRender(Lnet/minecraft/entity/EntityLivingBase;ID)I",
            at = @At(value = "INVOKE", target = "Lorg/lwjgl/opengl/GL11;glDisable(I)V", remap = false, ordinal = 0, shift = At.Shift.AFTER), expect = 0)
    private void angelica$sortandRenderDisableTexture2D(EntityLivingBase p_72719_1_, int p_72719_2_, double p_72719_3_, CallbackInfoReturnable<Integer> cir) {
        // Note: Conflicts with OcclusionRenderer
        Shaders.disableTexture2D();
    }

    @Inject(
            method = "drawSelectionBox(Lnet/minecraft/entity/player/EntityPlayer;Lnet/minecraft/util/MovingObjectPosition;IF)V",
            at = @At(value = "INVOKE", target = "Lorg/lwjgl/opengl/GL11;glDisable(I)V", remap = false, ordinal = 0, shift = At.Shift.AFTER), expect = 1)
    private void angelica$drawSelectionBoxDisableTexture2D(CallbackInfo ci) {
        Shaders.disableTexture2D();
    }

    @Inject(
            method = "sortAndRender(Lnet/minecraft/entity/EntityLivingBase;ID)I",
            at = @At(value = "INVOKE", target = "Lorg/lwjgl/opengl/GL11;glEnable(I)V", remap = false, ordinal = 0, shift = At.Shift.AFTER), expect = 0)
    private void angelica$sortAndRenderEnableTexture2D(CallbackInfoReturnable<Integer> cir) {
        // Note: Conflicts with OcclusionRenderer
        Shaders.enableTexture2D();
    }

    @Inject(
            method = "drawSelectionBox(Lnet/minecraft/entity/player/EntityPlayer;Lnet/minecraft/util/MovingObjectPosition;IF)V",
            at = @At(value = "INVOKE", target = "Lorg/lwjgl/opengl/GL11;glEnable(I)V", remap = false, ordinal = 1, shift = At.Shift.AFTER), expect = 1)
    private void angelica$drawSelectionBoxEnableTexture2D(EntityPlayer p_72731_1_, MovingObjectPosition p_72731_2_,
            int p_72731_3_, float p_72731_4_, CallbackInfo ci) {
        Shaders.enableTexture2D();
    }

    // Fog
    @Inject(
            method = "sortAndRender(Lnet/minecraft/entity/EntityLivingBase;ID)I",
            at = @At(value = "INVOKE", target = "Lorg/lwjgl/opengl/GL11;glDisable(I)V", remap = false, ordinal = 3, shift = At.Shift.AFTER), expect = 0)
    private void angelica$sortAndRenderDisableFog(CallbackInfoReturnable<Integer> cir) {
        // Note: Conflicts with OcclusionRenderer
        Shaders.disableFog();
    }

    @Inject(
            method = "sortAndRender(Lnet/minecraft/entity/EntityLivingBase;ID)I",
            at = @At(value = "INVOKE", target = "Lorg/lwjgl/opengl/GL11;glEnable(I)V", remap = false, ordinal = 2, shift = At.Shift.AFTER), expect = 0)
    private void angelica$sortAndRenderEnableFog(CallbackInfoReturnable<Integer> cir) {
        // Note: Conflicts with OcclusionRenderer
        Shaders.enableFog();
    }

    // RenderSky
    @WrapOperation(method = "renderSky(F)V", at = @At(value = "INVOKE", target = "Lorg/lwjgl/opengl/GL11;glDisable(I)V", remap = false))
    private void angelica$renderSkyDisable(int cap, Operation<Void> original) {
        original.call(cap);

        if (cap == GL11.GL_FOG) Shaders.disableFog();
        else if (cap == GL11.GL_TEXTURE_2D) Shaders.disableTexture2D();
    }

    @WrapOperation(method = "renderSky(F)V", at = @At(value = "INVOKE", target = "Lorg/lwjgl/opengl/GL11;glEnable(I)V", remap = false))
    private void angelica$renderSkyEnable(int cap, Operation<Void> original) {
        original.call(cap);
        if (cap == GL11.GL_FOG) Shaders.enableFog();
        else if (cap == GL11.GL_TEXTURE_2D) Shaders.enableTexture2D();
    }

    @Inject(
            method = "renderSky(F)V",
            at = @At(value = "INVOKE", target = "Lorg/lwjgl/opengl/GL11;glCallList(I)V", remap = false, ordinal = 0, shift = At.Shift.BEFORE, by = 2))
    private void angelica$preSkyList(float p_72714_1_, CallbackInfo ci) {
        Shaders.preSkyList();
    }

    @Inject(
            method = "renderSky(F)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/multiplayer/WorldClient;getCelestialAngle(F)F", ordinal = 1, shift = At.Shift.BY, by = -2))
    private void angelica$preCelestialRotate(float p_72714_1_, CallbackInfo ci) {
        Shaders.preCelestialRotate();
    }

    @Inject(
            method = "renderSky(F)V",
            at = @At(value = "INVOKE", target = "Lorg/lwjgl/opengl/GL11;glRotatef(FFFF)V", remap = false, ordinal = 9, shift = At.Shift.AFTER))
    private void angelica$postCelestialRotate(float p_72714_1_, CallbackInfo ci) {
        Shaders.postCelestialRotate();
    }

    // drawBlockDamageTexture

    @WrapOperation(
            method = "drawBlockDamageTexture(Lnet/minecraft/client/renderer/Tessellator;Lnet/minecraft/entity/EntityLivingBase;F)V",
            remap = false,
            at = @At(value = "INVOKE", target = "Lorg/lwjgl/opengl/GL11;glDisable(I)V", remap = false))
    private void angelica$drawBlockDamageTextureDisable(int cap, Operation<Void> original) {
        original.call(cap);

        if (cap == GL11.GL_TEXTURE_2D) Shaders.disableTexture2D();
    }

    @WrapOperation(
            method = "drawBlockDamageTexture(Lnet/minecraft/client/renderer/Tessellator;Lnet/minecraft/entity/EntityLivingBase;F)V",
            remap = false,
            at = @At(value = "INVOKE", target = "Lorg/lwjgl/opengl/GL11;glEnable(I)V", remap = false))
    private void angelica$drawBlockDamageTextureSkyEnable(int cap, Operation<Void> original) {
        original.call(cap);
        if (cap == GL11.GL_TEXTURE_2D) Shaders.enableTexture2D();
    }

    @Inject(
            method = "drawBlockDamageTexture(Lnet/minecraft/client/renderer/Tessellator;Lnet/minecraft/entity/EntityLivingBase;F)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/Tessellator;startDrawingQuads()V", ordinal = 0, shift = At.Shift.BEFORE))
    private void angelica$beginBlockDestroyProgress(Tessellator p_72717_1_, EntityLivingBase p_72717_2_,
            float p_72717_3_, CallbackInfo ci) {
        Shaders.beginBlockDestroyProgress();
    }

    @Inject(
            method = "drawBlockDamageTexture(Lnet/minecraft/client/renderer/Tessellator;Lnet/minecraft/entity/EntityLivingBase;F)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/Tessellator;setTranslation(DDD)V", ordinal = 1, shift = At.Shift.AFTER))
    private void angelica$endBlockDestroyProgress(Tessellator p_72717_1_, EntityLivingBase p_72717_2_, float p_72717_3_,
            CallbackInfo ci) {
        Shaders.endBlockDestroyProgress();
    }
}
