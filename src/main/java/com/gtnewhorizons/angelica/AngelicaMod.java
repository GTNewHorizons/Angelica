package com.gtnewhorizons.angelica;

import com.gtnewhorizons.angelica.client.gui.AngelicaVideoSettings;
import cpw.mods.fml.common.Mod;
import cpw.mods.fml.common.event.FMLPreInitializationEvent;
import cpw.mods.fml.relauncher.Side;
import jss.notfine.gui.GuiCustomMenu;
import jss.notfine.gui.ISettingsEnum;
import jss.notfine.gui.MenuButtonLists;

@Mod(
        modid = "angelica",
        name = "Angelica",
        version = Tags.VERSION,
        acceptedMinecraftVersions = "[1.7.10]",
        acceptableRemoteVersions = "*")
public class AngelicaMod {

    @Mod.EventHandler
    public void preInit(FMLPreInitializationEvent event) {
        if (event.getSide() == Side.CLIENT) {
            MenuButtonLists.addAdditionalEntry(MenuButtonLists.VIDEO, AngelicaVideoSettings.SHADERS);
            GuiCustomMenu.addButtonHandler(AngelicaVideoSettings.class, (xPosition, yPosition, setting) -> {
                ISettingsEnum settingsEnum = (ISettingsEnum) setting;
                return settingsEnum.createButton(xPosition, yPosition, setting);
            });
        }
    }
}
