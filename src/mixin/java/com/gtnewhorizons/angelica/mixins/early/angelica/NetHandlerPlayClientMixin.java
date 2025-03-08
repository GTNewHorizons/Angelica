package com.gtnewhorizons.angelica.mixins.early.angelica;

import com.seibel.distanthorizons.core.api.internal.ClientApi;
import com.seibel.distanthorizons.forge.ForgeServerProxy;
import net.minecraft.client.network.NetHandlerPlayClient;
import net.minecraft.network.play.server.S01PacketJoinGame;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(NetHandlerPlayClient.class)
public class NetHandlerPlayClientMixin {
    @Inject(method = "handleJoinGame", at = @At("RETURN"))
    private void connect(S01PacketJoinGame packetIn, CallbackInfo ci)
    {
        ClientApi.INSTANCE.onClientOnlyConnected();
        ForgeServerProxy.connected = true;
    }

    @Inject(method = "cleanup", at = @At("RETURN"))
    private void disconnect(CallbackInfo ci)
    {
        ForgeServerProxy.connected = false;
        ClientApi.INSTANCE.onClientOnlyDisconnected();
    }
}
