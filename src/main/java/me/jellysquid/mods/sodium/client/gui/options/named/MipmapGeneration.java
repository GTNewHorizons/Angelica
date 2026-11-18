package me.jellysquid.mods.sodium.client.gui.options.named;

import com.gtnewhorizons.angelica.proxy.ClientProxy;
import org.embeddedt.embeddium.impl.render.chunk.terrain.material.parameters.AlphaCutoffParameter;

public enum MipmapGeneration implements NamedState {
    VANILLA_1_7_10("sodium.options.mipmap_generation.vanilla_1_7_10"),
    CELERITAS("sodium.options.mipmap_generation.celeritas"),
    MODERN_VANILLA("sodium.options.mipmap_generation.modern_vanilla");

    private final String name;

    MipmapGeneration(String name) {
        this.name = name;
    }

    public static MipmapGeneration current() {
        return ClientProxy.options().quality.mipmapGeneration;
    }

    public static boolean usesModernMipmaps() {
        return current() == MODERN_VANILLA;
    }

    public static AlphaCutoffParameter alphaFloorCutoff() {
        return usesModernMipmaps() ? AlphaCutoffParameter.ONE_TENTH : AlphaCutoffParameter.ZERO;
    }

    @Override
    public String getKey() {
        return this.name;
    }
}
