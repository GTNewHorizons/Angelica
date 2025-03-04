package com.seibel.distanthorizons.common.wrappers.level;

import com.seibel.distanthorizons.core.level.IServerKeyedClientLevel;
import com.seibel.distanthorizons.core.level.IKeyedClientLevelManager;
import com.seibel.distanthorizons.core.wrapperInterfaces.world.IClientLevelWrapper;
import net.minecraft.client.multiplayer.WorldClient;
import org.jetbrains.annotations.Nullable;

public class KeyedClientLevelManager implements IKeyedClientLevelManager
{
	public static final KeyedClientLevelManager INSTANCE = new KeyedClientLevelManager();

	/** This is set and managed by the ClientApi for servers with support for DH. */
	@Nullable
	private IServerKeyedClientLevel serverKeyedLevel = null;
	/** Allows to keep level manager enabled between loading different keyed levels */
	private boolean enabled = false;




	//=============//
	// constructor //
	//=============//

	private KeyedClientLevelManager() { }



	//======================//
	// level override logic //
	//======================//

	@Override
	@Nullable
	public IServerKeyedClientLevel getServerKeyedLevel() { return this.serverKeyedLevel; }

	@Override
	public IServerKeyedClientLevel setServerKeyedLevel(IClientLevelWrapper clientLevel, String levelKey)
	{
		IServerKeyedClientLevel keyedLevel = new ServerKeyedClientLevel((WorldClient) clientLevel.getWrappedMcObject(), levelKey);
		this.serverKeyedLevel = keyedLevel;
		this.enabled = true;
		return keyedLevel;
	}

	@Override
	public void clearKeyedLevel() { this.serverKeyedLevel = null; }
	@Override
	public boolean isEnabled() { return this.enabled; }
	@Override
	public void disable() { this.enabled = false; }


}
