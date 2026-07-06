package com.gtnewhorizons.angelica.rendering.celeritas;

public enum BlockRenderLayer {
    SOLID,          // pass 0, no alpha test
    CUTOUT,         // pass 0, alpha test (not mipped)
    CUTOUT_MIPPED,  // pass 0, alpha test (mipped)
    TRANSLUCENT;    // pass 1

    public int toVanillaPass() {
        return this == TRANSLUCENT ? 1 : 0;
    }

    public static BlockRenderLayer fromVanillaPass(int pass) {
        // CUTOUT_MIPPED is the safe default for pass 0
        // SOLID/CUTOUT are only used for explicit shader pack and material overrides
        return pass == 0 ? CUTOUT_MIPPED : TRANSLUCENT;
    }
}
