package org.embeddedt.archaicfix.threadedupdates.api;

import net.minecraft.client.renderer.Tessellator;
import org.embeddedt.archaicfix.threadedupdates.ThreadedChunkUpdateHelper;

public class ThreadedChunkUpdates {

    public static boolean isEnabled() {
        return ThreadedChunkUpdateHelper.instance != null;
    }

    /** Returns the thread-local tessellator instance. Can only be called after init phase. */
    public static Tessellator getThreadTessellator() {
        return ThreadedChunkUpdateHelper.instance.getThreadTessellator();
    }

}
