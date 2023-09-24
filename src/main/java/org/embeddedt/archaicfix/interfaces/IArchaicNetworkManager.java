package org.embeddedt.archaicfix.interfaces;

import net.minecraft.network.EnumConnectionState;

public interface IArchaicNetworkManager {
    void setConnectionStateWithoutAutoRead(EnumConnectionState state);
}
