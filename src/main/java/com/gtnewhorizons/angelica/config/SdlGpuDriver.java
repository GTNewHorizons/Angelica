package com.gtnewhorizons.angelica.config;

import me.jellysquid.mods.sodium.client.gui.options.named.NamedState;

public enum SdlGpuDriver implements NamedState {
    AUTO  ("",           "options.angelica.renderer.driver.auto"),
    VULKAN("vulkan",     "options.angelica.renderer.driver.vulkan"),
    METAL ("metal",      "options.angelica.renderer.driver.metal"),
    D3D12 ("direct3d12", "options.angelica.renderer.driver.d3d12");

    private final String sdlName;
    private final String key;

    SdlGpuDriver(String sdlName, String key) {
        this.sdlName = sdlName;
        this.key = key;
    }

    @Override
    public String getKey() {
        return key;
    }

    public String sdlName() { return sdlName; }

    public static SdlGpuDriver fromSdlName(String sdlName) {
        for (final SdlGpuDriver driver : values()) {
            if (driver.sdlName.equalsIgnoreCase(sdlName)) return driver;
        }
        return null;
    }
}
