package com.gtnewhorizons.angelica.event;

import net.minecraftforge.eventbus.api.bus.CancellableEventBus;
import net.minecraftforge.eventbus.api.event.RecordEvent;
import net.minecraftforge.eventbus.api.event.characteristic.Cancellable;

/**
 * Posted from {@link net.coderbot.iris.pipeline.HandRenderer}'s {@code canRender}
 * <p>
 * Cancel the event to prevent the hand from rendering.
 */
public record RenderHandEvent() implements RecordEvent, Cancellable {

    public static final CancellableEventBus<RenderHandEvent> BUS = CancellableEventBus.create(RenderHandEvent.class);

    private static final RenderHandEvent INSTANCE = new RenderHandEvent();

    /**
     * @return true if a listener cancelled the event, meaning the hand should NOT render.
     */
    public static boolean post() {
        if (!BUS.hasListeners()) return false;
        return BUS.post(INSTANCE);
    }
}
