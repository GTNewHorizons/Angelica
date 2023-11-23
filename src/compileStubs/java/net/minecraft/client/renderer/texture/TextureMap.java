package net.minecraft.client.renderer.texture;

import net.minecraft.client.resources.IResourceManager;
import net.minecraft.util.ResourceLocation;

import java.util.Map;

public class TextureMap extends AbstractTexture {

    public static ResourceLocation locationBlocksTexture;
    public static ResourceLocation locationItemsTexture;

    public int angelica$atlasWidth;
    public int angelica$atlasHeight;

    public void loadTextureAtlas(IResourceManager p_110571_1_) {}

    public ResourceLocation completeResourceLocation(ResourceLocation p_147634_1_, int p_147634_2_) {
        return null;
    }

    public void updateAnimations() {}
    public void setAnisotropicFiltering(int p_147632_1_) {}
    public void setMipmapLevels(int p_147633_1_) {}

    public int getTextureType() {
        return 0;
    }

    public Object getTextureEntry(String iconName) { return null; }
}
