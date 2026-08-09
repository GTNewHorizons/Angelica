package com.gtnewhorizons.angelica.config;

import me.jellysquid.mods.sodium.client.gui.options.named.NamedState;

public enum GpuCullingMode implements NamedState {
    CPU_ONLY(false, "options.angelica.gpuCullingMode.cpu_only"),
    COMPUTE (true,  "options.angelica.gpuCullingMode.compute");

    private final boolean compute;
    private final String key;

    GpuCullingMode(boolean compute, String key) {
        this.compute = compute;
        this.key = key;
    }

    @Override
    public String getKey() {
        return key;
    }

    public boolean computeEnabled() { return compute; }
}
