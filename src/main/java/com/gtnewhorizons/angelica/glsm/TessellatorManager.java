package com.gtnewhorizons.angelica.glsm;

import net.minecraft.client.renderer.Tessellator;

public class TessellatorManager {
    private static final ThreadLocal<Tessellator> theTessellator = ThreadLocal.withInitial(Tessellator::new);
    private static final Thread mainThread = Thread.currentThread();

    public static Tessellator get() {
        if(isOnMainThread())
            return Tessellator.instance;
        return theTessellator.get();
    }

    public static boolean isOnMainThread() {
        return Thread.currentThread() == mainThread;
    }

    public static boolean isMainInstance(Object instance) {
        return instance == Tessellator.instance;
    }

    static {
        System.out.println("[TessellatorManager] Initialized on thread " + mainThread.getName());
    }
}
