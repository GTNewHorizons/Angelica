package com.gtnewhorizons.angelica.event;

import net.minecraft.client.renderer.EntityRenderer;
import net.minecraftforge.eventbus.api.bus.EventBus;
import net.minecraftforge.eventbus.api.event.MutableEvent;

public class ClientEvent extends MutableEvent {
    public static final EventBus<ClientEvent> BUS = EventBus.create(ClientEvent.class);

    public final ClientEventType event;
    public final EntityRenderer renderer;
    public final float partialTicks;

    private ClientEvent(ClientEventType event) {
        this.event = event;
        this.renderer = null;
        this.partialTicks = 0;
    }

    private ClientEvent(ClientEventType event, EntityRenderer renderer, float partialTicks) {
        this.event = event;
        this.renderer = renderer;
        this.partialTicks = partialTicks;
    }

    public static void post(ClientEventType event, EntityRenderer renderer, float partialTicks) {
        if (!BUS.hasListeners()) return;
        BUS.post(new ClientEvent(event, renderer, partialTicks));
    }

    public static void post(ClientEventType event) {
        if (!BUS.hasListeners()) return;
        BUS.post(new ClientEvent(event));
    }
}

