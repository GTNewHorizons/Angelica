package net.coderbot.iris.texture.pbr.loader;

import net.minecraft.util.ResourceLocation;

public class TextureAtlasSpriteInfo {
    private final ResourceLocation name;
    private final int width;
    private final int height;

    public TextureAtlasSpriteInfo(ResourceLocation arg, int i, int j) {
        this.name = arg;
        this.width = i;
        this.height = j;
    }

    public ResourceLocation name() {
        return this.name;
    }

    public int width() {
        return this.width;
    }

    public int height() {
        return this.height;
    }
}
