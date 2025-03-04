package com.seibel.distanthorizons.common;

import com.mojang.brigadier.CommandDispatcher;
import com.seibel.distanthorizons.api.methods.events.abstractEvents.DhApiAfterDhInitEvent;
import com.seibel.distanthorizons.api.methods.events.abstractEvents.DhApiBeforeDhInitEvent;
import com.seibel.distanthorizons.common.commands.CommandInitializer;
import com.seibel.distanthorizons.common.wrappers.DependencySetup;
import com.seibel.distanthorizons.common.wrappers.minecraft.MinecraftServerWrapper;
import com.seibel.distanthorizons.core.api.internal.ClientApi;
import com.seibel.distanthorizons.core.api.internal.SharedApi;
import com.seibel.distanthorizons.core.config.Config;
import com.seibel.distanthorizons.core.config.ConfigBase;
import com.seibel.distanthorizons.core.config.eventHandlers.presets.ThreadPresetConfigEventHandler;
import com.seibel.distanthorizons.core.dependencyInjection.ModAccessorInjector;
import com.seibel.distanthorizons.core.dependencyInjection.SingletonInjector;
import com.seibel.distanthorizons.core.jar.ModJarInfo;
import com.seibel.distanthorizons.core.jar.updater.SelfUpdater;
import com.seibel.distanthorizons.core.logging.DhLoggerBuilder;
import com.seibel.distanthorizons.core.wrapperInterfaces.modAccessor.IModAccessor;
import com.seibel.distanthorizons.core.wrapperInterfaces.modAccessor.IModChecker;
import com.seibel.distanthorizons.coreapi.DependencyInjection.ApiEventInjector;
import com.seibel.distanthorizons.coreapi.ModInfo;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.dedicated.DedicatedServer;
import org.apache.logging.log4j.Logger;

import java.lang.invoke.MethodHandles;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Base for all mod loader initializers
 * and handles most setup.
 */
public abstract class AbstractModInitializer
{
	protected static final Logger LOGGER = DhLoggerBuilder.getLogger(MethodHandles.lookup().lookupClass().getSimpleName());




	//==================//
	// abstract methods //
	//==================//

	protected abstract void createInitialBindings();
	protected abstract IEventProxy createClientProxy();
	protected abstract IEventProxy createServerProxy(boolean isDedicated);
	protected abstract void initializeModCompat();

	protected abstract void subscribeClientStartedEvent(Runnable eventHandler);
	protected abstract void subscribeServerStartingEvent(Consumer<MinecraftServer> eventHandler);
	protected abstract void runDelayedSetup();



	//===================//
	// initialize events //
	//===================//

	public void onInitializeClient()
	{
		DependencySetup.createClientBindings();

		LOGGER.info("Initializing " + ModInfo.READABLE_NAME + " client.");
		ApiEventInjector.INSTANCE.fireAllEvents(DhApiBeforeDhInitEvent.class, null);

		this.startup();
		this.logBuildInfo();

		this.createClientProxy().registerEvents();
		this.createServerProxy(false).registerEvents();

		this.initializeModCompat();

		LOGGER.info(ModInfo.READABLE_NAME + " client Initialized.");
		ApiEventInjector.INSTANCE.fireAllEvents(DhApiAfterDhInitEvent.class, null);

		// Client uses config for auto-updater, so it's initialized here instead of post-init stage
		this.initConfig();
		logModIncompatibilityWarnings(); // needs to be called after config loading

		this.subscribeClientStartedEvent(this::postInit);
	}

	public void onInitializeServer()
	{
		DependencySetup.createServerBindings();

		LOGGER.info("Initializing " + ModInfo.READABLE_NAME + " server.");
		ApiEventInjector.INSTANCE.fireAllEvents(DhApiBeforeDhInitEvent.class, null);

		this.startup();
		this.logBuildInfo();

		// This prevents returning uninitialized Config values,
		// resulting from a circular reference mid-initialization in a static class
		// noinspection ResultOfMethodCallIgnored
		ThreadPresetConfigEventHandler.INSTANCE.toString();

		this.createServerProxy(true).registerEvents();

		this.initializeModCompat();

		LOGGER.info(ModInfo.READABLE_NAME + " server Initialized.");
		ApiEventInjector.INSTANCE.fireAllEvents(DhApiAfterDhInitEvent.class, null);

		this.subscribeServerStartingEvent(server ->
		{
			MinecraftServerWrapper.INSTANCE.dedicatedServer = (DedicatedServer)server;

			this.initConfig();
			this.postInit();

			this.checkForUpdates();

			LOGGER.info("Dedicated server initialized");
		});
	}



	//===========================//
	// inner initializer methods //
	//===========================//

	private void startup()
	{
		DependencySetup.createSharedBindings();
		SharedApi.init();
		this.createInitialBindings();
	}

