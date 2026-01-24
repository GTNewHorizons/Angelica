package com.gtnewhorizons.angelica.mixins.early.celeritas.terrain;

import com.gtnewhorizons.angelica.compat.mojang.Camera;
import com.gtnewhorizons.angelica.compat.mojang.GameModeUtil;
import com.gtnewhorizons.angelica.glsm.GLStateManager;
import com.gtnewhorizons.angelica.mixins.interfaces.IRenderGlobalExt;
import com.gtnewhorizons.angelica.rendering.AngelicaRenderQueue;
import com.gtnewhorizons.angelica.rendering.RenderingState;
import com.gtnewhorizons.angelica.rendering.celeritas.BlockRenderLayer;
import com.gtnewhorizons.angelica.rendering.celeritas.CeleritasSetup;
import com.gtnewhorizons.angelica.rendering.celeritas.CeleritasWorldRenderer;
import net.coderbot.iris.Iris;
import net.coderbot.iris.layer.GbufferPrograms;
import net.coderbot.iris.pipeline.HandRenderer;
import net.coderbot.iris.pipeline.WorldRenderingPhase;
import net.coderbot.iris.pipeline.WorldRenderingPipeline;
import net.coderbot.iris.shadows.ShadowRenderingState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.GLAllocation;
import net.minecraft.client.renderer.RenderGlobal;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.client.renderer.culling.ICamera;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import org.embeddedt.embeddium.impl.gl.device.RenderDevice;
import org.embeddedt.embeddium.impl.render.chunk.shader.ChunkShaderFogComponent;
import org.embeddedt.embeddium.impl.render.terrain.SimpleWorldRenderer;
import org.embeddedt.embeddium.impl.render.viewport.ViewportProvider;
import org.lwjgl.opengl.GL11;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import static org.joml.Math.lerp;

/**
 * RenderGlobal mixin for celeritas integration.
 * Redirects chunk rendering to CeleritasWorldRenderer/AngelicaRenderSectionManager.
 */
@Mixin(value = RenderGlobal.class, priority = 900)
public class MixinRenderGlobal implements IRenderGlobalExt {
    @Shadow public Minecraft mc;
    @Shadow @Final private TextureManager renderEngine;

    @Unique private CeleritasWorldRenderer celeritas$renderer;
    @Unique private int celeritas$frame;
    @Unique private float celeritas$lastFov;

    @Unique
    private boolean angelica$isSpectatorMode() {
        return GameModeUtil.isSpectator();
    }

