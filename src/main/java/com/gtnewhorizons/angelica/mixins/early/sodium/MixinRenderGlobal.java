package com.gtnewhorizons.angelica.mixins.early.sodium;

import com.google.common.collect.ImmutableList;
import com.gtnewhorizons.angelica.compat.mojang.Camera;
import com.gtnewhorizons.angelica.compat.mojang.MatrixStack;
import com.gtnewhorizons.angelica.compat.mojang.RenderLayer;
import me.jellysquid.mods.sodium.client.gl.device.RenderDevice;
import me.jellysquid.mods.sodium.client.render.SodiumWorldRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.client.renderer.RenderGlobal;
import net.minecraft.client.renderer.culling.Frustrum;
import net.minecraft.client.renderer.culling.ICamera;
import net.minecraft.entity.EntityLivingBase;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

@Mixin(RenderGlobal.class)
public class MixinRenderGlobal {

    @Shadow
    public Minecraft mc;
    @Unique private SodiumWorldRenderer renderer;

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
        int frame = 0;
        boolean spectator = false;
        Camera camera = new Camera(mc.renderViewEntity, partialTicks);

        try {
            this.renderer.updateChunks(camera, frustum, hasForcedFrustum, frame, spectator);
        } finally {
            RenderDevice.exitManagedCode();
        }
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
}
