package com.gtnewhorizons.angelica.glsm.states;

import com.gtnewhorizons.angelica.glsm.GLStateManager;
import lombok.Getter;
import org.lwjgl.opengl.GL11;

@Getter
public class MatrixMode implements ISettableState<MatrixMode> {
    protected int mode = GL11.GL_MODELVIEW;

    public void setMode(int mode) {
        if(mode != GL11.GL_MODELVIEW && mode != GL11.GL_PROJECTION && mode != GL11.GL_TEXTURE && mode != GL11.GL_COLOR) {
            // Invalid mode, do nothing on the cache, but pass it along to OGL
            GL11.glMatrixMode(mode);
            return;
        }

        if(this.mode != mode || GLStateManager.shouldBypassCache()) {
            this.mode = mode;
            GL11.glMatrixMode(mode);
        }
    }

    public int getMatrix() {
        return switch(mode) {
            case GL11.GL_MODELVIEW -> GL11.GL_MODELVIEW_MATRIX;
            case GL11.GL_PROJECTION -> GL11.GL_PROJECTION_MATRIX;
            case GL11.GL_TEXTURE -> GL11.GL_TEXTURE_MATRIX;
            default -> throw new IllegalStateException("Unexpected value: " + mode);
        };
    }

    @Override
    public MatrixMode set(MatrixMode state) {
        this.mode = state.mode;
        return this;
    }

    @Override
    public boolean sameAs(Object state) {
        if(this == state) return true;
        if(!(state instanceof MatrixMode matrixMode)) return false;
        return mode == matrixMode.mode;
    }

    @Override
    public MatrixMode copy() {
        return new MatrixMode().set(this);
    }
}
