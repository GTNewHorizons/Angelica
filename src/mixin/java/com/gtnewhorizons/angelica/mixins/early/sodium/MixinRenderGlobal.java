package com.gtnewhorizons.angelica.mixins.early.sodium;

import com.gtnewhorizons.angelica.compat.mojang.Camera;
import com.gtnewhorizons.angelica.compat.toremove.MatrixStack;
import com.gtnewhorizons.angelica.config.AngelicaConfig;
import com.gtnewhorizons.angelica.mixins.interfaces.IRenderGlobalExt;
import com.gtnewhorizons.angelica.rendering.AngelicaRenderQueue;
import com.gtnewhorizons.angelica.rendering.RenderingState;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import lombok.Getter;
import me.jellysquid.mods.sodium.client.gl.device.RenderDevice;
import me.jellysquid.mods.sodium.client.render.SodiumWorldRenderer;
import me.jellysquid.mods.sodium.client.render.chunk.passes.BlockRenderPass;
import net.coderbot.iris.Iris;
import net.coderbot.iris.layer.GbufferPrograms;
import net.coderbot.iris.pipeline.HandRenderer;
import net.coderbot.iris.pipeline.ShadowRenderer;
import net.coderbot.iris.pipeline.WorldRenderingPhase;
import net.coderbot.iris.pipeline.WorldRenderingPipeline;
import net.coderbot.iris.uniforms.CapturedRenderingState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.PlayerControllerMP;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.client.renderer.RenderGlobal;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.client.renderer.WorldRenderer;
import net.minecraft.client.renderer.culling.Frustrum;
import net.minecraft.client.renderer.culling.ICamera;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.init.Blocks;
import org.lwjgl.opengl.GL11;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import static org.joml.Math.lerp;

// Let other mixins apply, and then overwrite them
@Mixin(value = RenderGlobal.class, priority = 2000)
public class MixinRenderGlobal implements IRenderGlobalExt {
    @Shadow public int renderChunksWide;
    @Shadow public int renderChunksTall;
    @Shadow public int renderChunksDeep;
    @Shadow public WorldClient theWorld;
    @Shadow public Minecraft mc;
    @Shadow public int renderDistanceChunks;
    @Shadow public WorldRenderer[] worldRenderers;
    @Shadow public WorldRenderer[] sortedWorldRenderers;
    @Shadow @Final private TextureManager renderEngine;


    @Getter
    @Unique private SodiumWorldRenderer renderer;

    private int sodium$frame;

    @Override
    public void scheduleTerrainUpdate() {
        this.renderer.scheduleTerrainUpdate();
    }

    @Inject(method="<init>", at=@At("RETURN"))
    private void sodium$initRenderer(Minecraft mc, CallbackInfo ci) {
        this.renderer = SodiumWorldRenderer.create(mc);
    }

    @Inject(method="Lnet/minecraft/client/renderer/RenderGlobal;setWorldAndLoadRenderers(Lnet/minecraft/client/multiplayer/WorldClient;)V", at=@At("RETURN"))
    private void sodium$setWorldAndLoadRenderers(WorldClient world, CallbackInfo ci) {
        RenderDevice.enterManagedCode();
        try {
            this.renderer.setWorld(world);
        } finally {
            RenderDevice.exitManagedCode();
        }
    }

    /**
     * @author mitchej123, sodium
     * @reason Redirect to our renderer
     */
    @Overwrite
    public String getDebugInfoRenders() {
        return this.renderer.getChunksDebugString();
    }

    /**
     * @author Sodium
     * @reason Redirect to our renderer
    */
    @Overwrite
    public int renderSortedRenderers(int x, int z, int pass, double partialTicks) {
        // Do nothing
        return 0;
    }
    /**
     * @author Sodium
     * @reason Redirect to our renderer
     */
    @Overwrite
    public void renderAllRenderLists(int pass, double partialTicks) {
        // Do nothing
    }

    /**
     * @author Sodium
     * @reason Redirect to our renderer
     */
    @Overwrite
    private void checkOcclusionQueryResult(int x, int z) {
        // Do nothing
    }

