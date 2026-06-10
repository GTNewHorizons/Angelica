package com.gtnewhorizons.angelica.event;

import net.minecraftforge.eventbus.api.bus.EventBus;
import net.minecraftforge.eventbus.api.event.MutableEvent;

public class ClientEvent extends MutableEvent {
    public static final EventBus<ClientEvent> BUS = EventBus.create(ClientEvent.class);

    public ClientEventType event;
    public Object[] params;

    private ClientEvent(ClientEventType event, Object... params) {
        this.event = event;
        this.params = params;
    }

    public static void post(ClientEventType event, Object... params) {
        if (!BUS.hasListeners()) return;
        BUS.post(new ClientEvent(event, params));
    }
}

