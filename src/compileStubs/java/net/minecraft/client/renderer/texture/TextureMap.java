package net.minecraft.client.renderer.texture;

import net.minecraft.util.ResourceLocation;

public class TextureMap extends AbstractTexture {

    public static ResourceLocation locationBlocksTexture;
    public int atlasWidth;
    public int atlasHeight;

    public int getTextureType()
    {
        return 0;
    }

    public ResourceLocation completeResourceLocation(ResourceLocation resourceLocation, int i) {
        return null;
    }

}
