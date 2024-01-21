package me.jellysquid.mods.sodium.proxy;

import com.gtnewhorizons.angelica.config.AngelicaConfig;
import cpw.mods.fml.common.event.FMLInitializationEvent;
import cpw.mods.fml.common.event.FMLPostInitializationEvent;
import cpw.mods.fml.common.event.FMLPreInitializationEvent;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import jss.notfine.gui.GuiCustomMenu;
import me.flashyreese.mods.reeses_sodium_options.client.gui.ReeseSodiumVideoOptionsScreen;
import me.jellysquid.mods.sodium.client.gui.SodiumGameOptionPages;
import me.jellysquid.mods.sodium.client.gui.SodiumOptionsGUI;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiVideoSettings;
import net.minecraftforge.client.event.GuiScreenEvent;
import net.minecraftforge.common.MinecraftForge;

public class ClientProxy extends CommonProxy {

    @Override
    public void preInit(FMLPreInitializationEvent event) {
//        FMLCommonHandler.instance().bus().register(this);
        MinecraftForge.EVENT_BUS.register(this);
    }

    @SubscribeEvent
    public void onGui(GuiScreenEvent.InitGuiEvent.Pre event) {
        if(event.gui instanceof GuiVideoSettings eventGui) {
            event.setCanceled(true);
            if(GuiScreen.isShiftKeyDown()) {
                Minecraft.getMinecraft().displayGuiScreen(new GuiCustomMenu(eventGui.parentGuiScreen, SodiumGameOptionPages.general(),
                    SodiumGameOptionPages.quality(), SodiumGameOptionPages.advanced(), SodiumGameOptionPages.performance()));
            } else if(!AngelicaConfig.enableReesesSodiumOptions || GuiScreen.isCtrlKeyDown()) {
                Minecraft.getMinecraft().displayGuiScreen(new SodiumOptionsGUI(eventGui.parentGuiScreen));
            } else {
                Minecraft.getMinecraft().displayGuiScreen(new ReeseSodiumVideoOptionsScreen(eventGui.parentGuiScreen));
            }
        }
    }

    @Override
    public void init(FMLInitializationEvent event) {
        // Nothing to do here (yet)
    }

    @Override
    public void postInit(FMLPostInitializationEvent event) {
        // Nothing to do here (yet)
    }



}
