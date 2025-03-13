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

package com.seibel.distanthorizons.api;

import com.seibel.distanthorizons.api.interfaces.events.IDhApiEventInjector;
import com.seibel.distanthorizons.api.interfaces.factories.IDhApiWrapperFactory;
import com.seibel.distanthorizons.api.interfaces.override.IDhApiOverrideable;
import com.seibel.distanthorizons.api.interfaces.override.worldGenerator.IDhApiWorldGeneratorOverrideRegister;
import com.seibel.distanthorizons.api.interfaces.render.IDhApiCustomRenderObjectFactory;
import com.seibel.distanthorizons.api.interfaces.render.IDhApiRenderProxy;
import com.seibel.distanthorizons.api.interfaces.world.IDhApiLevelWrapper;
import com.seibel.distanthorizons.api.methods.events.abstractEvents.DhApiAfterDhInitEvent;
import com.seibel.distanthorizons.api.methods.override.DhApiWorldGeneratorOverrideRegister;
import com.seibel.distanthorizons.coreapi.ModInfo;
import com.seibel.distanthorizons.api.interfaces.config.IDhApiConfig;
import com.seibel.distanthorizons.api.interfaces.world.IDhApiWorldProxy;
import com.seibel.distanthorizons.coreapi.DependencyInjection.ApiEventInjector;
import com.seibel.distanthorizons.coreapi.DependencyInjection.OverrideInjector;
import com.seibel.distanthorizons.api.interfaces.data.IDhApiTerrainDataRepo;
import com.seibel.distanthorizons.coreapi.interfaces.dependencyInjection.IOverrideInjector;

/**
 * This is the masthead of the API, almost everything you could want to do
 * can be achieved from here. <br>
 * For example: you can access singletons which handle the config or event binding. <br><br>
 *
 * <strong>Q:</strong> Why should I use this class instead of just getting the API singleton I need? <br>
 * <strong>A:</strong> This way there is a lower chance of your code breaking if we change something on our end.
 * For example, if we realized there is a much better way of handling dependency injection we would keep the
 * interface the same so your code doesn't have to change. Whereas if you were directly referencing
 * the concrete object we replaced, there would be issues.
 *
 * @author James Seibel
 * @version 2023-12-16
 * @since API 1.0.0
 */
public class DhApi
{
	/** 
	 * If you can see this Java Doc, this can be ignored. <br>
	 * This is just to let you know that Javadocs are available and that you should use the API jar
	 * instead of the full mod jar. <br><br>
	 * 
	 * Note: Don't use this string in your code. It may change and is only for reference.
	 */
	public static final String READ_ME = 
			"If you don't see Javadocs something is wrong. \n" +
			"If you are only using the full DH Mod in your build script, you won't have access to our javadocs and could potentially call into unsafe code. \n" +
			"\n" +
			"Please use the API jar in your build script as a compile time dependency " +
			"and the full DH jar as a runtime dependency. \n" +
			"\n" +
			"Please refer to the example API project or the DH Developer Wiki for additional information " +
			"and suggested setup. \n" + // DH Dev note: no links were included to prevent link rot. 
			"";
	public static String readMe() { return READ_ME; }
	
	/**
	 * This is just a humorous way to reference the {@link DhApi#READ_ME} constant string and hopefully peak a few people's attention
	 * vs the relatively boring "readMe".
	 */
	public static final String HEY_YOU_YOURE_FINALLY_AWAKE = READ_ME;
	/** 
	 * This is just a humorous way to reference the {@link DhApi#READ_ME} constant string and hopefully peak a few people's attention
	 * vs the relatively boring "readMe".
	 */
	public static String heyYou_YoureFinallyAwake() { return READ_ME; } 
	
	
	
	/**
	 * <strong>WARNING:</strong>
	 * All objects in this class will be null until after DH initializes for the first time. <br><br>
	 *
	 * Bind a custom {@link DhApiAfterDhInitEvent DhApiAfterDhInitEvent}
	 * to {@link DhApi#events ApiCoreInjectors.events} in order to be notified when this class can
	 * be safely used.
	 *
	 * @since API 1.0.0
	 */
	public static class Delayed
	{
		/** 
		 * Used to interact with Distant Horizons' Configs.
		 * @since API 1.0.0 
		 */
		public static IDhApiConfig configs = null;
		
