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

package com.seibel.distanthorizons.core.api.internal;

import com.seibel.distanthorizons.api.DhApi;
import com.seibel.distanthorizons.api.enums.config.EDhApiMcRenderingFadeMode;
import com.seibel.distanthorizons.api.enums.rendering.EDhApiRenderPass;
import com.seibel.distanthorizons.api.methods.events.abstractEvents.*;
import com.seibel.distanthorizons.api.methods.events.sharedParameterObjects.DhApiRenderParam;
import com.seibel.distanthorizons.core.file.structure.ClientOnlySaveStructure;
import com.seibel.distanthorizons.core.network.messages.MessageRegistry;
import com.seibel.distanthorizons.core.pos.DhChunkPos;
import com.seibel.distanthorizons.core.render.DhApiRenderProxy;
import com.seibel.distanthorizons.core.render.renderer.FadeRenderer;
import com.seibel.distanthorizons.core.util.TimerUtil;
import com.seibel.distanthorizons.core.util.objects.Pair;
import com.seibel.distanthorizons.core.wrapperInterfaces.minecraft.IMinecraftRenderWrapper;
import com.seibel.distanthorizons.coreapi.DependencyInjection.ApiEventInjector;
import com.seibel.distanthorizons.core.level.IDhClientLevel;
import com.seibel.distanthorizons.core.config.Config;
import com.seibel.distanthorizons.core.network.messages.AbstractNetworkMessage;
import com.seibel.distanthorizons.core.network.session.NetworkSession;
import com.seibel.distanthorizons.coreapi.ModInfo;
import com.seibel.distanthorizons.api.enums.rendering.EDhApiDebugRendering;
import com.seibel.distanthorizons.api.enums.rendering.EDhApiRendererMode;
import com.seibel.distanthorizons.core.dependencyInjection.SingletonInjector;
import com.seibel.distanthorizons.core.level.IServerKeyedClientLevel;
import com.seibel.distanthorizons.core.logging.ConfigBasedLogger;
import com.seibel.distanthorizons.core.logging.ConfigBasedSpamLogger;
import com.seibel.distanthorizons.core.logging.SpamReducedLogger;
import com.seibel.distanthorizons.core.util.math.Mat4f;
import com.seibel.distanthorizons.core.render.glObject.GLProxy;
import com.seibel.distanthorizons.core.render.renderer.TestRenderer;
import com.seibel.distanthorizons.core.util.RenderUtil;
import com.seibel.distanthorizons.core.world.AbstractDhWorld;
import com.seibel.distanthorizons.core.world.DhClientServerWorld;
import com.seibel.distanthorizons.core.world.DhClientWorld;
import com.seibel.distanthorizons.core.world.IDhClientWorld;
import com.seibel.distanthorizons.core.wrapperInterfaces.chunk.IChunkWrapper;
import com.seibel.distanthorizons.core.wrapperInterfaces.minecraft.IMinecraftClientWrapper;
import com.seibel.distanthorizons.core.wrapperInterfaces.minecraft.IProfilerWrapper;
import com.seibel.distanthorizons.core.wrapperInterfaces.world.IClientLevelWrapper;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.lwjgl.glfw.GLFW;

import java.io.File;
import java.util.*;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * This holds the methods that should be called
 * by the host mod loader (Fabric, Forge, etc.).
 * Specifically for the client.
 */
public class ClientApi
{
	private static final Logger LOGGER = LogManager.getLogger();
	
	public static boolean prefLoggerEnabled = false;
	
	public static final ClientApi INSTANCE = new ClientApi();
	public static final TestRenderer TEST_RENDERER = new TestRenderer();
	
	private static final IMinecraftClientWrapper MC_CLIENT = SingletonInjector.INSTANCE.get(IMinecraftClientWrapper.class);
	private static final IMinecraftRenderWrapper MC_RENDER = SingletonInjector.INSTANCE.get(IMinecraftRenderWrapper.class);
	
	public static final long SPAM_LOGGER_FLUSH_NS = TimeUnit.NANOSECONDS.convert(1, TimeUnit.SECONDS);
	
	/** this includes the is dev build message and low allocated memory warning */
	private static final int MS_BETWEEN_STATIC_STARTUP_MESSAGES = 4_000;
	
	
	
