package com.gtnewhorizons.angelica.glsm;

import com.gtnewhorizons.angelica.glsm.stacks.BooleanStateStack;

public final class ClipPlaneBooleanState extends BooleanStateStack {

    private final GLContextState owner;
    private final int index;

    ClipPlaneBooleanState(GLContextState owner, int index, int glCap) {
        super(glCap);
        this.owner = owner;
        this.index = index;
    }

    @Override
    protected void onEnabledChanged() {
        if (isEnabled()) {
            owner.clipPlaneEnabledMask |= (1 << index);
        } else {
            owner.clipPlaneEnabledMask &= ~(1 << index);
        }
    }
}
