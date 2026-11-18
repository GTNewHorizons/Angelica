package com.gtnewhorizons.angelica.glsm.states;

import lombok.Getter;
import lombok.Setter;
import org.lwjgl.opengl.GL20;

/**
 * Tracks GL point state: size.
 * Corresponds to GL_POINT_BIT attributes.
 */
@Getter @Setter
public class PointState implements ISettableState<PointState> {
    private float size = 1.0f;
    private float minSize = 0.0f;
    private float maxSize = Float.NaN;
    private float fadeThreshold = 1.0f;
    private float attenuationConstant = 1.0f;
    private float attenuationLinear = 0.0f;
    private float attenuationQuadratic = 0.0f;
    private int spriteCoordOrigin = GL20.GL_UPPER_LEFT;

    @Override
    public PointState set(PointState state) {
        this.size = state.size;
        this.minSize = state.minSize;
        this.maxSize = state.maxSize;
        this.fadeThreshold = state.fadeThreshold;
        this.attenuationConstant = state.attenuationConstant;
        this.attenuationLinear = state.attenuationLinear;
        this.attenuationQuadratic = state.attenuationQuadratic;
        this.spriteCoordOrigin = state.spriteCoordOrigin;
        return this;
    }

    @Override
    public boolean sameAs(Object state) {
        if (this == state) return true;
        if (!(state instanceof PointState pointState)) return false;
        return Float.compare(size, pointState.size) == 0
            && Float.compare(minSize, pointState.minSize) == 0
            && Float.compare(maxSize, pointState.maxSize) == 0
            && Float.compare(fadeThreshold, pointState.fadeThreshold) == 0
            && Float.compare(attenuationConstant, pointState.attenuationConstant) == 0
            && Float.compare(attenuationLinear, pointState.attenuationLinear) == 0
            && Float.compare(attenuationQuadratic, pointState.attenuationQuadratic) == 0
            && spriteCoordOrigin == pointState.spriteCoordOrigin;
    }

    @Override
    public PointState copy() {
        return new PointState().set(this);
    }
}
