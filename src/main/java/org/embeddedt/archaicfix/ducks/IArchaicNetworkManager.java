package org.embeddedt.archaicfix.ducks;

import net.minecraft.network.EnumConnectionState;

public interface IArchaicNetworkManager {
    void setConnectionStateWithoutAutoRead(EnumConnectionState state);
}
