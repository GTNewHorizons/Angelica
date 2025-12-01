package com.gtnewhorizons.angelica.glsm.states;

import java.nio.Buffer;
import org.lwjgl.opengl.GL11;

/**
 * Tracks OpenGL client array state (glVertexPointer, glColorPointer, etc.)
 * This state is NOT part of the server attribute stack (push/pop attrib),
 * but is needed for display list compilation to capture glDrawArrays calls.
 *
 * When a display list is compiled, glDrawArrays reads from the current
 * vertex/color arrays at compile time (not playback time). We need to track
 * this state globally so ImmediateModeRecorder can access it.
 */
public class ClientArrayState {
    // Vertex array state
    private boolean vertexArrayEnabled = false;
    private Buffer vertexPointer = null;
    private int vertexPointerSize = 4;
    private int vertexPointerType = GL11.GL_FLOAT;
    private int vertexPointerStride = 0;

    // Color array state
    private boolean colorArrayEnabled = false;
    private Buffer colorPointer = null;
    private int colorPointerSize = 4;
    private int colorPointerType = GL11.GL_FLOAT;
    private int colorPointerStride = 0;

    // Vertex array methods
    public void setVertexPointer(int size, int type, int stride, Buffer pointer) {
        this.vertexPointerSize = size;
        this.vertexPointerType = type;
        this.vertexPointerStride = stride;
        this.vertexPointer = pointer;
    }

    public boolean isVertexArrayEnabled() {
        return vertexArrayEnabled;
    }

    public void setVertexArrayEnabled(boolean enabled) {
        this.vertexArrayEnabled = enabled;
    }

    public Buffer getVertexPointer() {
        return vertexPointer;
    }

    public int getVertexPointerSize() {
        return vertexPointerSize;
    }

    public int getVertexPointerType() {
        return vertexPointerType;
    }

    public int getVertexPointerStride() {
        return vertexPointerStride;
    }

    // Color array methods
    public void setColorPointer(int size, int type, int stride, Buffer pointer) {
        this.colorPointerSize = size;
        this.colorPointerType = type;
        this.colorPointerStride = stride;
        this.colorPointer = pointer;
    }

    public boolean isColorArrayEnabled() {
        return colorArrayEnabled;
    }

    public void setColorArrayEnabled(boolean enabled) {
        this.colorArrayEnabled = enabled;
    }

    public Buffer getColorPointer() {
        return colorPointer;
    }

    public int getColorPointerSize() {
        return colorPointerSize;
    }

    public int getColorPointerType() {
        return colorPointerType;
    }

    public int getColorPointerStride() {
        return colorPointerStride;
    }
}
