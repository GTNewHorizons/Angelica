package me.jellysquid.mods.sodium.client.gui.options.named;

/**
 * Selects how the terrain fragment shader samples the block atlas.
 */
public enum TextureFilterMode implements NamedState {
    RGSS("sodium.options.texture_filtering.rgss"),
    ANISOTROPIC("sodium.options.texture_filtering.anisotropic"),
    RGSS_ANISOTROPIC("sodium.options.texture_filtering.rgss_anisotropic"),
    NONE("sodium.options.texture_filtering.none");

    private static final TextureFilterMode[] WITHOUT_ANISOTROPY = {RGSS, NONE};
    private final String name;

    TextureFilterMode(String name) {
        this.name = name;
    }

    /**
     * Anisotropic filtering rides on an extension that isn't guaranteed in GL 3.3, so the modes that use it are only
     * offered when the driver actually exposes it.
     */
    public static TextureFilterMode[] selectableValues(boolean anisotropySupported) {
        return anisotropySupported ? values() : WITHOUT_ANISOTROPY;
    }

    @Override
    public String getKey() {
        return this.name;
    }

    public boolean usesRgss() {
        return this == RGSS || this == RGSS_ANISOTROPIC;
    }

    public boolean usesAnisotropy() {
        return this == ANISOTROPIC || this == RGSS_ANISOTROPIC;
    }

}
