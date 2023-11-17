package com.gtnewhorizons.angelica.mixins.early.sodium;

import com.gtnewhorizons.angelica.mixins.interfaces.IWorldClientExt;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.client.network.NetHandlerPlayClient;
import net.minecraft.network.play.server.S21PacketChunkData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(NetHandlerPlayClient.class)
public class MixinNetHandlerPlayClient {
    @Shadow private WorldClient clientWorldController;

    @Inject(method = "handleChunkData", at= @At(value="INVOKE", target="Lnet/minecraft/client/multiplayer/WorldClient;markBlockRangeForRenderUpdate(IIIIII)V", shift = At.Shift.AFTER))
    private void sodium$afterChunkData(S21PacketChunkData packetIn, CallbackInfo ci) {
        ((IWorldClientExt)this.clientWorldController).doPostChunk(packetIn.func_149273_e(), packetIn.func_149271_f());
    }

    @Inject(method = "handleMapChunkBulk", at= @At(value="INVOKE", target="Lnet/minecraft/client/multiplayer/WorldClient;markBlockRangeForRenderUpdate(IIIIII)V", shift = At.Shift.AFTER))
    private void sodium$afterMapChunkBulk(CallbackInfo ci, @Local(name = "j") int x, @Local(name = "k") int z) {
        ((IWorldClientExt)this.clientWorldController).doPostChunk(x, z);
    }
}
