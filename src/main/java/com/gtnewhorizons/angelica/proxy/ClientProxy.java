package com.gtnewhorizons.angelica.proxy;

import com.gtnewhorizons.angelica.client.gui.AngelicaEntityRenderDistanceSetting;
import com.gtnewhorizons.angelica.client.gui.AngelicaVideoSettings;
import com.gtnewhorizons.angelica.config.AngelicaConfig;
import cpw.mods.fml.common.event.FMLInitializationEvent;
import cpw.mods.fml.common.event.FMLPreInitializationEvent;
import jss.notfine.gui.GuiCustomMenu;
import jss.notfine.gui.ISettingsEnum;
import jss.notfine.gui.MenuButtonLists;
import me.jellysquid.mods.sodium.client.SodiumDebugScreenHandler;
import net.minecraftforge.common.MinecraftForge;

public class ClientProxy extends CommonProxy {

    @Override
    public void preInit(FMLPreInitializationEvent event) {
        MenuButtonLists.addAdditionalEntry(MenuButtonLists.VIDEO, AngelicaVideoSettings.SHADERS);
        GuiCustomMenu.addButtonHandler(AngelicaVideoSettings.class, (xPosition, yPosition, setting) -> {
            ISettingsEnum settingsEnum = (ISettingsEnum) setting;
            return settingsEnum.createButton(xPosition, yPosition, setting);
        });
        MenuButtonLists.addAdditionalEntry(MenuButtonLists.VIDEO, AngelicaEntityRenderDistanceSetting.ENTITY_RENDER_DISTANCE);
        GuiCustomMenu.addButtonHandler(AngelicaEntityRenderDistanceSetting.class, (xPosition, yPosition, setting) ->{
            ISettingsEnum settingsEnum = (ISettingsEnum) setting;
            return settingsEnum.createButton(xPosition, yPosition, setting);
        });
    }

    @Override
    public void init(FMLInitializationEvent event) {
        // Nothing to do here (yet)
    }

    @Override
    public void postInit(FMLInitializationEvent event) {
        if(AngelicaConfig.enableSodium)
            MinecraftForge.EVENT_BUS.register(SodiumDebugScreenHandler.INSTANCE);
    }
}