	private boolean isDevBuildMessagePrinted = false;
	private boolean lowMemoryWarningPrinted = false;
	private boolean highVanillaRenderDistanceWarningPrinted = false;
	
	/** when the last static */
	private long lastStaticWarningMessageSentMsTime = 0L;
	
	private final Queue<String> chatMessageQueueForNextFrame = new LinkedBlockingQueue<>();
	private final Queue<String> overlayMessageQueueForNextFrame = new LinkedBlockingQueue<>();
	
	public boolean rendererDisabledBecauseOfExceptions = false;
	
	private long lastFlushNanoTime = 0;
	
	private final ClientPluginChannelApi pluginChannelApi = new ClientPluginChannelApi(this::clientLevelLoadEvent, this::clientLevelUnloadEvent);
	
	/** Delay loading the first level to give the server some time to respond with level to actually load */
	private Timer firstLevelLoadTimer;
	private static final long FIRST_LEVEL_LOAD_DELAY_IN_MS = 1_000;
	
	/** Holds any levels that were loaded before the {@link ClientApi#onClientOnlyConnected} was fired. */
	public final HashSet<IClientLevelWrapper> waitingClientLevels = new HashSet<>();
	/** Holds any chunks that were loaded before the {@link ClientApi#clientLevelLoadEvent(IClientLevelWrapper)} was fired. */
	public final HashMap<Pair<IClientLevelWrapper, DhChunkPos>, IChunkWrapper> waitingChunkByClientLevelAndPos = new HashMap<>();
	
	/** re-set every frame during the opaque rendering stage */
	private boolean renderingCancelledForThisFrame;
	
	
	
	//==============//
	// constructors //
	//==============//
	
	private ClientApi() { }
	
	
	
	//==============//
	// world events //
	//==============//
	
	/**
	 * May be fired slightly before or after the associated
	 * {@link ClientApi#clientLevelLoadEvent(IClientLevelWrapper)} event
	 * depending on how the host mod loader functions. <br><br>
	 * 
	 * Synchronized shouldn't be necessary, but is present to match {@see onClientOnlyDisconnected} and prevent any unforeseen issues. 
	 */
	public synchronized void onClientOnlyConnected()
	{
		// only continue if the client is connected to a different server
		boolean connectedToServer = MC_CLIENT.clientConnectedToDedicatedServer();
		boolean connectedToReplay = MC_CLIENT.connectedToReplay();
		if (connectedToServer || connectedToReplay)
		{
			if (connectedToServer)
			{
				LOGGER.info("Client on ClientOnly mode connecting.");
			}
			else
			{
				LOGGER.info("Replay on ClientServer mode connecting.");
				
				if (Config.Common.Logging.Warning.showReplayWarningOnStartup.get())
				{
					MC_CLIENT.sendChatMessage("\u00A76" + "Distant Horizons: Replay detected." + "\u00A7r"); // gold color
					MC_CLIENT.sendChatMessage("DH may behave strangely or have missing functionality.");
					MC_CLIENT.sendChatMessage("In order to use pre-generated LODs, put your DH database(s) in:");
					MC_CLIENT.sendChatMessage("\u00A77"+".Minecraft" + File.separator + ClientOnlySaveStructure.SERVER_DATA_FOLDER_NAME + File.separator + ClientOnlySaveStructure.REPLAY_SERVER_FOLDER_NAME + File.separator + "DIMENSION_NAME"+"\u00A7r"); // light gray color
					MC_CLIENT.sendChatMessage("This can be disabled in DH's config under Advanced -> Logging.");
					MC_CLIENT.sendChatMessage("");
				}
			}
			
			// firing after clientLevelLoadEvent
			// TODO if level has prepped to load it should fire level load event
			DhClientWorld world = new DhClientWorld();
			SharedApi.setDhWorld(world);
			
			this.pluginChannelApi.onJoinServer(world.networkState.getSession());
			world.networkState.sendConfigMessage();
			
			LOGGER.info("Loading [" + this.waitingClientLevels.size() + "] waiting client level wrappers.");
			for (IClientLevelWrapper level : this.waitingClientLevels)
			{
				this.clientLevelLoadEvent(level);
			}
			
			this.waitingClientLevels.clear();
		}
	}
	
