package com.gtnewhorizons.angelica.mixins.early.archaic.common.lighting;

import net.minecraft.network.play.server.S21PacketChunkData;
import net.minecraft.world.chunk.Chunk;
import org.embeddedt.archaicfix.lighting.api.ILightingEngineProvider;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(S21PacketChunkData.class)
public abstract class MixinSPacketChunkData {
    /**
     * @author Angeline
     * Injects a callback into SPacketChunkData#calculateChunkSize(Chunk, booolean, int) to force light updates to be
     * processed before creating the client payload. We use this method rather than the constructor as it is not valid
     * to inject elsewhere other than the RETURN of a ctor, which is too late for our needs.
     */
    @Inject(method = "func_149269_a", at = @At("HEAD"))
    private static void onCalculateChunkSize(Chunk chunkIn, boolean hasSkyLight, int changedSectionFilter, CallbackInfoReturnable<S21PacketChunkData.Extracted> cir) {
        ((ILightingEngineProvider) chunkIn).getLightingEngine().processLightUpdates();
    }
}
