package com.gtnewhorizons.angelica.glsm.texture;

import com.gtnewhorizons.angelica.glsm.RenderSystem;
import lombok.Getter;
import lombok.Setter;
import org.lwjgl.opengl.GL11;

public class TextureInfo {

    @Getter protected final int id;
    protected int internalFormat = -1;
    protected int width = -1;
    protected int height = -1;

    @Getter @Setter protected int minFilter = GL11.GL_NEAREST_MIPMAP_LINEAR;
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
            internalFormat = RenderSystem.getTexLevelParameteri(id, 0, GL11.GL_TEXTURE_INTERNAL_FORMAT);
        }
        return internalFormat;
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
}