    /**
     * @author Sodium
     * @reason Redirect to our renderer
     */
    @Overwrite
    public void markRenderersForNewPosition(int p_72722_1_, int p_72722_2_, int p_72722_3_) {
        // Do nothing
    }

    /**
     * @author Sodium
     * @reason Redirect to our renderer
     */
    @Overwrite
    public boolean updateRenderers(EntityLivingBase e, boolean b){
        AngelicaRenderQueue.processTasks(1);
        return true;
    }

    @Unique
    private void iris$beginTranslucents(WorldRenderingPipeline pipeline, Camera camera) {
        pipeline.beginHand();
        HandRenderer.INSTANCE.renderSolid(camera.getPartialTicks(), camera, mc.renderGlobal, pipeline);
        mc.mcProfiler.endStartSection("iris_pre_translucent");
        pipeline.beginTranslucents();
    }

    /**
     * @author Sodium
     * @reason Redirect to our renderer
     */
    @Overwrite
    public int sortAndRender(EntityLivingBase entity, int pass, double partialTicks) {
        final WorldRenderingPipeline pipeline;
        if(!AngelicaConfig.enableIris) {
            pipeline = null;
        } else {
            pipeline = Iris.getPipelineManager().getPipelineNullable();
            if(pass == 0) {
                pipeline.setPhase(WorldRenderingPhase.TERRAIN_CUTOUT);
            } else if(pass == 1) {
                final Camera camera = new Camera(mc.renderViewEntity, (float) partialTicks);

                if (!ShadowRenderer.ACTIVE) {
                    iris$beginTranslucents(pipeline, camera);
                }

                pipeline.setPhase(WorldRenderingPhase.TERRAIN_TRANSLUCENT);
                this.renderEngine.bindTexture(TextureMap.locationBlocksTexture);
            }
        }
        // Handle view distance change
        if(this.renderDistanceChunks != this.mc.gameSettings.renderDistanceChunks) {
            this.loadRenderers();
        }

        RenderHelper.disableStandardItemLighting();
        GL11.glEnable(GL11.GL_TEXTURE_2D);
        GL11.glEnable(GL11.GL_ALPHA_TEST);
        GL11.glEnable(GL11.GL_FOG);
        this.mc.entityRenderer.enableLightmap(partialTicks);
        // Roughly equivalent to `renderLayer`
        RenderDevice.enterManagedCode();

        final double x = lerp(entity.lastTickPosX, entity.posX, partialTicks);
        final double y = lerp(entity.lastTickPosY, entity.posY, partialTicks);
        final double z = lerp(entity.lastTickPosZ, entity.posZ, partialTicks);

        try {
            final MatrixStack matrixStack = new MatrixStack(ShadowRenderer.ACTIVE ? ShadowRenderer.MODELVIEW : RenderingState.INSTANCE.getModelViewMatrix());
            mc.mcProfiler.endStartSection("draw_chunk_layer_" + pass);
            this.renderer.drawChunkLayer(BlockRenderPass.VALUES[pass], matrixStack, x, y, z);
        } finally {
            RenderDevice.exitManagedCode();
            this.mc.entityRenderer.disableLightmap(partialTicks);
        }

        if(pipeline != null)  pipeline.setPhase(WorldRenderingPhase.NONE);

        return 0;
    }

    private boolean isSpectatorMode() {
        final PlayerControllerMP controller = Minecraft.getMinecraft().playerController;
        if(controller == null)
            return false;
        return controller.currentGameType.getID() == 3;
    }

    /**
     * @author Sodium
     * @reason Redirect to our renderer
     */
    @Overwrite
    public void clipRenderersByFrustum(ICamera frustrum, float partialTicks) {
        // Roughly equivalent to setupTerrain
        RenderDevice.enterManagedCode();

        final Frustrum frustum = (Frustrum) frustrum;
        boolean hasForcedFrustum = false;
        boolean spectator = isSpectatorMode();
        Camera camera = new Camera(mc.renderViewEntity, partialTicks);

        try {
            this.renderer.updateChunks(camera, frustum, hasForcedFrustum, sodium$frame++, spectator);
        } finally {
            RenderDevice.exitManagedCode();
        }
    }

