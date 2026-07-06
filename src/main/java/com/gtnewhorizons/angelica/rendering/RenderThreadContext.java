package com.gtnewhorizons.angelica.rendering;

import com.gtnewhorizons.angelica.rendering.celeritas.world.WorldSlice;
import net.minecraft.tileentity.TileEntity;

public class RenderThreadContext {

    private static final ThreadLocal<WorldSlice> currentWorldSlice = new ThreadLocal<>();

    public static void set(WorldSlice slice) {
        currentWorldSlice.set(slice);
    }

    public static void clear() {
        currentWorldSlice.remove();
    }

    public static TileEntity getSnapshotTE(int x, int y, int z) {
        final WorldSlice slice = currentWorldSlice.get();
        if (slice == null) return null;
        return slice.getTileEntity(x, y, z);
    }

    public static boolean hasWorldSlice() {
        return currentWorldSlice.get() != null;
    }
}
