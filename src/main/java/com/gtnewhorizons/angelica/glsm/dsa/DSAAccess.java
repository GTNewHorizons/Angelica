package com.gtnewhorizons.angelica.glsm.dsa;

import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;

public interface DSAAccess {
    void generateMipmaps(int texture, int target);

    // Texture image upload - DSA allows specifying texture directly without binding
    void textureImage2D(int texture, int target, int level, int internalformat, int width, int height, int border, int format, int type, long pixels_buffer_offset);
    void textureSubImage2D(int texture, int target, int level, int xoffset, int yoffset, int width, int height, int format, int type, long pixels_buffer_offset);

    void texParameteri(int texture, int target, int pname, int param);
    void texParameterf(int texture, int target, int pname, float param);
    void texParameteriv(int texture, int target, int pname, IntBuffer params);

    void readBuffer(int framebuffer, int buffer);

    void drawBuffers(int framebuffer, IntBuffer buffers);

    int getTexParameteri(int texture, int target, int pname);

    int getTexLevelParameteri(int texture, int level, int pname);

    void copyTexSubImage2D(int destTexture, int target, int i, int i1, int i2, int i3, int i4, int width, int height);

    void bindTextureToUnit(int unit, int texture);
    void bindTextureToUnit(int target, int unit, int texture);

    int bufferStorage(int target, FloatBuffer data, int usage);

    void blitFramebuffer(int source, int dest, int offsetX, int offsetY, int width, int height, int offsetX2, int offsetY2, int width2, int height2, int bufferChoice, int filter);

    void framebufferTexture2D(int fb, int fbtarget, int attachment, int target, int texture, int levels);

    int createFramebuffer();
    int createTexture(int target);

    // Texture storage methods for custom images
    void textureStorage1D(int texture, int target, int levels, int internalFormat, int width);
    void textureStorage2D(int texture, int target, int levels, int internalFormat, int width, int height);
    void textureStorage3D(int texture, int target, int levels, int internalFormat, int width, int height, int depth);
}
