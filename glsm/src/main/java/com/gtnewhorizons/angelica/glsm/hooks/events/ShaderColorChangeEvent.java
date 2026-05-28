package com.gtnewhorizons.angelica.glsm.hooks.events;

import net.minecraftforge.eventbus.api.event.MutableEvent;

public final class ShaderColorChangeEvent extends MutableEvent {
    public float red;
    public float green;
    public float blue;
    public float alpha;
}