		/**
		 * Used to interact with Distant Horizons' terrain data.
		 * Designed to be used in conjunction with {@link DhApi.Delayed#worldProxy}.
		 * @since API 1.0.0
		 */
		public static IDhApiTerrainDataRepo terrainRepo = null;
		
		/**
		 * Used to interact with Distant Horizons' currently loaded world.
		 * Designed to be used in conjunction with {@link DhApi.Delayed#terrainRepo}.
		 * @since API 1.0.0
		 */
		public static IDhApiWorldProxy worldProxy = null;
		
		/** 
		 * Used to interact with Distant Horizons' rendering system.
		 * @since API 1.0.0
		 */
		public static IDhApiRenderProxy renderProxy = null;
		
		/** 
		 * Used to create wrappers for Minecraft objects needed by other Distant Horizons API methods.
		 * @since API 2.0.0
		 */
		public static IDhApiWrapperFactory wrapperFactory = null;
		
		/**
		 * Used to create custom renderable objects. <br>
		 * These objects can be added to the renderer in {@link IDhApiLevelWrapper}
		 * @since API 3.0.0 
		 */
		public static IDhApiCustomRenderObjectFactory customRenderObjectFactory = null;
	}
	
	
	
	//==================//
	// always available //
	//==================//
	
	// interfaces //
	
	/** 
	 * Used to bind/unbind Distant Horizons Api events. 
	 * @since API 1.0.0 
	 */
	public static final IDhApiEventInjector events = ApiEventInjector.INSTANCE;
	
	/** 
	 * Used to bind/unbind Distant Horizons Api events. 
	 * @since API 1.0.0 
	 */
	public static final IDhApiWorldGeneratorOverrideRegister worldGenOverrides = DhApiWorldGeneratorOverrideRegister.INSTANCE;
	
	/** 
	 * Used to bind overrides to change Distant Horizons' core behavior. 
	 * @since API 1.0.0 
	 */
	public static final IOverrideInjector<IDhApiOverrideable> overrides = OverrideInjector.INSTANCE;
	
	
	// getters //
	
	/** 
	 * This version should only be updated when breaking changes are introduced to the Distant Horizons API.
	 * @since API 1.0.0
	 */
	public static int getApiMajorVersion() { return ModInfo.API_MAJOR_VERSION; }
	/** 
	 * This version should be updated whenever new methods are added to the Distant Horizons API. 
	 * @since API 1.0.0 
	 */
	public static int getApiMinorVersion() { return ModInfo.API_MINOR_VERSION; }
	/** 
	 * This version should be updated whenever non-breaking fixes are added to the Distant Horizons API. 
	 * @since API 1.0.0 
	 */
	public static int getApiPatchVersion() { return ModInfo.API_PATCH_VERSION; }
	
	/**
	 * Returns the mod's semantic version number in the format: Major.Minor.Patch
	 * with optional extensions "-a" for alpha, "-b" for beta, and -dev for unstable development builds. <br>
	 * Examples: "1.6.9-a", "1.7.0-a-dev", "2.1.0-b", "3.0.0", "3.1.4-dev"
	 * @since API 1.0.0
	 */
	public static String getModVersion() { return ModInfo.VERSION; }
	/** 
	 * Returns true if the mod is a development version, false if it is a release version. 
	 * @since API 1.0.0 
	 */
	public static boolean getIsDevVersion() { return ModInfo.IS_DEV_BUILD; }
	
	/** 
	 * Returns the network protocol version. 
	 * @since API 1.0.0 
	 */
	public static int getNetworkProtocolVersion() { return ModInfo.PROTOCOL_VERSION; }
	
	
	// methods //
	
	/**
	 * Returns true if the thread this method was called from is owned by Distant Horizons.
	 * @since API 2.0.0
	 */
	public static boolean isDhThread() { return Thread.currentThread().getName().startsWith(ModInfo.THREAD_NAME_PREFIX); }
	
}
