package com.gtnewhorizons.angelica.mixins.interfaces;

import cpw.mods.fml.relauncher.SideOnly;
import net.minecraftforge.client.model.IModelCustom;

import static cpw.mods.fml.relauncher.Side.CLIENT;

public interface IModelCustomExt extends IModelCustom {
    @SideOnly(CLIENT)
    void rebuildVBO();

    @SideOnly(CLIENT)
    void renderAllVBO();
}
