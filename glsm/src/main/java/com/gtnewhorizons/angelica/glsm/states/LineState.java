package com.gtnewhorizons.angelica.glsm.states;

import lombok.Getter;
import lombok.Setter;

/**
 * Tracks GL line state: width, stipple factor, and stipple pattern.
 * Corresponds to GL_LINE_BIT attributes.
 */
@Getter @Setter
public class LineState implements ISettableState<LineState> {
    private float width = 1.0f;
    private int stippleFactor = 1;
    private short stipplePattern = (short) 0xFFFF;

    @Override
    public LineState set(LineState state) {
        this.width = state.width;
        this.stippleFactor = state.stippleFactor;
        this.stipplePattern = state.stipplePattern;
        return this;
    }

    @Override
    public boolean sameAs(Object state) {
        if (this == state) return true;
        if (!(state instanceof LineState lineState)) return false;
        return Float.compare(width, lineState.width) == 0
            && stippleFactor == lineState.stippleFactor
            && stipplePattern == lineState.stipplePattern;
    }

    @Override
    public LineState copy() {
        return new LineState().set(this);
    }
}