    /**
     * @author Sodium
     * @reason Redirect to our renderer
     */
    @Overwrite
    public void markBlocksForUpdate(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
        // scheduleBlockRenders
        this.renderer.scheduleRebuildForBlockArea(minX, minY, minZ, maxX, maxY, maxZ, false);
    }


    /**
     * @author Sodium
     * @reason Redirect to our renderer
     */
    @Overwrite
    public void markBlockForUpdate(int x, int y, int z) {
        this.renderer.scheduleRebuildForBlockArea(x - 1, y - 1, z - 1, x + 1, y + 1, z + 1, false);
    }

    /**
     * @author Sodium
     * @reason Redirect to our renderer
     */
    @Overwrite
    public void markBlockForRenderUpdate(int x, int y, int z) {
        // scheduleBlockRenders
        this.renderer.scheduleRebuildForBlockArea(x - 1, y - 1, z - 1, x + 1, y + 1, z + 1, false);
    }


    /**
     * @author Sodium
     * @reason Redirect to our renderer
     */
    @Overwrite
    public void markBlockRangeForRenderUpdate(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
        // scheduleBlockRenders
        this.renderer.scheduleRebuildForBlockArea(minX, minY, minZ, maxX, maxY, maxZ, false);
    }

    @Inject(method = "loadRenderers", at = @At("RETURN"))
    private void onReload(CallbackInfo ci) {
        reload();
    }

    public void reload() {
        RenderDevice.enterManagedCode();

        try {
            this.renderer.reload();
        } finally {
            RenderDevice.exitManagedCode();
        }
    }

    /**
     * @author Sodium
     * @reason Redirect to our renderer
     */
    @Overwrite
    public void loadRenderers() {
        if (this.theWorld == null) return;
        Blocks.leaves.setGraphicsLevel(this.mc.gameSettings.fancyGraphics);
        Blocks.leaves2.setGraphicsLevel(this.mc.gameSettings.fancyGraphics);
        this.renderDistanceChunks = this.mc.gameSettings.renderDistanceChunks;
        this.worldRenderers = null;
        this.sortedWorldRenderers = null;

        this.renderChunksWide = 0;
        this.renderChunksTall = 0;
        this.renderChunksDeep = 0;
    }

    @WrapOperation(method="renderEntities", at=@At(value="INVOKE", target="Lnet/minecraft/client/renderer/entity/RenderManager;renderEntitySimple(Lnet/minecraft/entity/Entity;F)Z"))
    private boolean angelica$renderEntitySimple(RenderManager instance, Entity entity, float partialTicks, Operation<Boolean> original) {
        CapturedRenderingState.INSTANCE.setCurrentEntity(entity.getEntityId());
        GbufferPrograms.beginEntities();
        try {
            return original.call(instance, entity, partialTicks);
        } finally {
            CapturedRenderingState.INSTANCE.setCurrentEntity(-1);
            GbufferPrograms.endEntities();
        }
    }


    @Inject(method="renderEntities", at=@At(value="INVOKE", target="Lnet/minecraft/client/renderer/RenderHelper;enableStandardItemLighting()V", shift = At.Shift.AFTER))
    public void sodium$renderTileEntities(EntityLivingBase entity, ICamera camera, float partialTicks, CallbackInfo ci) {
        this.renderer.renderTileEntities(entity, camera, partialTicks);
    }

    @Redirect(method="renderEntities", at = @At(value = "INVOKE", target = "Lnet/minecraft/entity/Entity;isInRangeToRender3d(DDD)Z"))
    private boolean isInRange(Entity e, double x, double y, double z) {
        // TODO: this check is done slightly earlier than Sodium does it, make sure it doesn't cull too much
        return e.isInRangeToRender3d(x, y, z) && SodiumWorldRenderer.getInstance().isEntityVisible(e);
    }

}