    @Redirect(method = "<init>(Lnet/minecraft/client/Minecraft;)V", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/GLAllocation;generateDisplayLists(I)I", ordinal = 0))
    private int celeritas$minimizeDisplayListAllocation(int count) {
        // Minimize allocation - celeritas replaces WorldRenderer with its own rendering system
        return GLAllocation.generateDisplayLists(3);
    }

    @Redirect(method = "loadRenderers", at = @At(value = "FIELD", target = "Lnet/minecraft/client/renderer/RenderGlobal;renderDistanceChunks:I", opcode = Opcodes.GETFIELD, ordinal = 0))
    private int celeritas$nullifyBuiltChunkStorage(RenderGlobal self) {
        // Minimize resource allocation
        return 0;
    }

    @ModifyConstant(method = "loadRenderers", constant = @Constant(intValue = 16, ordinal = 0), require = 1)
    private int celeritas$nullifyBuiltChunkStorage2(int constant) {
        // Minimize resource allocation
        return 1;
    }

    @Inject(method = "<init>", at = @At("RETURN"))
    private void celeritas$initRenderer(Minecraft mc, CallbackInfo ci) {
        this.celeritas$renderer = CeleritasWorldRenderer.create(mc);
    }

    @Inject(method = "Lnet/minecraft/client/renderer/RenderGlobal;setWorldAndLoadRenderers(Lnet/minecraft/client/multiplayer/WorldClient;)V", at = @At("RETURN"))
    private void celeritas$setWorldAndLoadRenderers(WorldClient world, CallbackInfo ci) {
        RenderDevice.enterManagedCode();
        try {
            this.celeritas$renderer.setWorld(world);
        } finally {
            RenderDevice.exitManagedCode();
        }

        if (world != null && mc.renderViewEntity != null) {
            final Entity e = mc.renderViewEntity;
            Camera.INSTANCE.getPos().set(e.posX, e.posY + e.getEyeHeight(), e.posZ);
            e.lastTickPosX = e.posX;
            e.lastTickPosY = e.posY;
            e.lastTickPosZ = e.posZ;
        }
    }

    @Inject(method = "onStaticEntitiesChanged", at = @At("RETURN"))
    private void onTerrainUpdateScheduled(CallbackInfo ci) {
        this.celeritas$renderer.scheduleTerrainUpdate();
    }

    @Unique
    private static boolean sortAndRenderLogged = false;

    /**
     * @author celeritas
     * @reason Redirect to our renderer with Iris phase support
     */
    @Overwrite
    public int sortAndRender(EntityLivingBase entity, int pass, double partialTicks) {
        if (!sortAndRenderLogged && ShadowRenderingState.areShadowsCurrentlyBeingRendered()) {
            Iris.logger.info("[SHADOW DEBUG] sortAndRender called! pass={}", pass);
            sortAndRenderLogged = true;
        }

        final WorldRenderingPipeline pipeline;
        if (!Iris.enabled) {
            pipeline = null;
        } else {
            pipeline = Iris.getPipelineManager().getPipelineNullable();
            if (pass == 0) {
                pipeline.setPhase(WorldRenderingPhase.TERRAIN_CUTOUT);
            } else if (pass == 1) {
                if (!ShadowRenderingState.areShadowsCurrentlyBeingRendered()) {
                    iris$beginTranslucents(pipeline, Camera.INSTANCE);
                }
                pipeline.setPhase(WorldRenderingPhase.TERRAIN_TRANSLUCENT);
                this.renderEngine.bindTexture(TextureMap.locationBlocksTexture);
            }
        }

        RenderDevice.enterManagedCode();

        RenderHelper.disableStandardItemLighting();

        GLStateManager.glActiveTexture(OpenGlHelper.defaultTexUnit);
        GLStateManager.glBindTexture(GL11.GL_TEXTURE_2D, this.mc.getTextureMapBlocks().getGlTextureId());
        GLStateManager.glEnable(GL11.GL_TEXTURE_2D);

        GLStateManager.glEnable(GL11.GL_ALPHA_TEST);
        GLStateManager.glEnable(GL11.GL_FOG);

        this.mc.entityRenderer.enableLightmap(partialTicks);

        final double camX = lerp(entity.lastTickPosX, entity.posX, partialTicks);
        final double camY = lerp(entity.lastTickPosY, entity.posY, partialTicks);
        final double camZ = lerp(entity.lastTickPosZ, entity.posZ, partialTicks);

        try {
            if (pass == 0) {
                mc.mcProfiler.endStartSection("draw_chunk_layer_solid");
                this.celeritas$renderer.drawChunkLayer(BlockRenderLayer.SOLID, camX, camY, camZ);
                mc.mcProfiler.endStartSection("draw_chunk_layer_cutout_mipped");
                this.celeritas$renderer.drawChunkLayer(BlockRenderLayer.CUTOUT_MIPPED, camX, camY, camZ);
            } else {
                mc.mcProfiler.endStartSection("draw_chunk_layer_translucent");
                this.celeritas$renderer.drawChunkLayer(BlockRenderLayer.TRANSLUCENT, camX, camY, camZ);
            }
        } finally {
            RenderDevice.exitManagedCode();
        }

        this.mc.entityRenderer.disableLightmap(partialTicks);

        if (pipeline != null) pipeline.setPhase(WorldRenderingPhase.NONE);

        return 0;
    }

    /**
     * @author celeritas
     * @reason Redirect terrain setup to our renderer
     */
    @Overwrite
    public void clipRenderersByFrustum(ICamera camera, float partialTicks) {
        RenderDevice.enterManagedCode();

        try {
            final Entity viewEntity = this.mc.renderViewEntity;
            final float fogDistance = ChunkShaderFogComponent.FOG_SERVICE.getFogCutoff();

            final double camX = viewEntity.posX;
            final double camY = viewEntity.posY + viewEntity.getEyeHeight();
            final double camZ = viewEntity.posZ;

            final float currentFov = RenderingState.INSTANCE.getFov();
            if (currentFov != this.celeritas$lastFov) {
                this.celeritas$renderer.scheduleTerrainUpdate();
                this.celeritas$lastFov = currentFov;
            }

            final SimpleWorldRenderer.CameraState cameraState = new SimpleWorldRenderer.CameraState(
                camX, camY, camZ,
                viewEntity.rotationPitch, viewEntity.rotationYaw, fogDistance
            );
            this.celeritas$renderer.setupTerrain(((ViewportProvider)camera).sodium$createViewport(), cameraState, this.celeritas$frame++, angelica$isSpectatorMode(), false);
        } finally {
            RenderDevice.exitManagedCode();
        }
    }

    /**
     * @author celeritas
     * @reason Redirect to our renderer
     */
    @Overwrite
    public void markBlocksForUpdate(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
        this.celeritas$renderer.scheduleRebuildForBlockArea(minX, minY, minZ, maxX, maxY, maxZ, false);
    }

    @Unique
    public void angelica$reload() {
        CeleritasSetup.ensureInitialized();
        RenderDevice.enterManagedCode();
        try {
            this.celeritas$renderer.reload();
        } finally {
            RenderDevice.exitManagedCode();
        }
    }

    @Inject(method = "loadRenderers", at = @At("RETURN"))
    private void onReload(CallbackInfo ci) {
        angelica$reload();
    }



    @Inject(method = "renderEntities", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/RenderHelper;enableStandardItemLighting()V", shift = At.Shift.AFTER, ordinal = 0), cancellable = true)
    public void celeritas$renderTileEntities(EntityLivingBase entity, ICamera camera, float partialTicks, CallbackInfo ci) {
        if (Iris.enabled) {
            GbufferPrograms.beginBlockEntities();
            GbufferPrograms.setBlockEntityDefaults();
        }
        this.celeritas$renderer.renderBlockEntities(partialTicks);
        if (Iris.enabled) {
            GbufferPrograms.endBlockEntities();
        }
        this.mc.entityRenderer.disableLightmap(partialTicks);
        this.mc.mcProfiler.endSection();
        ci.cancel();
    }

    /**
     * @author celeritas
     * @reason Redirect to our renderer
     */
    @Overwrite
    public String getDebugInfoRenders() {
        return this.celeritas$renderer.getChunksDebugString();
    }





    /**
     * @author celeritas
     * @reason Process render queue with 5ms budget, track metrics
     */
    @Overwrite
    public boolean updateRenderers(EntityLivingBase e, boolean b) {
        final int BUDGET_NS = 5 * 1000 * 1000;
        final long startTime = System.nanoTime();
        int tasksRan = 0;

        while (System.nanoTime() - startTime < BUDGET_NS) {
            if (AngelicaRenderQueue.processTasks(1) == 0)
                break;
            tasksRan++;
        }

        AngelicaRenderQueue.recordFrameStats(tasksRan, System.nanoTime() - startTime);
        return true;
    }

    @Unique
    private void iris$beginTranslucents(WorldRenderingPipeline pipeline, Camera camera) {
        pipeline.beginHand();
        HandRenderer.INSTANCE.renderSolid(camera.getPartialTicks(), camera, mc.renderGlobal, pipeline);
        mc.mcProfiler.endStartSection("iris_pre_translucent");
        pipeline.beginTranslucents();
    }

    @Redirect(method = "renderEntities", at = @At(value = "INVOKE", target = "Lnet/minecraft/entity/Entity;isInRangeToRender3d(DDD)Z"))
    private boolean isInRange(Entity e, double x, double y, double z) {
        return e.isInRangeToRender3d(x, y, z) && celeritas$renderer.isEntityVisible(e);
    }


    @Override
    public void angelica$scheduleTerrainUpdate() {
        this.celeritas$renderer.scheduleTerrainUpdate();
    }

}
