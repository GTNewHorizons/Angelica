package com.gtnewhorizons.angelica.glsm.streaming;

import com.gtnewhorizon.gtnhlib.client.renderer.vertex.VertexFlags;

public final class StreamingVaos {
    private static final int FORMAT_COUNT = VertexFlags.BITSET_SIZE;

    static final int MID_TEX_AND_TANGENT = 1;
    static final int ENTITY_IDS = 2;
    private static final int EXTENDED_LAYOUTS = MID_TEX_AND_TANGENT | ENTITY_IDS;

    final int[] persistentVAOs = new int[FORMAT_COUNT];
    final int[] orphanVAOs = new int[FORMAT_COUNT];

    // An extended draw always carries at least one layout, so row 0 is layout 1
    private final int[][] extendedPersistentVAOs = new int[EXTENDED_LAYOUTS][FORMAT_COUNT];
    private final int[][] extendedOrphanVAOs = new int[EXTENDED_LAYOUTS][FORMAT_COUNT];

    final int[] lineQuadPersistentVAOs = new int[FORMAT_COUNT];
    final int[] lineQuadOrphanVAOs = new int[FORMAT_COUNT];

    int[] extendedPersistent(int layout) {
        return extendedPersistentVAOs[layout - 1];
    }

    int[] extendedOrphan(int layout) {
        return extendedOrphanVAOs[layout - 1];
    }
}
