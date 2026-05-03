package com.gtnewhorizons.angelica.glsm.hooks.events;

import net.minecraftforge.eventbus.api.event.MutableEvent;

public final class TextureUnitStateEvent extends MutableEvent {
    public int unit;
    public boolean enabled;
}
