package com.gtnewhorizons.angelica.rendering.tesr;

import com.gtnewhorizons.angelica.glsm.ffp.Instancing;

public interface TesrInstancingPipeline {

    void rebindCurrentPass();

    boolean hasInstancedVariant(Instancing kind);

    void bindInstancedVariant(Instancing kind);
}
