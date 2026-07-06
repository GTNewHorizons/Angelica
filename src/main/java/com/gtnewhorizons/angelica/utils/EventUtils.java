package com.gtnewhorizons.angelica.utils;

import cpw.mods.fml.common.eventhandler.Event;
import cpw.mods.fml.common.eventhandler.EventPriority;
import cpw.mods.fml.relauncher.ReflectionHelper;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;

public class EventUtils {
    private static final MethodHandle EVENT_PHASE;

    static {
        try {
            EVENT_PHASE = MethodHandles.publicLookup().unreflectSetter(ReflectionHelper.findField(Event.class, "phase"));
        } catch (IllegalAccessException e) {
            throw new RuntimeException(e);
        }
    }

    public static void clearPhase(Event e) {
        try {
            EVENT_PHASE.invokeExact(e, (EventPriority)null);
        } catch(Throwable ex) {
            throw new RuntimeException(ex);
        }
    }
}
