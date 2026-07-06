package com.gtnewhorizons.angelica.event;

import net.minecraftforge.eventbus.api.bus.EventBus;
import net.minecraftforge.eventbus.api.event.MutableEvent;

public final class ChunkBiomeDataChangedEvent extends MutableEvent {

    public static final EventBus<ChunkBiomeDataChangedEvent> BUS = EventBus.create(ChunkBiomeDataChangedEvent.class);

    private static final ChunkBiomeDataChangedEvent INSTANCE = new ChunkBiomeDataChangedEvent();

    public int chunkX;
    public int chunkZ;

    public static void post(int chunkX, int chunkZ) {
        if (!BUS.hasListeners()) return;
        INSTANCE.chunkX = chunkX;
        INSTANCE.chunkZ = chunkZ;
        BUS.post(INSTANCE);
    }
}
