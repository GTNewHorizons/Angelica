package com.gtnewhorizons.angelica.glsm.dsa;

import java.nio.ByteBuffer;
import java.nio.IntBuffer;

import static com.gtnewhorizons.angelica.glsm.backend.BackendManager.RENDER_BACKEND;

public class DSAEXT extends DSACore {
    @Override
    public void textureImage2D(int texture, int target, int level, int internalformat, int width, int height, int border, int format, int type, ByteBuffer pixels) {
        RENDER_BACKEND.textureImage2DEXT(texture, target, level, internalformat, width, height, border, format, type, pixels);
    }

    @Override
    public void textureImage2D(int texture, int target, int level, int internalformat, int width, int height, int border, int format, int type, IntBuffer pixels) {
        RENDER_BACKEND.textureImage2DEXT(texture, target, level, internalformat, width, height, border, format, type, pixels);
    }
}
