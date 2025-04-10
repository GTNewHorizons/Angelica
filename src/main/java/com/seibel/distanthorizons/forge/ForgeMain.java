/*
 *    This file is part of the Distant Horizons mod
 *    licensed under the GNU LGPL v3 License.
 *
 *    Copyright (C) 2020 James Seibel
 *
 *    This program is free software: you can redistribute it and/or modify
 *    it under the terms of the GNU Lesser General Public License as published by
 *    the Free Software Foundation, version 3.
 *
 *    This program is distributed in the hope that it will be useful,
 *    but WITHOUT ANY WARRANTY; without even the implied warranty of
 *    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *    GNU Lesser General Public License for more details.
 *
 *    You should have received a copy of the GNU Lesser General Public License
 *    along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.seibel.distanthorizons.forge;

import com.gtnewhorizons.angelica.config.AngelicaConfig;
import com.seibel.distanthorizons.common.AbstractModInitializer;
import com.seibel.distanthorizons.core.api.internal.ServerApi;
import com.seibel.distanthorizons.core.dependencyInjection.SingletonInjector;
import com.seibel.distanthorizons.core.wrapperInterfaces.misc.IPluginPacketSender;
import com.seibel.distanthorizons.core.wrapperInterfaces.modAccessor.IModChecker;
import com.seibel.distanthorizons.coreapi.ModInfo;

import com.seibel.distanthorizons.forge.wrappers.modAccessor.ModChecker;
import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.Mod;
import cpw.mods.fml.common.event.FMLInitializationEvent;
import cpw.mods.fml.common.event.FMLServerAboutToStartEvent;
import cpw.mods.fml.common.event.FMLServerStoppingEvent;
import cpw.mods.fml.common.network.NetworkCheckHandler;
import cpw.mods.fml.relauncher.Side;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.World;
import net.minecraftforge.common.ForgeChunkManager;
import net.minecraftforge.common.MinecraftForge;

import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Initialize and setup the Mod. <br>
 * If you are looking for the real start of the mod
 * check out the ClientProxy.
 */
@Mod(modid = "distanthorizons", name = "DistantHorizons")
public class ForgeMain extends AbstractModInitializer
{
    @Mod.Instance
    public static Object instance;

    public static boolean isHodgePodgeInstalled;

    @Mod.EventHandler
    public void init(FMLInitializationEvent event) {
        if (!AngelicaConfig.enableDistantHorizons)
            return;

        if (FMLCommonHandler.instance().getEffectiveSide().isClient()) {
            this.onInitializeClient();
        }
        else
        {
            this.onInitializeServer();
        }
        ForgeChunkManager.setForcedChunkLoadingCallback(instance, (List<ForgeChunkManager.Ticket> tickets, World world) -> chunkLoadedCallback());
    }

    private void chunkLoadedCallback()
    {

    }

    // ServerWorldLoadEvent
    @Mod.EventHandler
    public void dedicatedWorldLoadEvent(FMLServerAboutToStartEvent event)
    {
        if (!AngelicaConfig.enableDistantHorizons)
            return;

        ServerApi.INSTANCE.serverLoadEvent(event.getServer().isDedicatedServer());
    }

    // ServerWorldUnloadEvent
    @Mod.EventHandler
    public void serverWorldUnloadEvent(FMLServerStoppingEvent event)
    {
        if (!AngelicaConfig.enableDistantHorizons)
            return;

        ServerApi.INSTANCE.serverUnloadEvent();
    }

    @Override
	protected void createInitialBindings()
	{
		SingletonInjector.INSTANCE.bind(IModChecker.class, ModChecker.INSTANCE);
		SingletonInjector.INSTANCE.bind(IPluginPacketSender.class, new ForgePluginPacketSender());
	}

	@Override
	protected IEventProxy createClientProxy() { return new ForgeClientProxy(); }

	@Override
	protected IEventProxy createServerProxy(boolean isDedicated) { return new ForgeServerProxy(isDedicated); }

	@Override
	protected void initializeModCompat()
	{
        /* TODO
		ModLoadingContext.get().registerExtensionPoint(ExtensionPoint.CONFIGGUIFACTORY,
				() -> (client, parent) -> GetConfigScreen.getScreen(parent));
		*/
	}

	@Override
	protected void subscribeClientStartedEvent(Runnable eventHandler)
	{
		// FIXME What event is this?
	}

    @Mod.EventHandler
    public void onServerAboutToStart(FMLServerAboutToStartEvent event) {
        if (eventHandlerStartServer != null)
            eventHandlerStartServer.accept(event.getServer());
    }

    Consumer<MinecraftServer> eventHandlerStartServer;

	@Override
	protected void subscribeServerStartingEvent(Consumer<MinecraftServer> eventHandler)
	{
        eventHandlerStartServer = eventHandler;
	}

	@Override
	protected void runDelayedSetup() { SingletonInjector.INSTANCE.runDelayedSetup(); }

    @NetworkCheckHandler
    public boolean checkNetwork(Map<String, String> map, Side side) {
        if (side == Side.SERVER) {
            isHodgePodgeInstalled = map.containsKey("hodgepodge");
        }
        return true;
    }

}
