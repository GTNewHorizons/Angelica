package com.gtnewhorizons.angelica.mixins.early.celeritas.core.terrain;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.client.renderer.DestroyBlockProgress;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.RenderGlobal;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.client.renderer.culling.ICamera;
import net.minecraft.entity.EntityLivingBase;
import org.embeddedt.embeddium.impl.gl.device.RenderDevice;
import org.embeddedt.embeddium.impl.render.viewport.ViewportProvider;
import org.lwjgl.opengl.GL11;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.taumc.celeritas.impl.extensions.RenderGlobalExtension;
import org.taumc.celeritas.impl.render.terrain.CameraHelper;
import org.taumc.celeritas.impl.render.terrain.CeleritasWorldRenderer;

import java.util.Map;

@Mixin(value = RenderGlobal.class, priority = 900)
public abstract class RenderGlobalMixin implements RenderGlobalExtension {

    @Shadow
    @Final
    private Map<Integer, DestroyBlockProgress> damagedBlocks;

    @Shadow @Final private Minecraft mc;

    private CeleritasWorldRenderer renderer;

    @Redirect(method = "loadRenderers", at = @At(opcode = Opcodes.GETFIELD, value = "FIELD", target = "Lnet/minecraft/client/renderer/RenderGlobal;renderDistanceChunks:I", ordinal = 0))
    private int nullifyBuiltChunkStorage(RenderGlobal self) {
        // Do not allow any resources to be allocated
        return 0;
    }

    @Inject(method = "<init>", at = @At("RETURN"))
    private void init(Minecraft minecraft, CallbackInfo ci) {
        this.renderer = new CeleritasWorldRenderer(minecraft);
    }

    @Override
    public CeleritasWorldRenderer sodium$getWorldRenderer() {
        return this.renderer;
    }

    @Inject(method = "setWorldAndLoadRenderers", at = @At("RETURN"))
    private void onWorldChanged(WorldClient world, CallbackInfo ci) {
        RenderDevice.enterManagedCode();

        try {
            this.renderer.setWorld(world);
        } finally {
            RenderDevice.exitManagedCode();
        }
    }

    @Inject(method = "onStaticEntitiesChanged", at = @At("RETURN"))
    private void onTerrainUpdateScheduled(CallbackInfo ci) {
        this.renderer.scheduleTerrainUpdate();
    }

    /**
     * @reason Redirect the chunk layer render passes to our renderer
     * @author JellySquid
     */
    @Overwrite
    public int sortAndRender(EntityLivingBase entityIn, int pass, double partialTicks) {
        // Allow FalseTweaks mixin to replace constant
        @SuppressWarnings("unused")
        double magicSortingConstantValue = 1.0D;
        RenderDevice.enterManagedCode();

        RenderHelper.disableStandardItemLighting();

        OpenGlHelper.setActiveTexture(OpenGlHelper.defaultTexUnit);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, this.mc.getTextureMapBlocks().getGlTextureId());
        GL11.glEnable(GL11.GL_TEXTURE_2D);

        this.mc.entityRenderer.enableLightmap(partialTicks);

        var camPosition = CameraHelper.getCurrentCameraPosition(partialTicks);

        try {
            this.renderer.drawChunkLayer(pass, camPosition.x, camPosition.y, camPosition.z);
        } finally {
            RenderDevice.exitManagedCode();
        }

        this.mc.entityRenderer.disableLightmap(partialTicks);

        return 1;
    }

    @Unique
    private int frame = 0;

    /**
     * @reason Redirect the terrain setup phase to our renderer
     * @author JellySquid
     */
    @Overwrite
    public void clipRenderersByFrustum(ICamera camera, float tick) {
        RenderDevice.enterManagedCode();

        try {
            this.renderer.setupTerrain(((ViewportProvider)camera).sodium$createViewport(), tick, this.frame++, this.mc.thePlayer.noClip, false);
        } finally {
            RenderDevice.exitManagedCode();
        }
    }

    /**
     * @reason Redirect chunk updates to our renderer
     * @author JellySquid
     */
    @Overwrite
    public void markBlocksForUpdate(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
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

    @Inject(method = "renderEntities", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/RenderHelper;enableStandardItemLighting()V", shift = At.Shift.AFTER, ordinal = 0), cancellable = true)
    public void sodium$renderTileEntities(EntityLivingBase p_147589_1_, ICamera p_147589_2_, float partialTicks, CallbackInfo ci) {
        this.renderer.renderBlockEntities(partialTicks, damagedBlocks);

        this.mc.entityRenderer.disableLightmap(partialTicks);
        this.mc.mcProfiler.endSection();
        ci.cancel();
    }

    /**
     * @reason Replace the debug string
     * @author JellySquid
     */
    @Overwrite
    public String getDebugInfoRenders() {
        return this.renderer.getChunksDebugString();
    }
}

