package com.gtnewhorizons.angelica.glsm.states;

import com.gtnewhorizons.angelica.glsm.RenderSystem;

public class SamplerUnitArray {

    private static final int MIN_UNITS = 16;

    private int[] samplers;

    public int get(int unit) {
        final int[] local = samplers;
        return local != null && unit >= 0 && unit < local.length ? local[unit] : 0;
    }

    public boolean set(int unit, int sampler) {
        if (unit < 0) return false;
        int[] local = samplers;
        if (local == null) {
            local = new int[Math.max(MIN_UNITS, RenderSystem.getMaxCombinedTextureImageUnits())];
            samplers = local;
        }
        if (unit >= local.length) return false;
        local[unit] = sampler;
        return true;
    }

    public void unbindEverywhere(int sampler) {
        final int[] local = samplers;
        if (local == null) return;
        for (int i = 0; i < local.length; i++) {
            if (local[i] == sampler) local[i] = 0;
        }
    }

    public int size() {
        final int[] local = samplers;
        return local == null ? 0 : local.length;
    }
}
