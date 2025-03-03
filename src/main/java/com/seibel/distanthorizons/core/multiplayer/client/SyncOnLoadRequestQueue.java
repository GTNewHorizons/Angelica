package com.seibel.distanthorizons.core.multiplayer.client;

import com.seibel.distanthorizons.core.config.Config;
import com.seibel.distanthorizons.core.generation.RemoteWorldRetrievalQueue;
import com.seibel.distanthorizons.core.level.DhClientLevel;
import com.seibel.distanthorizons.core.pos.DhSectionPos;
import com.seibel.distanthorizons.core.pos.blockPos.DhBlockPos2D;

/** 
 * This queue only handles LOD updates for
 * LODs that were changed when the player wasn't online
 * and the player already loaded the LODs once.
 * {@link RemoteWorldRetrievalQueue} is used for all other requests.
 * 
 * @see Config.Server#synchronizeOnLoad
 * @see RemoteWorldRetrievalQueue
 */
public class SyncOnLoadRequestQueue extends AbstractFullDataNetworkRequestQueue
{
	//=============//
	// constructor //
	//=============//
	
	public SyncOnLoadRequestQueue(DhClientLevel level, ClientNetworkState networkState)
	{ super(networkState, level, true, Config.Client.Advanced.Debugging.DebugWireframe.showNetworkSyncOnLoadQueue); }
	
	
	
	//=========//
	// getters //
	//=========//
	
	@Override
	protected int getRequestRateLimit() { return this.networkState.sessionConfig.getSyncOnLoginRateLimit(); }
	@Override
	protected boolean isSectionAllowedToGenerate(long sectionPos, DhBlockPos2D targetPos)
	{
		return DhSectionPos.getChebyshevSignedBlockDistance(sectionPos, targetPos) <= this.networkState.sessionConfig.getMaxSyncOnLoadDistance() * 16;
	}
	
	@Override
	protected String getQueueName() { return "Sync On Login Queue"; }
	
	
	
	//==================//
	// request handling //
	//==================//
	
	@Override
	public boolean tick(DhBlockPos2D targetPos)
	{
		if (!this.networkState.sessionConfig.getSynchronizeOnLoad())
		{
			return false;
		}
		
		return super.tick(targetPos);
	}
	
	
	
}
