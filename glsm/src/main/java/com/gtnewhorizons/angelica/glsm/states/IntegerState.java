package com.gtnewhorizons.angelica.glsm.states;

import lombok.Getter;
import lombok.Setter;

public class IntegerState implements ISettableState<IntegerState> {
    @Getter @Setter private int value;

    @Override
    public IntegerState set(IntegerState state) {
        this.value = state.value;
        return this;
    }

    @Override
    public boolean sameAs(Object state) {
        if (this == state) return true;
        if (!(state instanceof IntegerState integerState)) return false;
        return value == integerState.value;
    }

    @Override
    public IntegerState copy() {
        return new IntegerState().set(this);
    }
}
