package com.gtnewhorizons.angelica.config;

import me.jellysquid.mods.sodium.client.gui.options.named.NamedState;

public enum RenderBackendChoice implements NamedState {
    OPENGL ("options.angelica.renderer.backend.opengl"),
    SDL_GPU("options.angelica.renderer.backend.sdl_gpu");

    private final String key;

    RenderBackendChoice(String key) {
        this.key = key;
    }

    @Override
    public String getKey() {
        return key;
    }
}
