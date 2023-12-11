package net.coderbot.iris.texture.pbr.loader;

import net.minecraft.client.resources.data.AnimationMetadataSection;
import net.minecraft.util.ResourceLocation;

public class TextureAtlasSpriteInfo {
    private final ResourceLocation name;
    private final int width;
    private final int height;
    private final AnimationMetadataSection metadata;

    public TextureAtlasSpriteInfo(ResourceLocation arg, int i, int j, AnimationMetadataSection arg2) {
        this.name = arg;
        this.width = i;
        this.height = j;
        this.metadata = arg2;
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
