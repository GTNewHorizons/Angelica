package com.gtnewhorizons.angelica.glsm.texture;

import com.gtnewhorizons.angelica.glsm.GLStateManager;
import com.gtnewhorizons.angelica.glsm.RenderSystem;
import lombok.Getter;
import lombok.Setter;
import org.lwjgl.opengl.EXTTextureFilterAnisotropic;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;

import static com.gtnewhorizons.angelica.glsm.backend.BackendManager.RENDER_BACKEND;

public class TextureInfo {

    private static final int UNSET_INT = Integer.MIN_VALUE;
    private static final float UNSET_FLOAT = Float.NEGATIVE_INFINITY;

    @Getter protected final int id;
    @Getter @Setter protected int target = GL11.GL_TEXTURE_2D;
    protected int internalFormat = -1;
    protected int resolvedInternalFormat = -1;
    protected int width = -1;
    protected int height = -1;

    @Setter protected int minFilter = UNSET_INT;
    @Setter protected int magFilter = UNSET_INT;
    @Setter protected int wrapS = UNSET_INT;
    @Setter protected int wrapT = UNSET_INT;
    @Setter protected int maxLevel = UNSET_INT;
    @Getter @Setter protected int baseLevel = 0;
    @Getter @Setter protected int minLod = -1000;
    @Getter @Setter protected int maxLod = 1000;
    @Getter @Setter protected float lodBias = 0.0f;

    @Setter protected float maxAnisotropy = UNSET_FLOAT;

    @Getter @Setter protected boolean generateMipmap = false;

    protected TextureInfo(int id) {
        this.id = id;
    }

    public int getInternalFormat() {
        if (internalFormat == -1) {
            internalFormat = RenderSystem.getTexLevelParameteri(id, 0, GL11.GL_TEXTURE_INTERNAL_FORMAT);
        }
        return internalFormat;
    }

    public int getResolvedInternalFormat() {
        if (resolvedInternalFormat == -1) {
            resolvedInternalFormat = RenderSystem.getTexLevelParameteri(id, 0, GL11.GL_TEXTURE_INTERNAL_FORMAT);
        }
        return resolvedInternalFormat;
    }

    public boolean needsInternalFormatResolve() {
        return resolvedInternalFormat == -1;
    }

    public int getWidth() {
        if (width == -1) {
            width = RenderSystem.getTexLevelParameteri(id, 0, GL11.GL_TEXTURE_WIDTH);
        }
        return width;
    }

    public int getHeight() {
        if (height == -1) {
            height = RenderSystem.getTexLevelParameteri(id, 0, GL11.GL_TEXTURE_HEIGHT);
        }
        return height;
    }

    public int getMinFilter() {
        if (minFilter == UNSET_INT) minFilter = queryParameteri(GL11.GL_TEXTURE_MIN_FILTER);
        return minFilter;
    }

    public int getMagFilter() {
        if (magFilter == UNSET_INT) magFilter = queryParameteri(GL11.GL_TEXTURE_MAG_FILTER);
        return magFilter;
    }

    public int getWrapS() {
        if (wrapS == UNSET_INT) wrapS = queryParameteri(GL11.GL_TEXTURE_WRAP_S);
        return wrapS;
    }

    public int getWrapT() {
        if (wrapT == UNSET_INT) wrapT = queryParameteri(GL11.GL_TEXTURE_WRAP_T);
        return wrapT;
    }

    public int getMaxLevel() {
        if (maxLevel == UNSET_INT) maxLevel = queryParameteri(GL12.GL_TEXTURE_MAX_LEVEL);
        return maxLevel;
    }

    public float getMaxAnisotropy() {
        if (maxAnisotropy == UNSET_FLOAT) {
            if (!RENDER_BACKEND.isAnisotropicSupported()) maxAnisotropy = 1.0f;
            else maxAnisotropy = queryParameterf(EXTTextureFilterAnisotropic.GL_TEXTURE_MAX_ANISOTROPY_EXT);
        }
        return maxAnisotropy;
    }

    private int queryParameteri(int pname) {
        if (GLStateManager.getBoundTextureForServerState() == id) {
            return RENDER_BACKEND.getTexParameteri(target, pname);
        }
        return RENDER_BACKEND.getTextureParameteri(id, target, pname);
    }

    private float queryParameterf(int pname) {
        if (GLStateManager.getBoundTextureForServerState() == id) {
            return RENDER_BACKEND.getTexParameterf(target, pname);
        }
        return RENDER_BACKEND.getTextureParameterf(id, target, pname);
    }
}
