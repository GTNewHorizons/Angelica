package com.gtnewhorizons.angelica.glsm.states;

import lombok.Getter;
import lombok.Setter;
import org.lwjgl.opengl.GL11;

/**
 * Tracks GL polygon state: polygon mode (front/back) and polygon offset.
 * Corresponds to GL_POLYGON_BIT attributes (excluding enable bits which are BooleanStateStack).
 */
@Getter @Setter
public class PolygonState implements ISettableState<PolygonState> {
    private int frontMode = GL11.GL_FILL;
    private int backMode = GL11.GL_FILL;
    private float offsetFactor = 0.0f;
    private float offsetUnits = 0.0f;
    private int cullFaceMode = GL11.GL_BACK;
    private int frontFace = GL11.GL_CCW;

    @Override
    public PolygonState set(PolygonState state) {
        this.frontMode = state.frontMode;
        this.backMode = state.backMode;
        this.offsetFactor = state.offsetFactor;
        this.offsetUnits = state.offsetUnits;
        this.cullFaceMode = state.cullFaceMode;
        this.frontFace = state.frontFace;
        return this;
    }

    @Override
    public boolean sameAs(Object state) {
        if (this == state) return true;
        if (!(state instanceof PolygonState polygonState)) return false;
        return frontMode == polygonState.frontMode
            && backMode == polygonState.backMode
            && Float.compare(offsetFactor, polygonState.offsetFactor) == 0
            && Float.compare(offsetUnits, polygonState.offsetUnits) == 0
            && cullFaceMode == polygonState.cullFaceMode
            && frontFace == polygonState.frontFace;
    }

    @Override
    public PolygonState copy() {
        return new PolygonState().set(this);
    }
}
