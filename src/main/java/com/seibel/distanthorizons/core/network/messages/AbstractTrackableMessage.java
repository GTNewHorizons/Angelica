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

package com.seibel.distanthorizons.core.network.messages;

import com.google.common.base.MoreObjects;
import com.seibel.distanthorizons.core.api.internal.SharedApi;
import com.seibel.distanthorizons.core.network.messages.requests.ExceptionMessage;
import com.seibel.distanthorizons.core.network.session.NetworkSession;
import com.seibel.distanthorizons.core.util.LodUtil;
import com.seibel.distanthorizons.core.world.EWorldEnvironment;
import io.netty.buffer.ByteBuf;

import java.util.concurrent.atomic.AtomicInteger;

public abstract class AbstractTrackableMessage extends AbstractNetworkMessage
{
	/** Tracks every message we've sent */
	private static final AtomicInteger LAST_MESSAGE_ID_REF = new AtomicInteger();
	
	/**
	 * 32 bits - NetworkSession ID (not transmitted) <br>
	 * 1 bit - Requesting side (client - 0, server - 1) <br>
	 * 31 bits - Request/Message ID <br><br>
	 * 
	 * SI = NetworkSession ID <br>
	 * CS = Client/Server flag <br>
	 * MI = Request/Message ID <br><br>
	 * 
	 * <code>
	 * =======Bit layout=======	<br>
	 * SI SI SI SI  SI SI SI SI <-- Top bits <br>
	 * SI SI SI SI  SI SI SI SI	<br>
	 * SI SI SI SI  SI SI SI SI	<br>
	 * SI SI SI SI  SI SI SI SI	<br>
	 * CS MI MI MI  MI MI MI MI	<br>
	 * MI MI MI MI  MI MI MI MI	<br>
	 * MI MI MI MI  MI MI MI MI	<br>
	 * MI MI MI MI  MI MI MI MI <-- Bottom bits	<br>
	 * </code>
	 */
	public long futureId;
	
	
	
	//=============//
	// constructor //
	//=============//
	
	public AbstractTrackableMessage()
	{
		EWorldEnvironment worldEnvironment = SharedApi.getEnvironment();
		LodUtil.assertTrue(worldEnvironment != null, "Message can't be created if no world is loaded.");
		
		
		// message/Request ID written as the least significant bits
		long id = LAST_MESSAGE_ID_REF.getAndIncrement();
		// write requesting side at bit 32
		id |= ((worldEnvironment == EWorldEnvironment.SERVER_ONLY) ? 1 : 0) << 31;
		this.futureId = id;
	}
	
	
	
	//==================//
	// abstract methods //
	//==================//
	
	protected abstract void encodeInternal(ByteBuf out) throws Exception;
	protected abstract void decodeInternal(ByteBuf in) throws Exception;
	
	
	
	//=================//
	// getters/setters //
	//=================//
	
	@Override
    public void setSession(NetworkSession networkSession)
	{
		super.setSession(networkSession);
		// Session ID is written in the most significant bits
		this.futureId |= (long) networkSession.id << 32;
	}
	
	
	
	//==============//
	// send message //
	//==============//
	
	public void sendResponse(AbstractTrackableMessage responseMessage)
	{
		responseMessage.futureId = this.futureId;
		this.getSession().sendMessage(responseMessage);
	}
	public void sendResponse(Exception e) { this.sendResponse(new ExceptionMessage(e)); }
	
	
	
	//=============//
	// serializing //
	//=============//
	
	@Override
	public final void encode(ByteBuf out)
	{
		try
		{
			out.writeInt((int) this.futureId);
			this.encodeInternal(out);
		}
		catch (Exception e)
		{
			throw new RuntimeException(e);
		}
	}
	
	@Override
	public final void decode(ByteBuf in)
	{
		try
		{
			this.futureId = in.readInt();
			this.decodeInternal(in);
		}
		catch (Exception e)
		{
			throw new RuntimeException(e);
		}
	}
	
	
	
	//================//
	// base overrides //
	//================//
	
	@Override 
	public MoreObjects.ToStringHelper toStringHelper()
	{
		return super.toStringHelper()
				.add("futureId", this.futureId);
	}
	
}