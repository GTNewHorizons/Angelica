package com.seibel.distanthorizons.core.network.event.internal;

/**
 * This event is received instead of a message if its protocol version is incompatible with version the mod uses.
 */
public class IncompatibleMessageInternalEvent extends AbstractInternalEvent
{
	public final int protocolVersion;
	
	public IncompatibleMessageInternalEvent(int protocolVersion)
	{
		this.protocolVersion = protocolVersion;
	}
	
}