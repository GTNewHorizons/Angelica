package com.gtnewhorizons.angelica.glsm.recording.commands;

import java.util.Arrays;

public final class AttribLayoutKey {
    final int[] locations;
    final int[] sizes;
    final int[] types;
    final boolean[] normalized;
    private final int hash;

    public AttribLayoutKey(int[] locations, int[] sizes, int[] types, boolean[] normalized) {
        this.locations = locations;
        this.sizes = sizes;
        this.types = types;
        this.normalized = normalized;
        int h = Arrays.hashCode(locations);
        h = 31 * h + Arrays.hashCode(sizes);
        h = 31 * h + Arrays.hashCode(types);
        h = 31 * h + Arrays.hashCode(normalized);
        this.hash = h;
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
