package com.gtnewhorizons.angelica.glsm.streaming;

import com.gtnewhorizon.gtnhlib.client.renderer.vertex.VertexFlags;

public final class StreamingVaos {
    private static final int FORMAT_COUNT = VertexFlags.BITSET_SIZE;

    final int[] persistentVAOs = new int[FORMAT_COUNT];
    final int[] orphanVAOs = new int[FORMAT_COUNT];

    final int[] extendedPersistentVAOs = new int[FORMAT_COUNT];
    final int[] extendedOrphanVAOs = new int[FORMAT_COUNT];
}
