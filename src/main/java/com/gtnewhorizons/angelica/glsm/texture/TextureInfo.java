package com.gtnewhorizons.angelica.glsm.texture;

import com.gtnewhorizons.angelica.glsm.GLStateManager;
import lombok.Getter;
import lombok.Setter;
import org.lwjgl.opengl.GL11;

public class TextureInfo {

    @Getter protected final int id;
    protected int internalFormat = -1;
    protected int width = -1;
    protected int height = -1;

    @Getter @Setter protected int minFilter = GL11.GL_LINEAR_MIPMAP_LINEAR;
    @Getter @Setter protected int magFilter = GL11.GL_LINEAR;
    @Getter @Setter protected int wrapS = GL11.GL_REPEAT;
    @Getter @Setter protected int wrapT = GL11.GL_REPEAT;
    @Getter @Setter protected int maxLevel = 1000;
    @Getter @Setter protected int minLod = -1000;
    @Getter @Setter protected int maxLod = 1000;
    @Getter @Setter protected float lodBias = 0.0f;

    @Getter @Setter protected float maxAnisotropy = 1.0f;

    protected TextureInfo(int id) {
        this.id = id;
    }

    public int getInternalFormat() {
        if (internalFormat == -1) {
            internalFormat = fetchLevelParameter(GL11.GL_TEXTURE_INTERNAL_FORMAT);
        }
        return internalFormat;
    }

    public int getWidth() {
        if (width == -1) {
            width = fetchLevelParameter(GL11.GL_TEXTURE_WIDTH);
        }
        return width;
    }

    public int getHeight() {
        if (height == -1) {
            height = fetchLevelParameter(GL11.GL_TEXTURE_HEIGHT);
        }
        return height;
    }

    private int fetchLevelParameter(int pname) {
        // Keep track of what texture was bound before
        final int previousTextureBinding = GLStateManager.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);

        // Bind this texture and grab the parameter from it.
        GLStateManager.glBindTexture(GL11.GL_TEXTURE_2D, id);
        final int parameter = GL11.glGetTexLevelParameteri(GL11.GL_TEXTURE_2D, 0, pname);

        // Make sure to re-bind the previous texture to avoid issues.
        GLStateManager.glBindTexture(GL11.GL_TEXTURE_2D, previousTextureBinding);

        return parameter;
    }
}