	/** Synchronized to prevent a rare issue where multiple disconnect events are triggered on top of each other. */
	public synchronized void onClientOnlyDisconnected()
	{
		// clear the first time timer
		if (this.firstLevelLoadTimer != null)
		{
			this.firstLevelLoadTimer.cancel();
			this.firstLevelLoadTimer = null;
		}
		
		AbstractDhWorld world = SharedApi.getAbstractDhWorld();
		if (world != null)
		{
			LOGGER.info("Client on ClientOnly mode disconnecting.");
			
			world.close();
			SharedApi.setDhWorld(null);
		}
		
		this.pluginChannelApi.reset();
		
		// remove any waiting items
		this.waitingChunkByClientLevelAndPos.clear();
		this.waitingClientLevels.clear();
	}
	
	
	
	//==============//
	// level events //
	//==============//
	
	public void clientLevelUnloadEvent(IClientLevelWrapper level)
	{
		try
		{
			LOGGER.info("Unloading client level [" + level.getClass().getSimpleName() + "]-[" + level.getDhIdentifier() + "].");
			
			if (level instanceof IServerKeyedClientLevel)
			{
				this.pluginChannelApi.onClientLevelUnload();
			}
			
			AbstractDhWorld world = SharedApi.getAbstractDhWorld();
			if (world != null)
			{
				world.unloadLevel(level);
				SharedApi.INSTANCE.clearQueuedChunkUpdates();
				ApiEventInjector.INSTANCE.fireAllEvents(DhApiLevelUnloadEvent.class, new DhApiLevelUnloadEvent.EventParam(level));
			}
			else
			{
				this.waitingClientLevels.remove(level);
			}
		}
		catch (Exception e)
		{
			// handle errors here to prevent blowing up a mixin or API up stream
			LOGGER.error("Unexpected error in ClientApi.clientLevelUnloadEvent(), error: "+e.getMessage(), e);
		}
	}
	
	public void clientLevelLoadEvent(IClientLevelWrapper levelWrapper)
	{
		// wait a moment before loading the level to give the server a chance to handle the client's login request
		if (MC_CLIENT.clientConnectedToDedicatedServer())
		{
			if (this.firstLevelLoadTimer == null)
			{
				this.firstLevelLoadTimer = TimerUtil.CreateTimer("FirstLevelLoadTimer");
				this.firstLevelLoadTimer.schedule(new TimerTask()
				{
					@Override
					public void run() { ClientApi.this.clientLevelLoadEvent(levelWrapper); }
				}, FIRST_LEVEL_LOAD_DELAY_IN_MS);
				return;
			}
			this.firstLevelLoadTimer.cancel();
		}
		
		
		try
		{
			LOGGER.info("Loading client level [" + levelWrapper + "]-[" + levelWrapper.getDhIdentifier() + "].");
			
			AbstractDhWorld world = SharedApi.getAbstractDhWorld();
			if (world != null)
			{
				if (!this.pluginChannelApi.allowLevelLoading(levelWrapper))
				{
					LOGGER.info("Levels in this connection are managed by the server, skipping auto-load.");
					
					// Instead of attempting to load themselves, send the config and wait for a server provided level key.
					((DhClientWorld) world).networkState.sendConfigMessage();
					return;
				}
				
				
				world.getOrLoadLevel(levelWrapper);
				ApiEventInjector.INSTANCE.fireAllEvents(DhApiLevelLoadEvent.class, new DhApiLevelLoadEvent.EventParam(levelWrapper));
				
				this.loadWaitingChunksForLevel(levelWrapper);
			}
			else
			{
				this.waitingClientLevels.add(levelWrapper);
			}
		}
		catch (Exception e)
		{
			// handle errors here to prevent blowing up a mixin or API up stream
			LOGGER.error("Unexpected error in ClientApi.clientLevelLoadEvent(), error: "+e.getMessage(), e);
		}
	}
	private void loadWaitingChunksForLevel(IClientLevelWrapper level)
	{
		HashSet<Pair<IClientLevelWrapper, DhChunkPos>> keysToRemove = new HashSet<>();
		for (Pair<IClientLevelWrapper, DhChunkPos> levelChunkPair : this.waitingChunkByClientLevelAndPos.keySet())
		{
			// only load chunks that came from this level
			IClientLevelWrapper levelWrapper = levelChunkPair.first;
			if (levelWrapper.equals(level))
			{
				IChunkWrapper chunkWrapper = this.waitingChunkByClientLevelAndPos.get(levelChunkPair);
				SharedApi.INSTANCE.chunkLoadEvent(chunkWrapper, levelWrapper);
				keysToRemove.add(levelChunkPair);
			}
		}
		LOGGER.info("Loaded [" + keysToRemove.size() + "] waiting chunk wrappers.");
		
		for (Pair<IClientLevelWrapper, DhChunkPos> keyToRemove : keysToRemove)
		{
			this.waitingChunkByClientLevelAndPos.remove(keyToRemove);
		}
	}
	
	
	
