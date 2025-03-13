package com.seibel.distanthorizons.core.network.event.internal;

import com.seibel.distanthorizons.core.network.messages.AbstractNetworkMessage;
import io.netty.buffer.ByteBuf;

/** internal events are messages sent from the client/sever back to themselves. */
public abstract class AbstractInternalEvent extends AbstractNetworkMessage
{
	@Override
	public void encode(ByteBuf out)
	{ throw new UnsupportedOperationException(this.getClass().getSimpleName() + " is an internal event, and cannot be sent."); }

	@Override
	public void decode(ByteBuf in)
	{ throw new UnsupportedOperationException(this.getClass().getSimpleName() + " is an internal event, and cannot be received."); }
	
}