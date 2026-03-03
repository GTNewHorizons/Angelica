package com.gtnewhorizons.angelica.glsm.dsa;

import org.lwjgl.opengl.EXTDirectStateAccess;

import java.nio.ByteBuffer;
import java.nio.IntBuffer;

public class DSAEXT extends DSACore {
    @Override
    public void textureImage2D(int texture, int target, int level, int internalformat, int width, int height, int border, int format, int type, ByteBuffer pixels) {
        EXTDirectStateAccess.glTextureImage2DEXT(texture, target, level, internalformat, width, height, border, format, type, pixels);
    }

    @Override
    public void textureImage2D(int texture, int target, int level, int internalformat, int width, int height, int border, int format, int type, IntBuffer pixels) {
        EXTDirectStateAccess.glTextureImage2DEXT(texture, target, level, internalformat, width, height, border, format, type, pixels);
    }
}
