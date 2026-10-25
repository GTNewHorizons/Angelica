package com.gtnewhorizons.angelica.debug.render;

import com.gtnewhorizons.angelica.glsm.GLStateManager;
import org.joml.Matrix4fc;
import org.lwjgl.BufferUtils;

import java.nio.FloatBuffer;

public final class DebugProgram {

    private final int handle;
    private final int mvpLocation;
    private final int lineWidthLocation;
    private final int viewportLocation;
    private final FloatBuffer matrix = BufferUtils.createFloatBuffer(16);

    DebugProgram(int handle) {
        this.handle = handle;
        this.mvpLocation = GLStateManager.glGetUniformLocation(handle, "u_MVP");
        this.lineWidthLocation = GLStateManager.glGetUniformLocation(handle, "u_LineWidth");
        this.viewportLocation = GLStateManager.glGetUniformLocation(handle, "u_ViewportSize");
    }

    public void use() {
        GLStateManager.glUseProgram(handle);
    }

    public void mvp(Matrix4fc mvp) {
        mvp.get(matrix);
        GLStateManager.glUniformMatrix4(mvpLocation, false, matrix);
    }

    public void lineWidth(float pixels) {
        GLStateManager.glUniform1f(lineWidthLocation, pixels);
    }

    public void viewport(int width, int height) {
        GLStateManager.glUniform2f(viewportLocation, width, height);
    }
}
