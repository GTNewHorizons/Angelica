package com.gtnewhorizons.angelica.mixins.early.shaders;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Share;
import com.llamalad7.mixinextras.sugar.ref.LocalRef;
import it.unimi.dsi.fastutil.objects.Object2IntFunction;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import net.coderbot.iris.Iris;
import net.coderbot.iris.block_rendering.BlockRenderingSettings;
import net.coderbot.iris.layer.GbufferPrograms;
import net.coderbot.iris.pipeline.WorldRenderingPhase;
import net.coderbot.iris.pipeline.WorldRenderingPipeline;
import net.coderbot.iris.shaderpack.materialmap.NamespacedId;
import net.coderbot.iris.uniforms.CapturedRenderingState;
import net.minecraft.client.renderer.RenderGlobal;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityList;
import net.minecraft.entity.effect.EntityLightningBolt;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.MovingObjectPosition;
import org.lwjgl.opengl.GL11;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Slice;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(RenderGlobal.class)
public class MixinRenderGlobal {
    @Unique private static final NamespacedId LIGHTNING_BOLT_ID = new NamespacedId("minecraft", "lightning_bolt");
    @Unique private static final Object2IntOpenHashMap<String> angelica$entityIdCache = new Object2IntOpenHashMap<>();
    @Unique private static Object2IntFunction<NamespacedId> angelica$cachedEntityIdMap;

    static {
        angelica$entityIdCache.defaultReturnValue(Integer.MIN_VALUE);
    }

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
        GL11.glRotatef(pipeline.get().getSunPathRotation(), 0.0F, 0.0F, 1.0F);
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
        int entityId = entity.getEntityId();

        Object2IntFunction<NamespacedId> entityIdMap = BlockRenderingSettings.INSTANCE.getEntityIds();
        if (entityIdMap != null) {
            // Invalidate cache if the map changed (shader reload)
            if (entityIdMap != angelica$cachedEntityIdMap) {
                angelica$entityIdCache.clear();
                angelica$cachedEntityIdMap = entityIdMap;
            }

            if (entity instanceof EntityLightningBolt) {
                entityId = entityIdMap.applyAsInt(LIGHTNING_BOLT_ID);
            } else {
                String entityType = EntityList.getEntityString(entity);
                if (entityType != null) {
                    int cached = angelica$entityIdCache.getInt(entityType);
                    if (cached == Integer.MIN_VALUE) {
                        cached = entityIdMap.applyAsInt(new NamespacedId(entityType));
                        angelica$entityIdCache.put(entityType, cached);
                    }
                    entityId = cached;
                }
            }
        }

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
