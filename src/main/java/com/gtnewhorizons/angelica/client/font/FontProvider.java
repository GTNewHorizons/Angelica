package com.gtnewhorizons.angelica.client.font;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.ITextureObject;
import net.minecraft.client.renderer.texture.SimpleTexture;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.util.ResourceLocation;

public interface FontProvider {

    /**
     * For use with §k. Should fetch a character of the same width as provided.
     */
    char getRandomReplacement(char chr);
    boolean isGlyphAvailable(char chr);
    float getUStart(char chr);
    float getVStart(char chr);
    float getXAdvance(char chr);
    float getGlyphW(char chr);
    float getUSize(char chr);
    float getVSize(char chr);
    float getShadowOffset();
    int getTexture(char chr);
    float getYScaleMultiplier();

    default int getTextureFromLocation(ResourceLocation resource) {
        final TextureManager textureManager = Minecraft.getMinecraft().getTextureManager();
        if (textureManager == null) return 0;
        ITextureObject texture = textureManager.getTexture(resource);
        if (texture == null) {
            texture = new SimpleTexture(resource);
            textureManager.loadTexture(resource, texture);
        }
        return texture.getGlTextureId();
    }
}
