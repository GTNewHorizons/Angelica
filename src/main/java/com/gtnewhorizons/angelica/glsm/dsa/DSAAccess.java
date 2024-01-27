package com.gtnewhorizons.angelica.glsm.dsa;

import java.nio.FloatBuffer;
import java.nio.IntBuffer;

public interface DSAAccess {
    void generateMipmaps(int texture, int target);

    void texParameteri(int texture, int target, int pname, int param);
    void texParameterf(int texture, int target, int pname, float param);
    void texParameteriv(int texture, int target, int pname, IntBuffer params);

    void readBuffer(int framebuffer, int buffer);

    void drawBuffers(int framebuffer, IntBuffer buffers);

    int getTexParameteri(int texture, int target, int pname);

    void copyTexSubImage2D(int destTexture, int target, int i, int i1, int i2, int i3, int i4, int width, int height);

    void bindTextureToUnit(int unit, int texture);

    int bufferStorage(int target, FloatBuffer data, int usage);

    void blitFramebuffer(int source, int dest, int offsetX, int offsetY, int width, int height, int offsetX2, int offsetY2, int width2, int height2, int bufferChoice, int filter);

    void framebufferTexture2D(int fb, int fbtarget, int attachment, int target, int texture, int levels);

    int createFramebuffer();
    int createTexture(int target);
}
