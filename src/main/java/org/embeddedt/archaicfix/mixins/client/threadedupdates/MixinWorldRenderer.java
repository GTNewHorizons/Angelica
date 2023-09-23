package org.embeddedt.archaicfix.mixins.client.threadedupdates;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderBlocks;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.WorldRenderer;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.world.ChunkCache;
import org.embeddedt.archaicfix.threadedupdates.ThreadedChunkUpdateHelper;
import org.embeddedt.archaicfix.threadedupdates.ICapturableTessellator;
import org.embeddedt.archaicfix.threadedupdates.IRendererUpdateResultHolder;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.LocalCapture;

import java.util.HashSet;

@Mixin(WorldRenderer.class)
public class MixinWorldRenderer implements IRendererUpdateResultHolder {

    private ThreadedChunkUpdateHelper.UpdateTask arch$updateTask;

    @Inject(method = "updateRenderer", at = @At("HEAD"))
    private void setLastWorldRendererSingleton(CallbackInfo ci) {
        ThreadedChunkUpdateHelper.lastWorldRenderer = ((WorldRenderer)(Object)this);
    }

    @Inject(method = "updateRenderer", at = @At(value="INVOKE", target = "Lnet/minecraft/client/renderer/WorldRenderer;postRenderBlocks(ILnet/minecraft/entity/EntityLivingBase;)V"), locals=LocalCapture.CAPTURE_FAILHARD)
    private void loadTessellationResult(EntityLivingBase cameraEntity, CallbackInfo ci, int i, int j, int k, int l, int i1, int j1, HashSet hashset, Minecraft minecraft, EntityLivingBase entitylivingbase1, int l1, int i2, int j2, byte b0, ChunkCache chunkcache, RenderBlocks renderblocks, int k2) {
        int pass = k2;
        if(!arch$getRendererUpdateTask().cancelled) {
            ((ICapturableTessellator) Tessellator.instance).arch$addTessellatorVertexState(arch$getRendererUpdateTask().result[pass].renderedQuads);
        }
    }

    @Inject(method = "updateRenderer", at = @At(value="INVOKE", target = "Lnet/minecraft/client/renderer/RenderBlocks;renderBlockByRenderType(Lnet/minecraft/block/Block;III)Z"))
    private void resetStack(CallbackInfo ci) {
        // Make sure the stack doesn't leak
        ThreadedChunkUpdateHelper.renderBlocksStack.reset();
    }

    @Override
    public ThreadedChunkUpdateHelper.UpdateTask arch$getRendererUpdateTask() {
        if(arch$updateTask == null) {
            arch$updateTask = new ThreadedChunkUpdateHelper.UpdateTask();
        }
        return arch$updateTask;
    }

    @Inject(method = "markDirty", at = @At("RETURN"))
    private void notifyDirty(CallbackInfo ci) {
        ThreadedChunkUpdateHelper.instance.onWorldRendererDirty((WorldRenderer)(Object)this);
    }

}
