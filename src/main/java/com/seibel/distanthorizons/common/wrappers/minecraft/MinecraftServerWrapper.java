package com.seibel.distanthorizons.common.wrappers.minecraft;

import com.seibel.distanthorizons.core.wrapperInterfaces.minecraft.IMinecraftSharedWrapper;
import net.minecraft.server.dedicated.DedicatedServer;

import java.io.File;

//@Environment(EnvType.SERVER)
public class MinecraftServerWrapper implements IMinecraftSharedWrapper
{
	public static final MinecraftServerWrapper INSTANCE = new MinecraftServerWrapper();
	
	public DedicatedServer dedicatedServer = null;
	
	
	//=============//
	// constructor //
	//=============//
	
	private MinecraftServerWrapper() { }
	
	
	
	//=========//
	// methods //
	//=========//
	
	@Override
	public boolean isDedicatedServer() { return true; }
	
	@Override
	public File getInstallationDirectory()
	{
		if (this.dedicatedServer == null)
		{
			throw new IllegalStateException("Trying to get Installation Direction before Dedicated server completed initialization!");
		}
		
		#if MC_VER < MC_1_21_1
		return this.dedicatedServer.getServerDirectory();
		#else
		return this.dedicatedServer.getServerDirectory().toFile();
		#endif
	}
	
	@Override
	public int getPlayerCount()
	{
		return this.dedicatedServer.getPlayerCount();
	}
	
}
