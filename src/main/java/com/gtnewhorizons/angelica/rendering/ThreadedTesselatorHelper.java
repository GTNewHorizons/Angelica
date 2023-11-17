package com.gtnewhorizons.angelica.rendering;

import net.minecraft.client.renderer.Tessellator;

public class ThreadedTesselatorHelper {
    public static ThreadedTesselatorHelper instance = new ThreadedTesselatorHelper();
    public static Thread MAIN_THREAD;
    public ThreadLocal<Tessellator> threadTessellator = ThreadLocal.withInitial(Tessellator::new);

    public void init() {
        MAIN_THREAD = Thread.currentThread();
    }

    public Tessellator getThreadTessellator() {
        if(Thread.currentThread() == MAIN_THREAD) {
            return Tessellator.instance;
        } else {
            return threadTessellator.get();
        }
    }

}
