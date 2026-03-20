package com.gtnewhorizons.angelica.client.rendering;

import com.gtnewhorizons.angelica.glsm.GLStateManager;
import com.gtnewhorizons.angelica.glsm.RenderSystem;
import com.gtnewhorizons.angelica.glsm.hooks.GLSMHooks;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import net.minecraft.client.renderer.texture.AbstractTexture;
import org.jetbrains.annotations.Nullable;

public class TextureTracker {

    /**
     * Adapted from Iris for use in GLSM
     */

    public static final TextureTracker INSTANCE = new TextureTracker();

    private final Int2ObjectMap<AbstractTexture> textures = new Int2ObjectOpenHashMap<>();

    private boolean lockBindCallback;

    private TextureTracker() {
    }

    public void trackTexture(int id, AbstractTexture texture) {
        textures.put(id, texture);
    }

    @Nullable
    public AbstractTexture getTexture(int id) {
        return textures.get(id);
    }

    public void onBindTexture(int id) {
        if (lockBindCallback) {
            return;
        }
        if (GLStateManager.getActiveTextureUnit() == 0) {
            lockBindCallback = true;
            if (GLSMHooks.TEXTURE_BIND.hasListeners()) {
                GLSMHooks.textureBindEvent.textureId = id;
                GLSMHooks.TEXTURE_BIND.post(GLSMHooks.textureBindEvent);
            }
            // Reset texture state
            RenderSystem.bindTextureToUnit(0, id);
            lockBindCallback = false;
        }
    }

    public void onDeleteTexture(int id) {
        textures.remove(id);
    }
}
