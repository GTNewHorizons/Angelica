package com.gtnewhorizons.angelica.glsm.states;

import lombok.Getter;
import lombok.Setter;
import org.joml.Vector3d;
import org.lwjgl.opengl.GL11;

import java.nio.FloatBuffer;

@Getter @Setter
public class FogState implements ISettableState<FogState> {
    protected int fogMode = GL11.GL_EXP;
    protected final Vector3d fogColor = new Vector3d(0.0F, 0.0F, 0.0F);
    protected float fogAlpha = 1.0F;
    protected final FloatBuffer fogColorBuffer = FloatBuffer.allocate(4);
    protected float density = 1.0F;
    protected float start;
    protected float end = 1.0F;

    @Override
    public FogState set(FogState state) {
        this.fogMode = state.fogMode;
        this.fogColor.set(state.fogColor);
        this.fogAlpha = state.fogAlpha;
        this.fogColorBuffer.put(0, state.fogColorBuffer.get(0));
        this.density = state.density;
        this.start = state.start;
        this.end = state.end;
        return this;
    }
    @Override
    public boolean sameAs(Object state) {
        if (this == state) return true;
        if (!(state instanceof FogState fogState)) return false;
        return fogMode == fogState.fogMode && Float.compare(fogState.fogAlpha, fogAlpha) == 0 && Float.compare(fogState.density, density) == 0 && Float.compare(fogState.start, start) == 0 && Float.compare(fogState.end, end) == 0 && fogColor.equals(fogState.fogColor);
    }

    @Override
    public FogState copy() {
        return new FogState().set(this);
    }
}
