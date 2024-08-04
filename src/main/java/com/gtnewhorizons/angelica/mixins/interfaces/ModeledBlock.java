package com.gtnewhorizons.angelica.mixins.interfaces;

import com.gtnewhorizon.gtnhlib.client.renderer.quad.QuadProvider;

public interface ModeledBlock {

    QuadProvider getModel();
    void setModel(QuadProvider model);
}
