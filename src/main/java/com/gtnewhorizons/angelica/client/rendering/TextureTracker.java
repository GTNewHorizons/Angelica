package com.gtnewhorizons.angelica.client.rendering;

import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import net.coderbot.iris.gl.state.StateUpdateNotifiers;
import net.minecraft.client.renderer.texture.AbstractTexture;
import org.jetbrains.annotations.Nullable;

public class TextureTracker {

    /**
     * Adapted from Iris for use in GLSM
     */

    public static final TextureTracker INSTANCE = new TextureTracker();
    
    private static Runnable bindTextureListener;
    
    static {
        StateUpdateNotifiers.bindTextureNotifier = listener -> bindTextureListener = listener;
    }
    
    private final Int2ObjectMap<AbstractTexture> textures = new Int2ObjectOpenHashMap<>();

    private TextureTracker() {
    }

    public void trackTexture(int id, AbstractTexture texture) {
        textures.put(id, texture);
    }

    @Nullable
    public AbstractTexture getTexture(int id) {
        return textures.get(id);
    }
    
    public void onBindTexture() {
        if (bindTextureListener != null) {
            bindTextureListener.run();
        }
    }
    
    public void onDeleteTexture(int id) {
        textures.remove(id);
    }
}
