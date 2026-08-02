package com.gtnewhorizons.angelica.event;

import net.minecraftforge.eventbus.api.bus.EventBus;
import net.minecraftforge.eventbus.api.event.RecordEvent;

public class RenderChunkEvent implements RecordEvent {
    public static final EventBus<RenderChunkEvent> BUS = EventBus.create(RenderChunkEvent.class);

    private static final RenderChunkEvent INSTANCE = new RenderChunkEvent();

    public static void post() {
        if (!BUS.hasListeners()) return;
        BUS.post(INSTANCE);
    }
}