	//===============//
	// render events //
	//===============//
	
	public void clientTickEvent()
	{
		IProfilerWrapper profiler = MC_CLIENT.getProfiler();
		profiler.push("DH-ClientTick");
		
		try
		{
			boolean doFlush = System.nanoTime() - this.lastFlushNanoTime >= SPAM_LOGGER_FLUSH_NS;
			if (doFlush)
			{
				this.lastFlushNanoTime = System.nanoTime();
				SpamReducedLogger.flushAll();
			}
			ConfigBasedLogger.updateAll();
			ConfigBasedSpamLogger.updateAll(doFlush);
			
			IDhClientWorld clientWorld = SharedApi.getIDhClientWorld();
			if (clientWorld != null)
			{
				clientWorld.clientTick();
				
				// Ignore local world gen, as it's managed by server ticking
				if (!(clientWorld instanceof DhClientServerWorld))
				{
					SharedApi.worldGenTick(clientWorld::worldGenTick);
				}
			}
		}
		catch (Exception e)
		{
			// handle errors here to prevent blowing up a mixin or API up stream
			LOGGER.error("Unexpected error in ClientApi.clientTickEvent(), error: "+e.getMessage(), e);
		}
		
		profiler.pop();
	}
	
	
	
	//============//
	// networking //
	//============//
	
	/**
	 * Forwards a decoded message into the registered handlers.
	 *
	 * @see MessageRegistry
	 */
	public void pluginMessageReceived(@NotNull AbstractNetworkMessage message)
	{
		NetworkSession networkSession = this.pluginChannelApi.networkSession;
		if (networkSession != null)
		{
			networkSession.tryHandleMessage(message);
		}
	}
	
	
	
	//===========//
	// rendering //
	//===========//
	
	/** Should be called before {@link ClientApi#renderDeferredLods} */
	public void renderLods(IClientLevelWrapper levelWrapper, Mat4f mcModelViewMatrix, Mat4f mcProjectionMatrix, float partialTicks)
	{ this.renderLodLayer(levelWrapper, mcModelViewMatrix, mcProjectionMatrix, partialTicks, false); }
	
	/** 
	 * Only necessary when Shaders are in use.
	 * Should be called after {@link ClientApi#renderLods} 
	 */
	public void renderDeferredLods(IClientLevelWrapper levelWrapper, Mat4f mcModelViewMatrix, Mat4f mcProjectionMatrix, float partialTicks)
	{ this.renderLodLayer(levelWrapper, mcModelViewMatrix, mcProjectionMatrix, partialTicks, true); }
	
