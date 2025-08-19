package com.gtnewhorizons.angelica.mixins.interfaces;

import com.gtnewhorizon.gtnhlib.client.renderer.quad.BakedModel;

public interface ModeledBlock {

    BakedModel getModel();
    void setModel(BakedModel model);
}