	private void logBuildInfo()
	{
		LOGGER.info(ModInfo.READABLE_NAME + ", Version: " + ModInfo.VERSION);

		// if the build is stable the branch/commit/etc shouldn't be needed
		if (ModInfo.IS_DEV_BUILD)
		{
			LOGGER.info("DH Branch: " + ModJarInfo.Git_Branch);
			LOGGER.info("DH Commit: " + ModJarInfo.Git_Commit);
			LOGGER.info("DH Jar Build Source: " + ModJarInfo.Build_Source);
		}
	}

	protected <T extends IModAccessor> void tryCreateModCompatAccessor(String modId, Class<? super T> accessorClass, Supplier<T> accessorConstructor)
	{
		IModChecker modChecker = SingletonInjector.INSTANCE.get(IModChecker.class);
		if (modChecker.isModLoaded(modId))
		{
			//noinspection unchecked
			ModAccessorInjector.INSTANCE.bind((Class<? extends IModAccessor>) accessorClass, accessorConstructor.get());
		}
	}

	private void initConfig()
	{
		ConfigBase.INSTANCE = new ConfigBase(ModInfo.ID, ModInfo.NAME, Config.class, ModInfo.CONFIG_FILE_VERSION);
		Config.completeDelayedSetup();
	}

	private void checkForUpdates()
	{
		if (Config.Client.Advanced.AutoUpdater.enableAutoUpdater.get())
		{
			if (Config.Client.Advanced.AutoUpdater.enableSilentUpdates.get())
			{
				LOGGER.info("Silent updates are not allowed for dedicated servers; force disabling.");
				Config.Client.Advanced.AutoUpdater.enableSilentUpdates.set(false);
			}

			SelfUpdater.onStart();
		}
	}

	private void postInit()
	{
		LOGGER.info("Post-Initializing Mod");
		this.runDelayedSetup();
		LOGGER.info("Mod Post-Initialized");
	}



	//==================================//
	// mod partial compatibility checks //
	//==================================//

	/**
	 * Some mods will work with a few tweaks
	 * or will partially work but have some known issues we can't solve.
	 * This method will log (and display to chat if enabled)
	 * these warnings and potential fixes.
	 */
	private static void logModIncompatibilityWarnings()
	{
		boolean showChatWarnings = Config.Common.Logging.Warning.showModCompatibilityWarningsOnStartup.get();
		IModChecker modChecker = SingletonInjector.INSTANCE.get(IModChecker.class);

		String startingString = "Partially Incompatible Distant Horizons mod detected: ";



		// Alex's caves
		if (modChecker.isModLoaded("alexscaves"))
		{
			// There've been a few reports about this mod breaking DH at a few different points in time
			// the fixes for said breakage changes depending on the version so unfortunately
			// all we can do is log a warning so the user can handle it.

			if (showChatWarnings)
			{
				String message =
						// orange text
						"\u00A76" + "Distant Horizons: Alex's Cave detected." + "\u00A7r\n" +
								"You may have to change Alex's config for DH to render. ";
				ClientApi.INSTANCE.showChatMessageNextFrame(message);
			}

			LOGGER.warn(startingString + "[Alex's Caves] may require some config changes in order to render Distant Horizons correctly.");
		}

		// William Wythers' Overhauled Overworld (WWOO)
		if (modChecker.isModLoaded("wwoo"))
		{
			// WWOO has a bug with it's world gen that can't be fixed by DH or WWOO
			// (at least that is what James learned after talking with WWOO)
			// WWOO will cause grid lines to appear in the world when DH generates the chunks
			// this might be due to how WWOO uses features for everything when generating
			// and said features don't always get to the edge of said chunks.

			String wwooWarning = "LODs generated by DH may have grid lines between sections. Disabling either WWOO or DH's distant generator will fix the problem.";

			if (showChatWarnings)
			{
				String message =
						// orange text
						"\u00A76" + "Distant Horizons: WWOO detected." + "\u00A7r\n" +
								wwooWarning;
				ClientApi.INSTANCE.showChatMessageNextFrame(message);
			}

			LOGGER.warn(startingString + "[WWOO] "+ wwooWarning);
		}

		// Chunky
		boolean chunkyPresent = false;
		try
		{
			Class.forName("org.popcraft.chunky.api.ChunkyAPI");
			chunkyPresent = true;
		}
		catch (ClassNotFoundException ignore) { }

		if (chunkyPresent)
		{
			// Chunky can generate chunks faster than DH can process them,
			// causing holes in the LODs.
			// Generally it's better and faster to use DH's world generator.

			String chunkyWarning = "Chunky can cause DH LODs to have holes " +
					"since Chunky can generate chunks faster than DH can process them. \n" +
					"Using DH's distant generator instead of chunky or increasing DH's CPU thread count can resolve the issue.";

			if (showChatWarnings)
			{
				String message =
						// orange text
						"\u00A76" + "Distant Horizons: Chunky detected." + "\u00A7r\n" +
								chunkyWarning;
				ClientApi.INSTANCE.showChatMessageNextFrame(message);
			}

			LOGGER.warn(startingString + "[Chunky] "+ chunkyWarning);
		}

	}



	//================//
	// helper classes //
	//================//

	public interface IEventProxy
	{
		void registerEvents();
	}

}
