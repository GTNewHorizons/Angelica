package com.gtnewhorizons.angelica.glsm.states;

import lombok.Getter;
import lombok.Setter;
import org.joml.Vector3d;
import org.lwjgl.opengl.GL11;

import java.nio.FloatBuffer;

@Getter @Setter
public class FogState implements ISettableState<FogState> {
    public static final int DISTANCE_EYE_RADIAL = 0;
    public static final int DISTANCE_EYE_PLANE = 1;
    public static final int DISTANCE_EYE_PLANE_ABS = 2;

    private static final double LN2 = Math.log(2.0);
    private static final double SQRT_LN2 = Math.sqrt(LN2);

    protected int fogMode = GL11.GL_EXP;
    protected final Vector3d fogColor = new Vector3d(0.0F, 0.0F, 0.0F);
    protected float fogAlpha = 1.0F;
    protected final FloatBuffer fogColorBuffer = FloatBuffer.allocate(4);
    protected float density = 1.0F;
    protected float start;
    protected float end = 1.0F;
    protected int fogDistanceMode = DISTANCE_EYE_PLANE_ABS;

    // Mesa STATE_FOG_PARAMS_OPTIMIZED
    public float linearScale() {
        final float range = end - start;
        return range != 0.0f ? -1.0f / range : 0.0f;
    }

    public float linearBias() {
        final float range = end - start;
        return range != 0.0f ? end / range : 1.0f;
    }

    public float expScale() {
        return (float) (density / LN2);
    }

    public float exp2Scale() {
        return (float) (density / SQRT_LN2);
    }

    @Override
    public FogState set(FogState state) {
        this.fogMode = state.fogMode;
        this.fogColor.set(state.fogColor);
        this.fogAlpha = state.fogAlpha;
        this.fogColorBuffer.put(0, state.fogColorBuffer.get(0));
        this.fogColorBuffer.put(1, state.fogColorBuffer.get(1));
        this.fogColorBuffer.put(2, state.fogColorBuffer.get(2));
        this.fogColorBuffer.put(3, state.fogColorBuffer.get(3));
        this.density = state.density;
        this.start = state.start;
        this.end = state.end;
        this.fogDistanceMode = state.fogDistanceMode;
        return this;
    }
    @Override
    public boolean sameAs(Object state) {
        if (this == state) return true;
        if (!(state instanceof FogState fogState)) return false;
        return fogMode == fogState.fogMode && fogDistanceMode == fogState.fogDistanceMode && Float.compare(fogState.fogAlpha, fogAlpha) == 0 && Float.compare(fogState.density, density) == 0 && Float.compare(fogState.start, start) == 0 && Float.compare(fogState.end, end) == 0 && fogColor.equals(fogState.fogColor);
    }

    @Override
    public FogState copy() {
        return new FogState().set(this);
    }
}
