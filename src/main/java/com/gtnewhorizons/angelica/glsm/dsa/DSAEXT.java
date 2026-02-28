package com.gtnewhorizons.angelica.glsm.dsa;

import org.lwjgl.opengl.EXTDirectStateAccess;

public class DSAEXT extends DSACore {
    @Override
    public void textureImage2D(int texture, int target, int level, int internalformat, int width, int height, int border, int format, int type, long pixels_buffer_offset) {
        EXTDirectStateAccess.glTextureImage2DEXT(texture, target, level, internalformat, width, height, border, format, type, pixels_buffer_offset);
    }
}
