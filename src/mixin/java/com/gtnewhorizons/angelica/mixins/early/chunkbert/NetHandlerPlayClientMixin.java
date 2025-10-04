package com.gtnewhorizons.angelica.mixins.early.chunkbert;

import com.embeddedt.chunkbert.FakeChunkManager;
import com.embeddedt.chunkbert.ext.IChunkProviderClient;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.client.network.NetHandlerPlayClient;
import net.minecraft.network.play.server.S21PacketChunkData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(NetHandlerPlayClient.class)
public class NetHandlerPlayClientMixin {
    @Shadow private WorldClient clientWorldController;

    @Inject(method = "handleChunkData", at = @At("HEAD"))
    private void bobbyUnloadFakeChunk(S21PacketChunkData data, CallbackInfo ci) {
        FakeChunkManager bobbyChunkManager = ((IChunkProviderClient)(clientWorldController.getChunkProvider())).chunkbert$getChunkManager();
        if (bobbyChunkManager == null) {
            return;
        }

        int chunkX = data.func_149273_e();
        int chunkZ = data.func_149271_f();

        // This needs to be called unconditionally because even if there is no chunk loaded at the moment,
        // we might already have one queued which we need to cancel as otherwise it will overwrite the real one later.
        bobbyChunkManager.unload(chunkX, chunkZ, true);
    }
}
