package com.gtnewhorizons.angelica.client.rendering;

import com.gtnewhorizons.angelica.glsm.GLStateManager;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;

public final class ModelViewDelta {

    private final Matrix4f base = new Matrix4f();
    private final Matrix4f baseInverse = new Matrix4f();

    public void snapshot() {
        base.set(GLStateManager.getModelViewMatrix());
        base.invert(baseInverse);
    }

    public Matrix4fc deltaOrNull(Matrix4f scratch) {
        final Matrix4f current = GLStateManager.getModelViewMatrix();
        if (current.equals(base)) return null;
        return baseInverse.mul(current, scratch);
    }
}
