package com.seibel.distanthorizons.core.network.messages;

import com.google.common.base.MoreObjects;
import com.seibel.distanthorizons.core.network.INetworkObject;
import com.seibel.distanthorizons.core.network.session.NetworkSession;
import com.seibel.distanthorizons.core.wrapperInterfaces.misc.IServerPlayerWrapper;

/** Any new implementing classes should be registered in {@link MessageRegistry} */
public abstract class AbstractNetworkMessage implements INetworkObject
{
	//============//
	// properties //
	//============//
	
	private NetworkSession networkSession = null;
	public NetworkSession getSession() { return this.networkSession; }
	public void setSession(NetworkSession networkSession) { this.networkSession = networkSession; }
	
	public IServerPlayerWrapper serverPlayer() { return this.networkSession.serverPlayer; }
	
	
	
	//================//
	// base overrides //
	//================//
	
	@Override
	public String toString() { return this.toStringHelper().toString(); }
	public MoreObjects.ToStringHelper toStringHelper() { return MoreObjects.toStringHelper(this); }
	
}