	private void renderLodLayer(
			IClientLevelWrapper levelWrapper, Mat4f mcModelViewMatrix, Mat4f mcProjectionMatrix, float partialTicks,
			boolean renderingDeferredLayer)
	{
		// logging //
		
		this.sendQueuedChatMessages();
		
		IProfilerWrapper profiler = MC_CLIENT.getProfiler();
		profiler.pop(); // get out of "terrain"
		profiler.push("DH-RenderLevel");
		
		
		
		// render parameter setup //
		
		EDhApiRenderPass renderPass;
		if (DhApiRenderProxy.INSTANCE.getDeferTransparentRendering())
		{
			if (renderingDeferredLayer)
			{
				renderPass = EDhApiRenderPass.TRANSPARENT;
			}
			else
			{
				renderPass = EDhApiRenderPass.OPAQUE;
			}
		}
		else
		{
			renderPass = EDhApiRenderPass.OPAQUE_AND_TRANSPARENT;
		}
		
		DhApiRenderParam renderEventParam =
				new DhApiRenderParam(
						renderPass,
						partialTicks,
						RenderUtil.getNearClipPlaneDistanceInBlocks(partialTicks), RenderUtil.getFarClipPlaneDistanceInBlocks(),
						mcProjectionMatrix, mcModelViewMatrix,
						RenderUtil.createLodProjectionMatrix(mcProjectionMatrix, partialTicks), RenderUtil.createLodModelViewMatrix(mcModelViewMatrix),
						levelWrapper.getMinHeight()
				);
		
		
		
		// render validation //
		
		try
		{
			// TODO write this message to the F3 menu so people can see when a different mod screws with the lightmap
			String reasonLodsCannotRender = RenderUtil.shouldLodsRender(levelWrapper, renderEventParam);
			if (reasonLodsCannotRender != null)
			{
				return;
			}
			
			IDhClientWorld dhClientWorld = SharedApi.getIDhClientWorld();
			if (dhClientWorld == null)
			{
				return;
			}
			
			IDhClientLevel level = (IDhClientLevel) dhClientWorld.getLevel(levelWrapper);
			if (level == null)
			{
				return;
			}
			
			
			if (this.rendererDisabledBecauseOfExceptions)
			{
				// re-enable rendering if the user toggles DH rendering
				if (!Config.Client.quickEnableRendering.get())
				{
					LOGGER.info("DH Renderer re-enabled after exception. Some rendering issues may occur. Please reboot Minecraft if you see any rendering issues.");
					this.rendererDisabledBecauseOfExceptions = false;
					Config.Client.quickEnableRendering.set(true);
				}
				
				return;
			}
			
			
			
			// render pass //
			
			if (!renderingDeferredLayer)
			{
				if (Config.Client.Advanced.Debugging.rendererMode.get() == EDhApiRendererMode.DEFAULT)
				{
					this.renderingCancelledForThisFrame = ApiEventInjector.INSTANCE.fireAllEvents(DhApiBeforeRenderEvent.class, renderEventParam);
					if (!this.renderingCancelledForThisFrame)
					{
						level.render(renderEventParam, profiler);
					}
					
					if (!DhApi.Delayed.renderProxy.getDeferTransparentRendering())
					{
						ApiEventInjector.INSTANCE.fireAllEvents(DhApiAfterRenderEvent.class, null);
					}
				}
				else if (Config.Client.Advanced.Debugging.rendererMode.get() == EDhApiRendererMode.DEBUG)
				{
					profiler.push("Render Debug");
					ClientApi.TEST_RENDERER.render();
					profiler.pop();
				}
			}
			else
			{
				boolean renderingCancelled = ApiEventInjector.INSTANCE.fireAllEvents(DhApiBeforeDeferredRenderEvent.class, renderEventParam);
				if (!renderingCancelled)
				{
					level.renderDeferred(renderEventParam, profiler);
				}
				
				
				if (DhApi.Delayed.renderProxy.getDeferTransparentRendering())
				{
					ApiEventInjector.INSTANCE.fireAllEvents(DhApiAfterRenderEvent.class, null);
				}
			}
		}
		catch (Exception e)
		{
			this.rendererDisabledBecauseOfExceptions = true;
			LOGGER.error("Unexpected Renderer error in render pass [" + renderPass + "]. Error: " + e.getMessage(), e);
			
			MC_CLIENT.sendChatMessage("\u00A74\u00A7l\u00A7uERROR: Distant Horizons renderer has encountered an exception!");
			MC_CLIENT.sendChatMessage("\u00A74Renderer disabled to try preventing GL state corruption.");
			MC_CLIENT.sendChatMessage("\u00A74Toggle DH rendering via the config UI to re-activate DH rendering.");
			MC_CLIENT.sendChatMessage("\u00A74Error: " + e);
		}
		finally
		{
			try
			{
				// these tasks always need to be called, regardless of whether the renderer is enabled or not to prevent memory leaks
				GLProxy.getInstance().runRenderThreadTasks();
			}
			catch (Exception e)
			{
				LOGGER.error("Unexpected issue running render thread tasks.", e);
			}
			
			
			profiler.pop(); // end LOD
			profiler.push("terrain"); // go back into "terrain"
		}
	}
	
