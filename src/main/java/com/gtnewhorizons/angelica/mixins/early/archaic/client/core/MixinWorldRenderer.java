package com.gtnewhorizons.angelica.mixins.early.archaic.client.core;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.WorldRenderer;
import net.minecraft.entity.Entity;
import org.embeddedt.archaicfix.interfaces.IWorldRenderer;
import org.lwjgl.opengl.GL11;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Arrays;

/* MC-129 */
@Mixin(WorldRenderer.class)
public abstract class MixinWorldRenderer implements IWorldRenderer {
    @Shadow private boolean isInitialized;
    @Shadow public boolean needsUpdate;

    @Shadow public abstract float distanceToEntitySquared(Entity p_78912_1_);

    @Shadow public boolean[] skipRenderPass;

    @Shadow private int glRenderList;

    @Shadow private int bytesDrawn;

    public boolean arch$isInView() {
        if(Minecraft.getMinecraft().renderViewEntity == null)
            return true;
        float distance = this.distanceToEntitySquared(Minecraft.getMinecraft().renderViewEntity);
        int renderDistanceBlocks = (Minecraft.getMinecraft().gameSettings.renderDistanceChunks) * 16;
        return distance <= (renderDistanceBlocks * renderDistanceBlocks);
    }

    /**
     * Make sure chunks re-render immediately (MC-129).
     */
    @Inject(method = "markDirty", at = @At("TAIL"))
    private void forceRender(CallbackInfo ci) {
        Arrays.fill(this.skipRenderPass, false);
    }

    /**
     * When switching worlds/dimensions, clear out the old render lists for old chunks. This prevents old dimension
     * content from being visible in the new world.
     */
    @Inject(method = "setDontDraw", at = @At("TAIL"))
    private void clearOldRenderList(CallbackInfo ci) {
        if(this.glRenderList == -1 || this.bytesDrawn <= 0)
            return;
        for(int pass = 0; pass < 2; pass++) {
            GL11.glNewList(this.glRenderList + pass, GL11.GL_COMPILE);
            GL11.glEndList();
        }
    }
}
