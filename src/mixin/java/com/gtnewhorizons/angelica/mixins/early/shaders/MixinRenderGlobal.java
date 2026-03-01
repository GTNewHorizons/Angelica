package com.gtnewhorizons.angelica.mixins.early.shaders;

import com.gtnewhorizon.gtnhlib.client.renderer.ITessellatorInstance;
import com.gtnewhorizons.angelica.glsm.GLStateManager;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Share;
import com.llamalad7.mixinextras.sugar.ref.LocalRef;
import net.coderbot.iris.Iris;
import net.coderbot.iris.layer.GbufferPrograms;
import net.coderbot.iris.pipeline.WorldRenderingPhase;
import net.coderbot.iris.pipeline.WorldRenderingPipeline;
import net.coderbot.iris.uniforms.CapturedRenderingState;
import net.coderbot.iris.uniforms.EntityIdHelper;
import net.minecraft.client.renderer.RenderGlobal;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.entity.Entity;
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
    private void iris$renderSky$beginNormalSky(float partialTicks, CallbackInfo ci, @Share("pipeline") LocalRef<WorldRenderingPipeline> pipeline) {
        // None of the vanilla sky is rendered until after this call, so if anything is rendered before, it's CUSTOM_SKY.
        pipeline.set(Iris.getPipelineManager().getPipelineNullable());
        pipeline.get().setPhase(WorldRenderingPhase.SKY);
    }

    @Inject(method = "renderSky", at = @At(value = "FIELD", target = "Lnet/minecraft/client/renderer/RenderGlobal;locationSunPng:Lnet/minecraft/util/ResourceLocation;"))
    private void iris$setSunRenderStage(float p_72714_1_, CallbackInfo ci, @Share("pipeline") LocalRef<WorldRenderingPipeline> pipeline) {
        pipeline.get().setPhase(WorldRenderingPhase.SUN);
    }

    @Inject(method = "renderSky", at = @At(value = "FIELD", target = "Lnet/minecraft/client/renderer/RenderGlobal;locationMoonPhasesPng:Lnet/minecraft/util/ResourceLocation;"))
    private void iris$setMoonRenderStage(float p_72714_1_, CallbackInfo ci, @Share("pipeline") LocalRef<WorldRenderingPipeline> pipeline) {
        pipeline.get().setPhase(WorldRenderingPhase.MOON);
    }

    @Inject(method = "renderSky", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/WorldProvider;calcSunriseSunsetColors(FF)[F"))
    private void iris$setSunsetRenderStage(float p_72714_1_, CallbackInfo ci, @Share("pipeline") LocalRef<WorldRenderingPipeline> pipeline) {
        pipeline.get().setPhase(WorldRenderingPhase.SUNSET);
    }

    @Inject(method = "renderSky", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/multiplayer/WorldClient;getStarBrightness(F)F"))
    private void iris$setStarRenderStage(float p_72714_1_, CallbackInfo ci, @Share("pipeline") LocalRef<WorldRenderingPipeline> pipeline) {
        pipeline.get().setPhase(WorldRenderingPhase.STARS);
    }

    @Inject(method = "renderSky", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/entity/EntityClientPlayerMP;getPosition(F)Lnet/minecraft/util/Vec3;"))
    private void iris$setVoidRenderStage(float p_72714_1_, CallbackInfo ci, @Share("pipeline") LocalRef<WorldRenderingPipeline> pipeline) {
        pipeline.get().setPhase(WorldRenderingPhase.VOID);
    }

    @Inject(method = "renderSky", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/multiplayer/WorldClient;getCelestialAngle(F)F"),
        slice = @Slice(from = @At(value = "INVOKE", target = "Lnet/minecraft/client/multiplayer/WorldClient;getRainStrength(F)F")))
    private void iris$renderSky$tiltSun(float p_72714_1_, CallbackInfo ci, @Share("pipeline") LocalRef<WorldRenderingPipeline> pipeline) {
        GLStateManager.glRotatef(pipeline.get().getSunPathRotation(), 0.0F, 0.0F, 1.0F);
    }

    // Sky disc: wrap the glCallList(glSkyList) call — the first glCallList in the surface world branch
    @WrapOperation(method = "renderSky",
        at = @At(value = "INVOKE", target = "Lorg/lwjgl/opengl/GL11;glCallList(I)V", ordinal = 0, remap = false),
        slice = @Slice(from = @At(value = "INVOKE", target = "Lnet/minecraft/client/multiplayer/WorldClient;getSkyColor(Lnet/minecraft/entity/Entity;F)Lnet/minecraft/util/Vec3;")))
    private void iris$skipSkyDisc(int list, Operation<Void> original) {
        WorldRenderingPipeline pipeline = Iris.getPipelineManager().getPipelineNullable();
        if (pipeline == null || pipeline.shouldRenderSkyDisc()) {
            original.call(list);
        }
    }

    // Sun: wrap Tessellator.draw() after sun texture bind, before moon texture bind
    @WrapOperation(method = "renderSky",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/Tessellator;draw()I", ordinal = 0),
        slice = @Slice(
            from = @At(value = "FIELD", target = "Lnet/minecraft/client/renderer/RenderGlobal;locationSunPng:Lnet/minecraft/util/ResourceLocation;"),
            to = @At(value = "FIELD", target = "Lnet/minecraft/client/renderer/RenderGlobal;locationMoonPhasesPng:Lnet/minecraft/util/ResourceLocation;")))
    private int iris$skipSun(Tessellator instance, Operation<Integer> original) {
        WorldRenderingPipeline pipeline = Iris.getPipelineManager().getPipelineNullable();
        if (pipeline == null || pipeline.shouldRenderSun()) {
            return original.call(instance);
        }
        ((ITessellatorInstance) instance).discard();
        return 0;
    }

    // Moon: wrap Tessellator.draw() after moon texture bind, before getStarBrightness
    @WrapOperation(method = "renderSky",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/Tessellator;draw()I", ordinal = 0),
        slice = @Slice(
            from = @At(value = "FIELD", target = "Lnet/minecraft/client/renderer/RenderGlobal;locationMoonPhasesPng:Lnet/minecraft/util/ResourceLocation;"),
            to = @At(value = "INVOKE", target = "Lnet/minecraft/client/multiplayer/WorldClient;getStarBrightness(F)F")))
    private int iris$skipMoon(Tessellator instance, Operation<Integer> original) {
        WorldRenderingPipeline pipeline = Iris.getPipelineManager().getPipelineNullable();
        if (pipeline == null || pipeline.shouldRenderMoon()) {
            return original.call(instance);
        }
        ((ITessellatorInstance) instance).discard();
        return 0;
    }

    // Stars: wrap glCallList(starGLCallList) — the glCallList after getStarBrightness
    @WrapOperation(method = "renderSky",
        at = @At(value = "INVOKE", target = "Lorg/lwjgl/opengl/GL11;glCallList(I)V", ordinal = 0, remap = false),
        slice = @Slice(from = @At(value = "INVOKE", target = "Lnet/minecraft/client/multiplayer/WorldClient;getStarBrightness(F)F")))
    private void iris$skipStars(int list, Operation<Void> original) {
        WorldRenderingPipeline pipeline = Iris.getPipelineManager().getPipelineNullable();
        if (pipeline == null || pipeline.shouldRenderStars()) {
            original.call(list);
        }
    }

    @Inject(method="drawSelectionBox", at=@At(value="HEAD"))
    private void iris$startOutline(EntityPlayer player, MovingObjectPosition pos, int p_72731_3_, float p_72731_4_, CallbackInfo ci) {
        GbufferPrograms.beginOutline();
    }
    @Inject(method="drawSelectionBox", at=@At(value="RETURN"))
    private void iris$endOutline(EntityPlayer player, MovingObjectPosition pos, int p_72731_3_, float p_72731_4_, CallbackInfo ci) {
        GbufferPrograms.endOutline();
    }

    @WrapOperation(method="renderEntities", at=@At(value="INVOKE", target="Lnet/minecraft/client/renderer/entity/RenderManager;renderEntitySimple(Lnet/minecraft/entity/Entity;F)Z"))
    private boolean angelica$renderEntitySimple(RenderManager instance, Entity entity, float partialTicks, Operation<Boolean> original) {
        int entityId = EntityIdHelper.getEntityId(entity);

        CapturedRenderingState.INSTANCE.setCurrentEntity(entityId);
        GbufferPrograms.beginEntities();
        try {
            return original.call(instance, entity, partialTicks);
        } finally {
            CapturedRenderingState.INSTANCE.setCurrentEntity(-1);
            GbufferPrograms.endEntities();
        }
    }
}
