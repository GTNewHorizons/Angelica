package com.gtnewhorizons.angelica.mixins.early.shaders;

import com.gtnewhorizon.gtnhlib.client.renderer.ITessellatorInstance;
import com.gtnewhorizons.angelica.experimental.surround.Surround;
import com.gtnewhorizons.angelica.glsm.GLStateManager;
import net.coderbot.iris.Iris;
import net.coderbot.iris.layer.GbufferPrograms;
import net.coderbot.iris.pipeline.WorldRenderingPhase;
import net.coderbot.iris.pipeline.WorldRenderingPipeline;
import net.minecraft.client.renderer.RenderGlobal;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.MovingObjectPosition;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Slice;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(RenderGlobal.class)
public class MixinRenderGlobal {

    @Inject(method = "renderSky", at = @At(value = "FIELD", target = "Lnet/minecraft/client/renderer/Tessellator;instance:Lnet/minecraft/client/renderer/Tessellator;"))
    private void iris$renderSky$beginNormalSky(float partialTicks, CallbackInfo ci) {
        // None of the vanilla sky is rendered until after this call, so if anything is rendered before, it's CUSTOM_SKY.
        Iris.getPipelineManager().getPipelineNullable().setPhase(WorldRenderingPhase.SKY);
    }

    @Inject(method = "renderSky", at = @At(value = "FIELD", target = "Lnet/minecraft/client/renderer/RenderGlobal;locationSunPng:Lnet/minecraft/util/ResourceLocation;"))
    private void iris$setSunRenderStage(float p_72714_1_, CallbackInfo ci) {
        Iris.getPipelineManager().getPipelineNullable().setPhase(WorldRenderingPhase.SUN);
    }

    @Inject(method = "renderSky", at = @At(value = "FIELD", target = "Lnet/minecraft/client/renderer/RenderGlobal;locationMoonPhasesPng:Lnet/minecraft/util/ResourceLocation;"))
    private void iris$setMoonRenderStage(float p_72714_1_, CallbackInfo ci) {
        Iris.getPipelineManager().getPipelineNullable().setPhase(WorldRenderingPhase.MOON);
    }

