package org.embeddedt.archaicfix.mixins.common.core;

import io.netty.channel.Channel;
import io.netty.util.AttributeKey;
import net.minecraft.network.EnumConnectionState;
import net.minecraft.network.NetworkManager;
import org.embeddedt.archaicfix.ducks.IArchaicNetworkManager;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(NetworkManager.class)
public class MixinNetworkManager implements IArchaicNetworkManager {

    @Shadow private EnumConnectionState connectionState;

    @Shadow private Channel channel;

    @Shadow @Final private boolean isClientSide;

    @Override
    public void setConnectionStateWithoutAutoRead(EnumConnectionState state) {
        this.connectionState = (EnumConnectionState)this.channel.attr(NetworkManager.attrKeyConnectionState).getAndSet(state);
        this.channel.attr(NetworkManager.attrKeyReceivable).set(state.func_150757_a(this.isClientSide));
        this.channel.attr(NetworkManager.attrKeySendable).set(state.func_150754_b(this.isClientSide));
    }
}
