package com.gtnewhorizons.angelica.rendering;

import com.gtnewhorizon.gtnhlib.client.renderer.TessellatorManager;
import com.gtnewhorizons.angelica.rendering.celeritas.world.WorldSlice;

public class RenderThreadContext {

    private static final ThreadLocal<WorldSlice> currentWorldSlice = new ThreadLocal<>();

    public static void set(WorldSlice slice) {
        currentWorldSlice.set(slice);
    }

    public static void clear() {
        currentWorldSlice.remove();
    }

    public static WorldSlice get() {
        return currentWorldSlice.get();
    }

    public static WorldSlice workerSlice() {
        if (TessellatorManager.isOnMainThread()) return null;
        return currentWorldSlice.get();
    }

    public static boolean hasWorldSlice() {
        return currentWorldSlice.get() != null;
    }
}
