package com.gtnewhorizons.angelica.mixins.interfaces;

import cpw.mods.fml.client.registry.ISimpleBlockRenderingHandler;

public interface IRenderingRegistryExt {
    ISimpleBlockRenderingHandler getISBRH(int modelId);
}
