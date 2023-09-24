package com.gtnewhorizons.angelica.mixins.early.archaic.common.core;

import cpw.mods.fml.common.network.handshake.NetworkDispatcher;
import cpw.mods.fml.relauncher.Side;
import io.netty.channel.ChannelConfig;
import net.minecraft.network.EnumConnectionState;
import net.minecraft.network.NetworkManager;
import org.embeddedt.archaicfix.config.ArchaicConfig;
import org.embeddedt.archaicfix.interfaces.IArchaicNetworkManager;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(NetworkDispatcher.class)
public class MixinNetworkDispatcher {
    @Shadow(remap = false) @Final private Side side;

    @Redirect(method = "clientListenForServerHandshake", at = @At(value = "INVOKE", remap = true, target =
            "Lnet/minecraft/network/NetworkManager;setConnectionState(Lnet/minecraft/network/EnumConnectionState;)V"))
    public void archaic_raceConditionWorkAround1(NetworkManager self, EnumConnectionState state) {
        if(ArchaicConfig.fixLoginRaceCondition)
            ((IArchaicNetworkManager)self).setConnectionStateWithoutAutoRead(state);
        else
            self.setConnectionState(state);
    }

    @Redirect(method = "handlerAdded", at = @At(value = "INVOKE", target =
            "Lio/netty/channel/ChannelConfig;setAutoRead(Z)Lio/netty/channel/ChannelConfig;"), remap = false)
    public ChannelConfig archaic_raceConditionWorkAround2(ChannelConfig self, boolean autoRead) {
        if (ArchaicConfig.fixLoginRaceCondition && side == Side.CLIENT) {
            autoRead = false;
        }
        return self.setAutoRead(autoRead);
    }
}
