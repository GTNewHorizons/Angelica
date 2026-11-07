package com.gtnewhorizons.angelica.glsm;

import com.gtnewhorizons.angelica.glsm.hooks.GLSMHooks;
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
    protected boolean forwardsToBackend() {
        return !RenderSystem.isClipDistanceEmulated();
    }

    @Override
    protected void onEnabledChanged() {
        final int before = owner.clipPlaneEnabledMask;
        if (isEnabled()) {
            owner.clipPlaneEnabledMask |= (1 << index);
        } else {
            owner.clipPlaneEnabledMask &= ~(1 << index);
        }
        owner.clipPlaneGeneration++;
        if ((before == 0) != (owner.clipPlaneEnabledMask == 0) && RenderSystem.isClipDistanceEmulated() && GLSMHooks.CLIP_PLANES_IN_USE.hasListeners()) {
            GLSMHooks.CLIP_PLANES_IN_USE.post(GLSMHooks.clipPlanesInUseEvent);
        }
    }
}