    @Inject(method = "renderSky", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/WorldProvider;calcSunriseSunsetColors(FF)[F"))
    private void iris$setSunsetRenderStage(float p_72714_1_, CallbackInfo ci) {
        Iris.getPipelineManager().getPipelineNullable().setPhase(WorldRenderingPhase.SUNSET);
    }

    @Inject(method = "renderSky", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/multiplayer/WorldClient;getStarBrightness(F)F"))
    private void iris$setStarRenderStage(float p_72714_1_, CallbackInfo ci) {
        Iris.getPipelineManager().getPipelineNullable().setPhase(WorldRenderingPhase.STARS);
    }

    @Inject(method = "renderSky", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/entity/EntityClientPlayerMP;getPosition(F)Lnet/minecraft/util/Vec3;"))
    private void iris$setVoidRenderStage(float p_72714_1_, CallbackInfo ci) {
        Iris.getPipelineManager().getPipelineNullable().setPhase(WorldRenderingPhase.VOID);
    }

    @Inject(method = "renderSky", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/multiplayer/WorldClient;getCelestialAngle(F)F"),
        slice = @Slice(from = @At(value = "INVOKE", target = "Lnet/minecraft/client/multiplayer/WorldClient;getRainStrength(F)F")))
    private void iris$renderSky$tiltSun(float p_72714_1_, CallbackInfo ci) {
        GLStateManager.glRotatef(Iris.getPipelineManager().getPipelineNullable().getSunPathRotation(), 0.0F, 0.0F, 1.0F);
    }

    // Sky disc: wrap the glCallList(glSkyList) call — the first glCallList in the surface world branch
    @Surround(id = "skipSkyDisc", method = "renderSky",
        at = @At(value = "INVOKE", target = "Lorg/lwjgl/opengl/GL11;glCallList(I)V", ordinal = 0, remap = false),
        slice = @Slice(from = @At(value = "INVOKE", target = "Lnet/minecraft/client/multiplayer/WorldClient;getSkyColor(Lnet/minecraft/entity/Entity;F)Lnet/minecraft/util/Vec3;")))
    private void iris$skipSkyDisc() {
        WorldRenderingPipeline pipeline = Iris.getPipelineManager().getPipelineNullable();
        @Surround.Skip
        boolean skip = !(pipeline == null || pipeline.shouldRenderSkyDisc());
    }

    // Sun: wrap Tessellator.draw() after sun texture bind, before moon texture bind
    @Surround(id = "skipSun", method = "renderSky",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/Tessellator;draw()I", ordinal = 0),
        slice = @Slice(
            from = @At(value = "FIELD", target = "Lnet/minecraft/client/renderer/RenderGlobal;locationSunPng:Lnet/minecraft/util/ResourceLocation;"),
            to = @At(value = "FIELD", target = "Lnet/minecraft/client/renderer/RenderGlobal;locationMoonPhasesPng:Lnet/minecraft/util/ResourceLocation;")))
    private void iris$skipSun() {
        WorldRenderingPipeline pipeline = Iris.getPipelineManager().getPipelineNullable();
        @Surround.Skip
        boolean skip = !(pipeline == null || pipeline.shouldRenderSun());
    }

    @Surround.Skipped("skipSun")
    private int iris$skipSunSkipped(Tessellator instance) {
        ((ITessellatorInstance) instance).discard();
        return 0;
    }

    // Moon: wrap Tessellator.draw() after moon texture bind, before getStarBrightness
    @Surround(id = "skipMoon", method = "renderSky",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/Tessellator;draw()I", ordinal = 0),
        slice = @Slice(
            from = @At(value = "FIELD", target = "Lnet/minecraft/client/renderer/RenderGlobal;locationMoonPhasesPng:Lnet/minecraft/util/ResourceLocation;"),
            to = @At(value = "INVOKE", target = "Lnet/minecraft/client/multiplayer/WorldClient;getStarBrightness(F)F")))
    private void iris$skipMoon() {
        WorldRenderingPipeline pipeline = Iris.getPipelineManager().getPipelineNullable();
        @Surround.Skip
        boolean skip = !(pipeline == null || pipeline.shouldRenderMoon());
    }

    @Surround.Skipped("skipMoon")
    private int iris$skipMoonSkipped(Tessellator instance) {
        ((ITessellatorInstance) instance).discard();
        return 0;
    }

    // Stars: wrap glCallList(starGLCallList) — the glCallList after getStarBrightness
    @Surround(id = "skipStars", method = "renderSky",
        at = @At(value = "INVOKE", target = "Lorg/lwjgl/opengl/GL11;glCallList(I)V", ordinal = 0, remap = false),
        slice = @Slice(from = @At(value = "INVOKE", target = "Lnet/minecraft/client/multiplayer/WorldClient;getStarBrightness(F)F")))
    private void iris$skipStars() {
        WorldRenderingPipeline pipeline = Iris.getPipelineManager().getPipelineNullable();
        @Surround.Skip
        boolean skip = !(pipeline == null || pipeline.shouldRenderStars());
    }

    @Surround(id = "startOutline", method = "drawSelectionBox")
    private void iris$startOutline(EntityPlayer player, MovingObjectPosition pos, int p_72731_3_, float p_72731_4_) {
        GbufferPrograms.beginOutline();
    }

    @Surround.Finally("startOutline")
    private void iris$endOutline() {
        GbufferPrograms.endOutline();
    }

    @Inject(method = "renderEntities", at = @At(value = "INVOKE_STRING", target = "Lnet/minecraft/profiler/Profiler;endStartSection(Ljava/lang/String;)V", args = "ldc=global"))
    private void iris$beginEntityLoop(CallbackInfo ci) {
        GbufferPrograms.beginEntityLoop();
    }

    @Inject(method = "renderEntities", at = @At(value = "INVOKE_STRING", target = "Lnet/minecraft/profiler/Profiler;endStartSection(Ljava/lang/String;)V", args = "ldc=blockentities"))
    private void iris$endEntityLoop(CallbackInfo ci) {
        GbufferPrograms.endEntityLoop();
    }

    @Inject(method="renderEntities", at=@At(value="INVOKE", target="Lnet/minecraft/client/renderer/entity/RenderManager;renderEntitySimple(Lnet/minecraft/entity/Entity;F)Z"))
    private void angelica$renderEntitySimple(CallbackInfo ci) {
        GbufferPrograms.onEntityRenderBoundary();
    }
}
