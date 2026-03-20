package com.gtnewhorizons.angelica.glsm.hooks.events;

import net.minecraftforge.eventbus.api.event.MutableEvent;

public final class BlendFuncChangeEvent extends MutableEvent {
    public int srcRgb;
    public int dstRgb;
    public int srcAlpha;
    public int dstAlpha;
}
