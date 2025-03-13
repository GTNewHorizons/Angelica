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

package com.seibel.distanthorizons.core;

import com.seibel.distanthorizons.core.dependencyInjection.SingletonInjector;
import com.seibel.distanthorizons.core.render.renderer.generic.GenericRenderObjectFactory;
import com.seibel.distanthorizons.core.sql.DatabaseUpdater;
import com.seibel.distanthorizons.core.wrapperInterfaces.IWrapperFactory;
import com.seibel.distanthorizons.core.wrapperInterfaces.minecraft.IMinecraftClientWrapper;
import com.seibel.distanthorizons.coreapi.ModInfo;
import com.seibel.distanthorizons.core.world.DhApiWorldProxy;
import com.seibel.distanthorizons.core.api.external.methods.config.DhApiConfig;
import com.seibel.distanthorizons.core.api.external.methods.data.DhApiTerrainDataRepo;
import com.seibel.distanthorizons.api.DhApi;
import com.seibel.distanthorizons.core.render.DhApiRenderProxy;
import net.jpountz.lz4.LZ4FrameOutputStream;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.sqlite.SQLiteJDBCLoader;
import org.sqlite.util.OSInfo;
import org.tukaani.xz.XZOutputStream;

import java.awt.*;
import java.io.File;

/** Handles first time Core setup. */
public class Initializer
{
	private static final Logger LOGGER = LogManager.getLogger(ModInfo.NAME + "-" + Initializer.class.getSimpleName());
	private static final IMinecraftClientWrapper MC_CLIENT = SingletonInjector.INSTANCE.get(IMinecraftClientWrapper.class);
	
	
	public static void init()
	{
		// confirm that all referenced libraries are available to use
		try
		{
			// if any library isn't present in the jar its class
			// will throw an error (not an exception)
			Class<?> fastCompressor = LZ4FrameOutputStream.class;
			Class<?> smallCompressor = XZOutputStream.class;
			//Class<?> networking = ByteBuf.class;
			Class<?> config = com.electronwill.nightconfig.core.Config.class;
			Class<?> oldFastUtil = it.unimi.dsi.fastutil.longs.LongArrayList.class; // available in 8.2.1
			//Class<?> newFastUtil = it.unimi.dsi.fastutil.ints.IntUnaryOperator.class; // available in 8.5.13
			Class<?> sqliteJava = org.sqlite.SQLiteConnection.class;
			Class<?> sqliteNative = org.sqlite.core.NativeDB.class;
			
			//// maybe these lines are needed to shade SQLite, James isn't sure.
			//// Although they never seemed to fail, which is a bit odd.
			//try
			//{
			//	// needed by Forge to load the Java database connection
			//	Class.forName("org.sqlite.JDBC");
			//	LOGGER.info("loaded normal SQLITE");
			//}
			//catch (ClassNotFoundException e)
			//{
			//	LOGGER.warn("normal: " + e.getMessage(), e);
			//}
			//
			//try
			//{
			//	// needed by Forge to load the Java database connection
			//	Class.forName("DistantHorizons.libraries.sqlite.JDBC");
			//	LOGGER.info("loaded shaded SQLITE");
			//}
			//catch (ClassNotFoundException e)
			//{
			//	LOGGER.warn("shaded: " + e.getMessage(), e);
			//}
			
			boolean sqliteLoaded = SQLiteJDBCLoader.initialize();
			if (!sqliteLoaded)
			{
				throw new RuntimeException("Failed to load SQLite native library. Hopefully SQLite logged a reason for this failure.");
			}
		}
		catch (Throwable e)
		{
			LOGGER.fatal("Critical programmer error: One or more libraries aren't present. Error: [" + e.getMessage() + "].", e);
			// throwing here should crash the game, notifying the developer that something is wrong
			throw new RuntimeException(e);
		}
		
		// confirm the resource directory is present
		try
		{
			int scriptCount = DatabaseUpdater.getAutoUpdateScriptCount();
			if (scriptCount == 0)
			{
				throw new NullPointerException("No auto update scripts found, but no error thrown. This might mean the script list file is corrupted or empty.");
			}
		}
		catch (Exception e)
		{
			LOGGER.fatal("Critical programmer error: Can't read SQL Scripts resource folder is either missing or malformed. Error: [" + e.getMessage() + "].");
			throw new RuntimeException(e);
		}
		
		// This code has been disabled since it can cause Mac
		// to lock up and refuse the load (there's a bug with Java.awt texture loading)
		//if (MC_CLIENT != null)
		//{
		//	// attempt to set up Swing so we can display dialogs (popup windows)
		//	System.setProperty("java.awt.headless", "false");
		//	if (GraphicsEnvironment.isHeadless())
		//	{
		//		LOGGER.warn("Java.awt.headless is false. This means Distant Horizons can't display error and info dialog windows.");
		//	}
		//	else
		//	{
		//		LOGGER.info("Java.awt.headless set to true. Distant Horizons can correctly display error and info dialog windows.");
		//	}
		//}
		
		// link Core's config to the API
		DhApi.Delayed.configs = DhApiConfig.INSTANCE;
		DhApi.Delayed.terrainRepo = DhApiTerrainDataRepo.INSTANCE;
		DhApi.Delayed.worldProxy = DhApiWorldProxy.INSTANCE;
		DhApi.Delayed.renderProxy = DhApiRenderProxy.INSTANCE;
		DhApi.Delayed.customRenderObjectFactory = GenericRenderObjectFactory.INSTANCE;
		DhApi.Delayed.wrapperFactory = SingletonInjector.INSTANCE.get(IWrapperFactory.class);
		if (DhApi.Delayed.wrapperFactory == null)
		{
			LOGGER.error("Programmer Error: No ["+IWrapperFactory.class.getSimpleName()+"] assigned to the DhApi.");
		}
		
	}
	
}
