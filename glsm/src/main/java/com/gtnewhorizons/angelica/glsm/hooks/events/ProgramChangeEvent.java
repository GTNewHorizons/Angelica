package com.gtnewhorizons.angelica.glsm.hooks.events;

import net.minecraftforge.eventbus.api.event.MutableEvent;

public final class ProgramChangeEvent extends MutableEvent {
    public int previousProgram;
    public int newProgram;
}
