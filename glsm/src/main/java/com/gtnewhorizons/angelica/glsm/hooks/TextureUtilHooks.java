package com.gtnewhorizons.angelica.glsm.hooks;

import com.gtnewhorizons.angelica.glsm.DisplayListManager;
import com.gtnewhorizons.angelica.glsm.GLContextState;
import com.gtnewhorizons.angelica.glsm.GLStateManager;
import com.gtnewhorizons.angelica.glsm.RenderSystem;
import com.gtnewhorizons.angelica.glsm.backend.BackendManager;
import com.gtnewhorizons.angelica.glsm.texture.TextureInfo;
import com.gtnewhorizons.angelica.glsm.texture.TextureInfoCache;
import org.lwjgl.opengl.EXTTextureFilterAnisotropic;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;

public final class TextureUtilHooks {

    private static int pausedTexture;
    private static int pausedMin;
    private static int pausedMag;
    private static float pausedAniso;

    private static int nearestTexture;
    private static int nearestMin;
    private static int nearestMag;
    private static float nearestAniso;

    private TextureUtilHooks() {}

    public static void setMaxLevel(int mipmapLevels) {
        GLStateManager.glTexParameteri(GL11.GL_TEXTURE_2D, GL12.GL_TEXTURE_MAX_LEVEL, mipmapLevels);
    }

    public static void captureBoundFilterTexture() {
        final GLContextState ctx = GLStateManager.ctx();
        final int id = GLStateManager.getBoundTextureForServerState();
        ctx.savedFilterTextureId = id;
        final TextureInfo info = TextureInfoCache.INSTANCE.getInfo(id);
        if (info != null) {
            ctx.savedFilterMin = info.getMinFilter();
            ctx.savedFilterMag = info.getMagFilter();
            ctx.savedFilterAniso = info.getMaxAnisotropy();
        } else {
            ctx.savedFilterMin = -1;
        }
    }

    public static void clearSavedFilterTexture() {
        GLStateManager.ctx().savedFilterTextureId = 0;
    }

    public static void restoreFilter(int min, int mag) {
        final int id = GLStateManager.ctx().savedFilterTextureId;
        if (id != 0 && DisplayListManager.getRecordMode() == DisplayListManager.RecordMode.NONE) {
            RenderSystem.texParameteri(id, GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, min);
            RenderSystem.texParameteri(id, GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, mag);
        } else {
            GLStateManager.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, min);
            GLStateManager.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, mag);
        }
    }

    public static void restoreAniso(float aniso) {
        final int id = GLStateManager.ctx().savedFilterTextureId;
        if (id != 0 && DisplayListManager.getRecordMode() == DisplayListManager.RecordMode.NONE) {
            RenderSystem.texParameterf(id, GL11.GL_TEXTURE_2D, EXTTextureFilterAnisotropic.GL_TEXTURE_MAX_ANISOTROPY_EXT, aniso);
        } else {
            GLStateManager.glTexParameterf(GL11.GL_TEXTURE_2D, EXTTextureFilterAnisotropic.GL_TEXTURE_MAX_ANISOTROPY_EXT, aniso);
        }
    }

    public static boolean pauseTemporaryFilter() {
        final GLContextState ctx = GLStateManager.ctx();
        final int id = ctx.savedFilterTextureId;
        if (id == 0 || pausedTexture != 0 || ctx.savedFilterMin < 0
            || DisplayListManager.getRecordMode() != DisplayListManager.RecordMode.NONE) return false;
        final TextureInfo info = TextureInfoCache.INSTANCE.getInfo(id);
        if (info == null) return false;
        pausedTexture = id;
        pausedMin = info.getMinFilter();
        pausedMag = info.getMagFilter();
        pausedAniso = info.getMaxAnisotropy();
        setFilter(id, ctx.savedFilterMin, ctx.savedFilterMag, ctx.savedFilterAniso);
        return true;
    }

    public static void resumeTemporaryFilter() {
        if (pausedTexture == 0) return;
        final int id = pausedTexture;
        pausedTexture = 0;
        setFilter(id, pausedMin, pausedMag, pausedAniso);
    }

    /** TextureUtil.func_152777_a(false, false, 1) without using vanilla's single save slot, which the caller may hold. */
    public static void forceNearestFilter() {
        final int id = GLStateManager.getBoundTextureForServerState();
        final TextureInfo info = id != 0 ? TextureInfoCache.INSTANCE.getInfo(id) : null;
        if (info == null) {
            nearestTexture = 0;
            return;
        }
        nearestTexture = id;
        nearestMin = info.getMinFilter();
        nearestMag = info.getMagFilter();
        nearestAniso = info.getMaxAnisotropy();
        setFilter(id, GL11.GL_NEAREST, GL11.GL_NEAREST, 1.0f);
    }

    public static void restoreFilterAfterNearest() {
        if (nearestTexture == 0) return;
        final int id = nearestTexture;
        nearestTexture = 0;
        setFilter(id, nearestMin, nearestMag, nearestAniso);
    }

    private static void setFilter(int id, int min, int mag, float aniso) {
        final boolean anisotropic = BackendManager.RENDER_BACKEND.isAnisotropicSupported();
        if (DisplayListManager.getRecordMode() == DisplayListManager.RecordMode.NONE) {
            RenderSystem.texParameteri(id, GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, min);
            RenderSystem.texParameteri(id, GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, mag);
            if (anisotropic) RenderSystem.texParameterf(id, GL11.GL_TEXTURE_2D, EXTTextureFilterAnisotropic.GL_TEXTURE_MAX_ANISOTROPY_EXT, aniso);
        } else {
            GLStateManager.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, min);
            GLStateManager.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, mag);
            if (anisotropic) GLStateManager.glTexParameterf(GL11.GL_TEXTURE_2D, EXTTextureFilterAnisotropic.GL_TEXTURE_MAX_ANISOTROPY_EXT, aniso);
        }
    }
}
