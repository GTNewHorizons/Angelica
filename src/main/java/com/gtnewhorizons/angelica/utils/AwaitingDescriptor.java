package com.gtnewhorizons.angelica.utils;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import net.minecraft.tileentity.TileEntity;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.gtnewhorizons.angelica.mixins.interfaces.AwaitingDescriptorTE;

public final class AwaitingDescriptor {

    private static final Logger LOGGER = LogManager.getLogger("Angelica");

    private static final int WAIT_TICKS = 100;

    private static final Map<Class<?>, Boolean> SENDS_DESCRIPTOR = new ConcurrentHashMap<>();

    private AwaitingDescriptor() {}

    public static void begin(TileEntity te) {
        if (!SENDS_DESCRIPTOR.computeIfAbsent(te.getClass(), AwaitingDescriptor::sendsDescriptor)) return;

        final AwaitingDescriptorTE tile = (AwaitingDescriptorTE) te;
        tile.angelica$setAwaitingDescriptor(true);
        tile.angelica$setDescriptorWaitTicks(WAIT_TICKS);
    }

    public static void end(AwaitingDescriptorTE tile) {
        tile.angelica$setAwaitingDescriptor(false);
        tile.angelica$setDescriptorWaitTicks(0);
    }

    public static boolean defersUpdate(TileEntity te) {
        final AwaitingDescriptorTE tile = (AwaitingDescriptorTE) te;
        final int waiting = tile.angelica$getDescriptorWaitTicks();
        if (waiting <= 0) return false;

        tile.angelica$setDescriptorWaitTicks(waiting - 1);
        if (waiting == 1) stopWaitingOn(te.getClass());
        return true;
    }

    private static boolean sendsDescriptor(Class<?> cls) {
        try {
            return cls.getMethod("getDescriptionPacket").getDeclaringClass() != TileEntity.class;
        } catch (NoSuchMethodException e) {
            return false;
        }
    }

    private static void stopWaitingOn(Class<?> cls) {
        if (!Boolean.TRUE.equals(SENDS_DESCRIPTOR.put(cls, Boolean.FALSE))) return;
        LOGGER.warn("{} declares getDescriptionPacket() but never sent one", cls.getName());
    }
}
