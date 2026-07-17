package com.gtnewhorizons.angelica.mixins.interfaces;

import org.joml.FrustumIntersection;
import org.joml.Matrix4fc;

public interface ClippingHelperExt {
    FrustumIntersection celeritas$getFrustumIntersection();
    void celeritas$updateFrustumIntersection();

    Matrix4fc celeritas$getCombinedMatrix();
}
