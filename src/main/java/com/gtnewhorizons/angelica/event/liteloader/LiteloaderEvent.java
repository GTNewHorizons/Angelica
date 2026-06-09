package com.gtnewhorizons.angelica.event.liteloader;

import net.minecraftforge.eventbus.api.bus.EventBus;
import net.minecraftforge.eventbus.api.event.MutableEvent;

public class LiteloaderEvent extends MutableEvent {
    public static final EventBus<LiteloaderEvent> BUS = EventBus.create(LiteloaderEvent.class);

    public LiteloaderEventType event;
    public Object[] params;

    private LiteloaderEvent(LiteloaderEventType event, Object... params) {
        this.event = event;
        this.params = params;
    }

    public static void post(LiteloaderEventType event, Object... params) {
        if (!BUS.hasListeners()) return;
        BUS.post(new LiteloaderEvent(event, params));
    }
}

