package com.seibel.distanthorizons.common.wrappers.level;

import com.seibel.distanthorizons.common.wrappers.world.ClientLevelWrapper;
import com.seibel.distanthorizons.core.level.IServerKeyedClientLevel;
import com.seibel.distanthorizons.core.wrapperInterfaces.world.ILevelWrapper;
import com.seibel.distanthorizons.coreapi.util.StringUtil;
import net.minecraft.client.multiplayer.ClientLevel;

public class ServerKeyedClientLevel extends ClientLevelWrapper implements IServerKeyedClientLevel
{
	/** A unique identifier (generally the level's name) for differentiating multiverse levels */
	private final String serverLevelKey;
	
	
	
	public ServerKeyedClientLevel(ClientLevel level, String serverLevelKey)
	{
		super(level);
		this.serverLevelKey = serverLevelKey;
	}
	
	
	
	@Override
	public String getServerLevelKey() { return this.serverLevelKey; }
	
	@Override
	public String getDhIdentifier() { return this.getServerLevelKey(); }
	
}
