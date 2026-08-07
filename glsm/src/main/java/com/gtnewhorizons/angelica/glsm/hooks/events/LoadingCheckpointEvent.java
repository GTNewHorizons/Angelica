package com.gtnewhorizons.angelica.glsm.hooks.events;

import net.minecraftforge.eventbus.api.event.MutableEvent;

/** Fired at loading-phase boundaries */
public final class LoadingCheckpointEvent extends MutableEvent {
    public boolean requiresSync;
}
