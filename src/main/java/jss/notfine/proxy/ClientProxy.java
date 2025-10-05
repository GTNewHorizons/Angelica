package jss.notfine.proxy;

import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.event.FMLPostInitializationEvent;
import cpw.mods.fml.common.event.FMLPreInitializationEvent;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import jss.notfine.config.NotFineConfig;
import jss.notfine.core.Settings;
import jss.notfine.core.SettingsManager;
import net.minecraft.client.Minecraft;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.world.WorldEvent;

public class ClientProxy extends CommonProxy {

    @Override
    public void preInit(FMLPreInitializationEvent event) {
        FMLCommonHandler.instance().bus().register(this);
        MinecraftForge.EVENT_BUS.register(this);

        if(!NotFineConfig.allowAdvancedOpenGL) {
            Minecraft.getMinecraft().gameSettings.advancedOpengl = false;
        }
        if(!NotFineConfig.allowToggle3DAnaglyph) {
            Minecraft.getMinecraft().gameSettings.anaglyph = false;
        }
        if(!NotFineConfig.allowToggleFBO) {
            Minecraft.getMinecraft().gameSettings.fboEnable = true;
        }

        for(Settings setting : Settings.values()) {
            setting.ready();
        }
    }

    @Override
    public void postInit(FMLPostInitializationEvent event) {
        SettingsManager.settingsFile.loadSettings();
    }

    @SubscribeEvent
    public void worldLoad(WorldEvent.Load event) {
        SettingsManager.graphicsUpdated();
    }
}
