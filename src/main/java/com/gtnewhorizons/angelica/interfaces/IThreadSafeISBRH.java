package com.gtnewhorizons.angelica.interfaces;

import cpw.mods.fml.client.registry.ISimpleBlockRenderingHandler;

public interface IThreadSafeISBRH extends ISimpleBlockRenderingHandler {
    IThreadSafeISBRH getThreadLocal();

}