	/** should be called after DH and MC finish rendering so we can smooth the transition between the two */
	public void renderFadeOpaque(Mat4f mcModelViewMatrix, Mat4f mcProjectionMatrix, float partialTicks, IClientLevelWrapper level)
	{
		// only fade when DH is rendering
		if (Config.Client.Advanced.Debugging.rendererMode.get() == EDhApiRendererMode.DEFAULT)
		{
			if (Config.Client.Advanced.Graphics.Quality.vanillaFadeMode.get() == EDhApiMcRenderingFadeMode.DOUBLE_PASS)
			{
				FadeRenderer.INSTANCE.render(mcModelViewMatrix, mcProjectionMatrix, partialTicks, level);
			}
		}
	}
	/** should be called after DH and MC finish rendering so we can smooth the transition between the two */
	public void renderFade(Mat4f mcModelViewMatrix, Mat4f mcProjectionMatrix, float partialTicks, IClientLevelWrapper level)
	{
		// only fade when DH is rendering
		if (Config.Client.Advanced.Debugging.rendererMode.get() == EDhApiRendererMode.DEFAULT)
		{
			// fade if any level fading is active
			if (Config.Client.Advanced.Graphics.Quality.vanillaFadeMode.get() != EDhApiMcRenderingFadeMode.NONE)
			{
				FadeRenderer.INSTANCE.render(mcModelViewMatrix, mcProjectionMatrix, partialTicks, level);
			}
		}
	}
	
	
	
	
	//=================//
	//    DEBUG USE    //
	//=================//
	
	/** Trigger once on key press, with CLIENT PLAYER. */
	public void keyPressedEvent(int glfwKey)
	{
		if (!Config.Client.Advanced.Debugging.enableDebugKeybindings.get())
		{
			// keybindings are disabled
			return;
		}
		
		
		if (glfwKey == GLFW.GLFW_KEY_F8)
		{
			Config.Client.Advanced.Debugging.debugRendering.set(EDhApiDebugRendering.next(Config.Client.Advanced.Debugging.debugRendering.get()));
			MC_CLIENT.sendChatMessage("F8: Set debug mode to " + Config.Client.Advanced.Debugging.debugRendering.get());
		}
		else if (glfwKey == GLFW.GLFW_KEY_F6)
		{
			Config.Client.Advanced.Debugging.rendererMode.set(EDhApiRendererMode.next(Config.Client.Advanced.Debugging.rendererMode.get()));
			MC_CLIENT.sendChatMessage("F6: Set rendering to " + Config.Client.Advanced.Debugging.rendererMode.get());
		}
		else if (glfwKey == GLFW.GLFW_KEY_P)
		{
			prefLoggerEnabled = !prefLoggerEnabled;
			MC_CLIENT.sendChatMessage("P: Debug Pref Logger is " + (prefLoggerEnabled ? "enabled" : "disabled"));
		}
	}
	
