package com.gtnewhorizons.angelica.mixins.early.archaic.common.lighting;

import net.minecraft.util.IProgressUpdate;
import net.minecraft.world.WorldServer;
import net.minecraft.world.gen.ChunkProviderServer;
import org.embeddedt.archaicfix.lighting.api.ILightingEngineProvider;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Set;

@Mixin(ChunkProviderServer.class)
public abstract class MixinChunkProviderServer {
    @Shadow private Set chunksToUnload;

    @Shadow public WorldServer worldObj;

    /**
     * Injects a callback into the start of saveChunks(boolean) to force all light updates to be processed before saving.
     *
     * @author Angeline
     */
    @Inject(method = "saveChunks", at = @At("HEAD"))
    private void onSaveChunks(boolean all, IProgressUpdate update, CallbackInfoReturnable<Boolean> cir) {
        ((ILightingEngineProvider) this.worldObj).getLightingEngine().processLightUpdates();
    }

    /**
     * Injects a callback into the start of the onTick() method to process all pending light updates. This is not necessarily
     * required, but we don't want our work queues getting too large.
     *
     * @author Angeline
     */
    @Inject(method = "unloadQueuedChunks", at = @At("HEAD"))
    private void onTick(CallbackInfoReturnable<Boolean> cir) {
        if (!this.worldObj.levelSaving) {
            if (!this.chunksToUnload.isEmpty()) {
                ((ILightingEngineProvider) this.worldObj).getLightingEngine().processLightUpdates();
            }
        }
    }
}
