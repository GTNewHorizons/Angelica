package com.gtnewhorizons.angelica.mixins.interfaces;

import org.joml.FrustumIntersection;

public interface ClippingHelperExt {
    FrustumIntersection celeritas$getFrustumIntersection();
    void celeritas$updateFrustumIntersection();
}
