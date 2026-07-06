package com.gtnewhorizons.angelica.glsm.states;

import lombok.Getter;
import lombok.Setter;

/**
 * Tracks GL point state: size.
 * Corresponds to GL_POINT_BIT attributes.
 */
@Getter @Setter
public class PointState implements ISettableState<PointState> {
    private float size = 1.0f;

    @Override
    public PointState set(PointState state) {
        this.size = state.size;
        return this;
    }

    @Override
    public boolean sameAs(Object state) {
        if (this == state) return true;
        if (!(state instanceof PointState pointState)) return false;
        return Float.compare(size, pointState.size) == 0;
    }

    @Override
    public PointState copy() {
        return new PointState().set(this);
    }
}
