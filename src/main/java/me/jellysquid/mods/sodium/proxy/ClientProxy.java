package me.jellysquid.mods.sodium.proxy;

import com.gtnewhorizons.angelica.config.AngelicaConfig;
import cpw.mods.fml.common.event.FMLInitializationEvent;
import cpw.mods.fml.common.event.FMLPostInitializationEvent;
import cpw.mods.fml.common.event.FMLPreInitializationEvent;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import jss.notfine.gui.GuiCustomMenu;
import jss.notfine.gui.NotFineGameOptionPages;
import me.flashyreese.mods.reeses_sodium_options.client.gui.ReeseSodiumVideoOptionsScreen;
import com.gtnewhorizons.angelica.client.gui.SodiumOptionsGUI;
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


    @Override
    public void init(FMLInitializationEvent event) {
        // Nothing to do here (yet)
    }

    @Override
    public void postInit(FMLPostInitializationEvent event) {
        // Nothing to do here (yet)
    }



}