	private void sendQueuedChatMessages()
	{
		// this includes if the current build is a dev build
		// and configuration warnings (IE Java memory amount and MC settings)
		this.detectAndSendBootTimeWarnings();
		
		// Don't send any generic messages until the static ones have been sent.
		// This makes sure the more critical messages are seen first.
		if (this.staticStartupMessageSentRecently())
		{
			return;
		}
			
		
		// chat messages
		while (!this.chatMessageQueueForNextFrame.isEmpty())
		{
			String message = this.chatMessageQueueForNextFrame.poll();
			if (message == null)
			{
				// done to prevent potential null pointers
				message = "";
			}
			MC_CLIENT.sendChatMessage(message);
		}
		
		// overlay messages
		while (!this.overlayMessageQueueForNextFrame.isEmpty())
		{
			String message = this.overlayMessageQueueForNextFrame.poll();
			if (message == null)
			{
				// done to prevent potential null pointers
				message = "";
			}
			MC_CLIENT.sendOverlayMessage(message);
		}
	}
	private void detectAndSendBootTimeWarnings()
	{
		// dev build
		if (ModInfo.IS_DEV_BUILD && !this.isDevBuildMessagePrinted && MC_CLIENT.playerExists())
		{
			this.isDevBuildMessagePrinted = true;
			this.lastStaticWarningMessageSentMsTime = System.currentTimeMillis();
			
			// remind the user that this is a development build
			String message =
					// green text
					"\u00A72" + "Distant Horizons: nightly/unstable build, version: [" + ModInfo.VERSION+"]." + "\u00A7r\n" +
							"Issues may occur with this version.\n" +
							"Here be dragons!\n";
			MC_CLIENT.sendChatMessage(message);
		}
		
		
		// memory
		if (this.staticStartupMessageSentRecently()) return;
		if (!this.lowMemoryWarningPrinted && Config.Common.Logging.Warning.showLowMemoryWarningOnStartup.get())
		{
			this.lowMemoryWarningPrinted = true;
			this.lastStaticWarningMessageSentMsTime = System.currentTimeMillis();
			
			// 4 GB
			long minimumRecommendedMemoryInBytes = 4L * 1_000_000_000L;
			
			// Java returned 17,171,480,576 for 16 GB so it might be slightly off what you'd expect
			long maxMemoryInBytes = Runtime.getRuntime().maxMemory();
			if (maxMemoryInBytes < minimumRecommendedMemoryInBytes)
			{
				String message =
						// orange text		
						"\u00A76" + "Distant Horizons: Low memory detected." + "\u00A7r \n" +
						"Stuttering or low FPS may occur. \n" +
						"Please increase Minecraft's available memory to 4 GB or more. \n" +
						"This warning can be disabled in DH's config under Advanced -> Logging. \n";
				MC_CLIENT.sendChatMessage(message);
			}
		}
		
		
		// high vanilla render distance
		if (this.staticStartupMessageSentRecently()) return;
		if (!this.highVanillaRenderDistanceWarningPrinted && Config.Common.Logging.Warning.showHighVanillaRenderDistanceWarning.get())
		{
			// DH generally doesn't need a vanilla render distance above 12 
			if (MC_RENDER.getRenderDistance() > 12)
			{
				this.highVanillaRenderDistanceWarningPrinted = true;
				this.lastStaticWarningMessageSentMsTime = System.currentTimeMillis();
				
				String message =
						// yellow text
						"\u00A7e" + "Distant Horizons: High vanilla render distance detected." + "\u00A7r \n" +
						"Using a high vanilla render distance uses a lot of CPU power \n" +
						"and doesn't improve graphics much after about 12.\n" +
						"Lowing your vanilla render distance will give you better FPS\n" +
						"and reduce stuttering at a similar visual quality.\n" +
						// gray text		
						"\u00A77" + "A vanilla render distance of 8 is recommended." + "\u00A7r \n" +
						"This message can be disabled in DH's config under Advanced -> Logging.\n";
				MC_CLIENT.sendChatMessage(message);
			}
		}
	}
	/** done to prevent sending a bunch of startup messages all at once, causing some to be missed. */
	private boolean staticStartupMessageSentRecently()
	{
		if (this.lastStaticWarningMessageSentMsTime == 0)
		{
			return true;
		}
		
		long timeSinceLastMessage = System.currentTimeMillis() - this.lastStaticWarningMessageSentMsTime; 
		return timeSinceLastMessage <= MS_BETWEEN_STATIC_STARTUP_MESSAGES;
	}
	
	
	/** 
	 * Queues the given message to appear in chat the next valid frame.
	 * Useful for queueing up messages that may be triggered before the user has loaded into the world. 
	 */
	public void showChatMessageNextFrame(String chatMessage) { this.chatMessageQueueForNextFrame.add(chatMessage); }
	
	/**
	 * Similar to {@link ClientApi#showChatMessageNextFrame(String)} but appears above the toolbar.
	 */
	public void showOverlayMessageNextFrame(String message) { this.overlayMessageQueueForNextFrame.add(message); }
	
}
