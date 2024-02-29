package com.gtnewhorizons.angelica.glsm;

import com.gtnewhorizons.angelica.compat.mojang.VertexBuffer;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;

public class VBOManager {
    // Not thread safe, only expected to be called from the main thread

    private static int nextDisplayList = Integer.MIN_VALUE;

    private static int getNextDisplayList() {
        return nextDisplayList++;
    }

    private static Int2ObjectMap<VertexBuffer> vertexBuffers = new Int2ObjectOpenHashMap<>();

    /*
     * Allocate a (range) of "display list IDs" that will refer to a VBO in the arraylist of vertex buffers.
     */
    public static int generateDisplayLists(int range) {
        if(range < 1) throw new IllegalArgumentException("Range must be at least 1!");

        final int id = getNextDisplayList();
        for (int i = 1; i < range; i++) {
            // Reserve, but don't use, the rest in the range
            getNextDisplayList();
        }
        // Return the first in the range
        return id;
    }

    public static VertexBuffer registerVBO(int displayList, VertexBuffer vertexBuffer) {
        if(displayList > 0) throw new IllegalArgumentException("Display list must be negative!");
        displayList -= Integer.MIN_VALUE;

        vertexBuffers.put(displayList, vertexBuffer);
        return vertexBuffer;
    }

    public static VertexBuffer get(int list) {
        list -= Integer.MIN_VALUE;
        return vertexBuffers.getOrDefault(list, null);
    }
}
