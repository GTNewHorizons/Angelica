package me.jellysquid.mods.sodium.client.gui.options.named;

/**
 * Terrain sampler min/mag filter.
 */
public enum TexelSampling implements NamedState {
    LINEAR("sodium.options.texel_sampling.linear"),
    NEAREST("sodium.options.texel_sampling.nearest");

    private final String name;

    TexelSampling(String name) {
        this.name = name;
    }

    @Override
    public String getKey() {
        return this.name;
    }

    public boolean isNearest() {
        return this == NEAREST;
    }
}
