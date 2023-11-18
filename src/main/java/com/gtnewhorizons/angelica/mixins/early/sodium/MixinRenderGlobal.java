package com.gtnewhorizons.angelica.mixins.early.sodium;

import com.google.common.collect.ImmutableList;
import com.gtnewhorizons.angelica.compat.mojang.Camera;
import com.gtnewhorizons.angelica.compat.mojang.MatrixStack;
import com.gtnewhorizons.angelica.compat.mojang.RenderLayer;
import com.gtnewhorizons.angelica.mixins.interfaces.IRenderGlobalExt;
import me.jellysquid.mods.sodium.client.gl.device.RenderDevice;
import me.jellysquid.mods.sodium.client.render.SodiumWorldRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.client.renderer.RenderGlobal;
import net.minecraft.client.renderer.WorldRenderer;
import net.minecraft.client.renderer.culling.Frustrum;
import net.minecraft.client.renderer.culling.ICamera;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.init.Blocks;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

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

    @Inject(method="Lnet/minecraft/client/renderer/RenderGlobal;setWorldAndLoadRenderers(Lnet/minecraft/client/multiplayer/WorldClient;)V", at=@At("HEAD"))
    private void sodium$setWorldAndLoadRenderers(WorldClient world, CallbackInfo ci) {
        RenderDevice.enterManagedCode();
        try {
            this.renderer.setWorld(world);
        } finally {
            RenderDevice.exitManagedCode();
        }
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
        return true;
    }

    /**
     * @author Sodium
     * @reason Redirect to our renderer
     */
    @Overwrite
    public int sortAndRender(EntityLivingBase entity, int pass, double partialTicks) {
        // Roughly equivalent to `renderLayer`
        RenderDevice.enterManagedCode();

        final double x = entity.lastTickPosX + (entity.posX - entity.lastTickPosX) * partialTicks;
        final double y = entity.lastTickPosY + (entity.posY - entity.lastTickPosY) * partialTicks;
        final double z = entity.lastTickPosZ + (entity.posZ - entity.lastTickPosZ) * partialTicks;


        try {
            MatrixStack matrixStack = new MatrixStack();
            final List<RenderLayer> renderLayers = switch(pass) {
                case 0 -> ImmutableList.of(RenderLayer.solid(), RenderLayer.cutoutMipped(), RenderLayer.cutout());
                case 1 -> ImmutableList.of(RenderLayer.translucent());
                default -> throw new IllegalStateException("Unexpected value: " + pass);
            };
            for (RenderLayer renderLayer : renderLayers) {
                this.renderer.drawChunkLayer(renderLayer, matrixStack, x, y, z);
            }
        } finally {
            RenderDevice.exitManagedCode();
        }
        return 0;
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
        boolean spectator = false;
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

}
