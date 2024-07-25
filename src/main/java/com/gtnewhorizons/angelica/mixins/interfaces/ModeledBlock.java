package com.gtnewhorizons.angelica.mixins.interfaces;

import com.gtnewhorizons.angelica.api.QuadProvider;

public interface ModeledBlock {

    QuadProvider getModel();
    void setModel(QuadProvider model);
}
