package com.gtnewhorizons.angelica.rendering.tesr;

import net.minecraft.tileentity.TileEntity;

/**
 * Lets a block entity renderer keep geometry pending across consecutive block entities of its own kind during the
 * block entity pass.
 */
public final class PendingTesrDraws {

    private static Pending pending;
    private static boolean inPass;

    private PendingTesrDraws() {
    }

    public static boolean canDefer() {
        return inPass;
    }

    public static void hold(Pending owner) {
        if (pending != owner) flush();
        pending = owner;
    }

    public static void beforeTileEntity(TileEntity next) {
        if (pending != null && !pending.canWaitFor(next)) flush();
    }

    public static void beginPass() {
        inPass = true;
    }

    public static void endPass() {
        inPass = false;
        flush();
    }

    public static void flush() {
        final Pending owner = pending;
        if (owner == null) return;
        pending = null;
        owner.flush();
    }

    public interface Pending {
        boolean canWaitFor(TileEntity next);

        void flush();
    }
}
