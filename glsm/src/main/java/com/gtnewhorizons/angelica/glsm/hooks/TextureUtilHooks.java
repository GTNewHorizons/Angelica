package com.gtnewhorizons.angelica.glsm.hooks;

import com.gtnewhorizons.angelica.glsm.DisplayListManager;
import com.gtnewhorizons.angelica.glsm.GLStateManager;
import com.gtnewhorizons.angelica.glsm.RenderSystem;
import org.lwjgl.opengl.EXTTextureFilterAnisotropic;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;

public final class TextureUtilHooks {

    private TextureUtilHooks() {}

    private static int savedFilterTextureId;

    public static void setMaxLevel(int mipmapLevels) {
        GLStateManager.glTexParameteri(GL11.GL_TEXTURE_2D, GL12.GL_TEXTURE_MAX_LEVEL, mipmapLevels);
    }

    public static void captureBoundFilterTexture() {
        savedFilterTextureId = GLStateManager.getBoundTextureForServerState();
    }

    public static void clearSavedFilterTexture() {
        savedFilterTextureId = 0;
    }

    public static void restoreFilter(int min, int mag) {
        final int id = savedFilterTextureId;
        if (id != 0 && DisplayListManager.getRecordMode() == DisplayListManager.RecordMode.NONE) {
            RenderSystem.texParameteri(id, GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, min);
            RenderSystem.texParameteri(id, GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, mag);
        } else {
            GLStateManager.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, min);
            GLStateManager.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, mag);
        }
    }

    public static void restoreAniso(float aniso) {
        final int id = savedFilterTextureId;
        if (id != 0 && DisplayListManager.getRecordMode() == DisplayListManager.RecordMode.NONE) {
            RenderSystem.texParameterf(id, GL11.GL_TEXTURE_2D, EXTTextureFilterAnisotropic.GL_TEXTURE_MAX_ANISOTROPY_EXT, aniso);
        } else {
            GLStateManager.glTexParameterf(GL11.GL_TEXTURE_2D, EXTTextureFilterAnisotropic.GL_TEXTURE_MAX_ANISOTROPY_EXT, aniso);
        }
    }
}
