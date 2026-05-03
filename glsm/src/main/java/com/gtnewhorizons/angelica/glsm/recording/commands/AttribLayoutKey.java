package com.gtnewhorizons.angelica.glsm.recording.commands;

import com.gtnewhorizons.angelica.glsm.states.VertexAttribState;

import java.util.Arrays;

public final class AttribLayoutKey {
    final int[] locations;
    final int[] sizes;
    final int[] types;
    final boolean[] normalized;
    private final int[] offsets;
    private final int stride;
    private final int hash;

    public AttribLayoutKey(int[] locations, int[] sizes, int[] types, boolean[] normalized) {
        this.locations = locations;
        this.sizes = sizes;
        this.types = types;
        this.normalized = normalized;

        final int n = locations.length;
        final int[] off = new int[n];
        int base = 0;
        int maxAlign = 1;
        for (int i = 0; i < n; i++) {
            final int typeSize = VertexAttribState.Attrib.glTypeSizeBytes(types[i]);
            if (typeSize > maxAlign) maxAlign = typeSize;
            base = (base + typeSize - 1) & -typeSize;
            off[i] = base;
            base += sizes[i] * typeSize;
        }
        this.offsets = off;
        this.stride = (base + maxAlign - 1) & -maxAlign;

        int h = Arrays.hashCode(locations);
        h = 31 * h + Arrays.hashCode(sizes);
        h = 31 * h + Arrays.hashCode(types);
        h = 31 * h + Arrays.hashCode(normalized);
        this.hash = h;
    }

    public int offset(int i) {
        return offsets[i];
    }

    public int stride() {
        return stride;
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof AttribLayoutKey k)) return false;
        return Arrays.equals(locations, k.locations)
            && Arrays.equals(sizes, k.sizes)
            && Arrays.equals(types, k.types)
            && Arrays.equals(normalized, k.normalized);
    }

    @Override
    public int hashCode() {
        return hash;
    }
}
