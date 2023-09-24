package com.gtnewhorizons.angelica.mixins.early.archaic.client.threadedupdates;

import net.minecraft.block.Block;
import net.minecraft.client.renderer.RenderBlocks;
import net.minecraft.client.renderer.Tessellator;
import net.minecraftforge.client.ForgeHooksClient;
import org.embeddedt.archaicfix.threadedupdates.IRendererUpdateResultHolder;
import org.embeddedt.archaicfix.threadedupdates.ThreadedChunkUpdateHelper;
import org.embeddedt.archaicfix.threadedupdates.api.ThreadedChunkUpdates;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.spongepowered.asm.mixin.injection.callback.LocalCapture;

@Mixin(RenderBlocks.class)
public class MixinRenderBlocks {

    @Inject(method = "renderBlockByRenderType", at = @At(value = "INVOKE", target = "Lnet/minecraft/block/Block;setBlockBoundsBasedOnState(Lnet/minecraft/world/IBlockAccess;III)V"), cancellable = true, locals = LocalCapture.CAPTURE_FAILHARD)
    private void cancelRenderDelegatedToDifferentThread(Block block, int x, int y, int z, CallbackInfoReturnable<Boolean> cir, int renderType) {
        int pass = ForgeHooksClient.getWorldRenderPass();
        boolean mainThread = Thread.currentThread() == ThreadedChunkUpdateHelper.MAIN_THREAD;

        ThreadedChunkUpdateHelper.UpdateTask task = mainThread
                ? ((IRendererUpdateResultHolder)ThreadedChunkUpdateHelper.lastWorldRenderer).arch$getRendererUpdateTask()
                : null;

        boolean offThreadBlock = ThreadedChunkUpdateHelper.canBlockBeRenderedOffThread(block, pass, renderType)
                && !(task != null && task.cancelled)
                && (!mainThread || ThreadedChunkUpdateHelper.renderBlocksStack.getLevel() == 1);
        if ((mainThread ? pass >= 0 : true) && (mainThread ? offThreadBlock : !offThreadBlock)) {
            // Cancel rendering block if it's delegated to a different thread.
            cir.setReturnValue(mainThread ? task.result[pass].renderedSomething : false);
        }
    }

    @Inject(method = "renderBlockByRenderType", at = @At("HEAD"))
    private void pushStack(CallbackInfoReturnable<Boolean> cir) {
        ThreadedChunkUpdateHelper.renderBlocksStack.push();
    }

    @Inject(method = "renderBlockByRenderType", at = @At("RETURN"))
    private void popStack(CallbackInfoReturnable<Boolean> cir) {
        ThreadedChunkUpdateHelper.renderBlocksStack.pop();
    }

    @Redirect(method = "*", at = @At(value = "FIELD", target = "Lnet/minecraft/client/renderer/Tessellator;instance:Lnet/minecraft/client/renderer/Tessellator;"))
    private Tessellator modifyTessellatorAccess() {
        return ThreadedChunkUpdates.getThreadTessellator();
    }

}
