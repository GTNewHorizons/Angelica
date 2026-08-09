package com.gtnewhorizons.angelica.glsm.states;

import com.gtnewhorizons.angelica.glsm.RenderSystem;

public class ImageUnitArray {

    private static final int MIN_UNITS = 8;

    private volatile ImageUnitBinding[] bindings;

    public ImageUnitBinding get(int unit) {
        if (unit < 0) return null;
        ImageUnitBinding[] local = bindings;
        if (local == null) local = allocate();
        return unit < local.length ? local[unit] : null;
    }

    public int size() {
        final ImageUnitBinding[] local = bindings;
        return local == null ? 0 : local.length;
    }

    private synchronized ImageUnitBinding[] allocate() {
        ImageUnitBinding[] local = bindings;
        if (local == null) {
            final int count = Math.max(MIN_UNITS, RenderSystem.getMaxImageUnits());
            local = new ImageUnitBinding[count];
            for (int i = 0; i < count; i++) local[i] = new ImageUnitBinding();
            bindings = local;
        }
        return local;
    }
}
