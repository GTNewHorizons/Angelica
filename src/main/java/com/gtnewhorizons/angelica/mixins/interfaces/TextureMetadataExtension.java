package com.gtnewhorizons.angelica.mixins.interfaces;

import com.gtnewhorizons.angelica.utils.MipmapStrategy;

public interface TextureMetadataExtension {

    void angelica$setMipmapStrategy(MipmapStrategy strategy);

    MipmapStrategy angelica$getMipmapStrategy();
}
