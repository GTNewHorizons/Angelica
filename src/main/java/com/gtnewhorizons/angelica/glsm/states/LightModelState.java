package com.gtnewhorizons.angelica.glsm.states;

import com.gtnewhorizons.angelica.glsm.GLStateManager;
import org.joml.Vector4f;
import org.joml.Vector4i;
import org.lwjgl.opengl.GL12;

import java.nio.FloatBuffer;
import java.nio.IntBuffer;

import static com.gtnewhorizons.angelica.glsm.GLStateManager.i2f;

public class LightModelState implements ISettableState<LightModelState> {

    private static final Vector4f vector4f = new Vector4f();
    private static final Vector4i vector4i = new Vector4i();

    public final Vector4f ambient;
    public int colorControl;
    public float localViewer;
    public float twoSide;

    public LightModelState() {
        ambient = new Vector4f(0.2F, 0.2F, 0.2F, 1.0F);
        colorControl = GL12.GL_SINGLE_COLOR;
        localViewer = 0.0F;
        twoSide = 0.0F;
    }

    public void setAmbient(FloatBuffer newBuffer) {
        vector4f.set(newBuffer);
        if (GLStateManager.shouldBypassCache() || !this.ambient.equals(vector4f)) {
            this.ambient.set(vector4f);
        }
    }

    public void setAmbient(IntBuffer newBuffer) {
        vector4i.set(newBuffer);
        vector4f.set(i2f(vector4i.x), i2f(vector4i.y), i2f(vector4i.z), i2f(vector4i.w));
        if (GLStateManager.shouldBypassCache() || !this.ambient.equals(vector4f)) {
            this.ambient.set(vector4f);
        }
    }

    public void setColorControl(int val) {
        if (GLStateManager.shouldBypassCache() || this.colorControl != val) {
            this.colorControl = val;
        }
    }

    public void setColorControl(IntBuffer newBuffer) {
        setColorControl(newBuffer.get());
    }

    public void setLocalViewer(float val) {
        if (GLStateManager.shouldBypassCache() || Float.compare(this.localViewer, val) != 0) {
            this.localViewer = val;
        }
    }

    public void setLocalViewer(FloatBuffer newBuffer) {
        setLocalViewer(newBuffer.get());
    }

    public void setLocalViewer(IntBuffer newBuffer) {
        setLocalViewer((float) newBuffer.get());
    }

    public void setLocalViewer(int val) {
        setLocalViewer((float) val);
    }

    public void setTwoSide(float val) {
        if (GLStateManager.shouldBypassCache() || Float.compare(this.twoSide, val) != 0) {
            this.twoSide = val;
        }
    }

    public void setTwoSide(FloatBuffer newBuffer) {
        setTwoSide(newBuffer.get());
    }

    public void setTwoSide(IntBuffer newBuffer) {
        setTwoSide((float) newBuffer.get());
    }

    public void setTwoSide(int val) {
        setTwoSide((float) val);
    }

    @Override
    public LightModelState set(LightModelState state) {
        this.ambient.set(state.ambient);
        this.colorControl = state.colorControl;
        this.localViewer = state.localViewer;
        this.twoSide = state.twoSide;

        return this;
    }

    @Override
    public boolean sameAs(Object state) {
        if (this == state) return true;
        if (!(state instanceof LightModelState lightModelState)) return false;
        return this.ambient.equals(lightModelState.ambient)
            && this.colorControl == lightModelState.colorControl
            && Float.compare(lightModelState.localViewer, this.localViewer) == 0
            && Float.compare(lightModelState.twoSide, this.twoSide) == 0;
    }

    @Override
    public LightModelState copy() {
        return new LightModelState().set(this);
    }
}
