package com.gtnewhorizons.angelica.glsm;

import com.gtnewhorizons.angelica.compat.mojang.VertexBuffer;

import java.util.ArrayList;

public class VBOManager {
    // Not thread safe, only expected to be called from the main thread

    private static int nextDisplayList = 0;

    private static int getNextDisplayList() {
        return nextDisplayList++;
    }

    private static ArrayList<VertexBuffer> vertexBuffers = new ArrayList<>();

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

    public static void registerVBO(int displayList, VertexBuffer vertexBuffer) {
        if(displayList > vertexBuffers.size()) {
            vertexBuffers.ensureCapacity(displayList * 2);
        }
        vertexBuffers.add(displayList, vertexBuffer);
    }

    public static VertexBuffer get(int list) {
        return vertexBuffers.get(list);
    }
}
