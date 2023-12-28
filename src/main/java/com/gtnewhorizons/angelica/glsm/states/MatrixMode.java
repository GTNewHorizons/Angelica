package com.gtnewhorizons.angelica.glsm.states;

import com.gtnewhorizons.angelica.glsm.GLStateManager;
import lombok.Getter;
import org.lwjgl.opengl.GL11;

@Getter
public class MatrixMode implements ISettableState<MatrixMode> {
    protected int mode = GL11.GL_MODELVIEW;

    public void setMode(int mode) {
        if(mode != GL11.GL_MODELVIEW && mode != GL11.GL_PROJECTION && mode != GL11.GL_TEXTURE && mode != GL11.GL_COLOR) {
            // Invalid mode, do nothing
            return;
        }

        if(this.mode != mode || GLStateManager.BYPASS_CACHE) {
            this.mode = mode;
            GL11.glMatrixMode(mode);
        }
    }

    @Override
    public MatrixMode set(MatrixMode state) {
        this.mode = state.mode;
        return this;
    }
}
