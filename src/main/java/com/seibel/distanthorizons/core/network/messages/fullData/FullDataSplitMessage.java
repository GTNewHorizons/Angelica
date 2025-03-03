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

package com.seibel.distanthorizons.core.network.messages.fullData;

import com.google.common.base.MoreObjects;
import com.seibel.distanthorizons.core.multiplayer.fullData.FullDataPayload;
import com.seibel.distanthorizons.core.network.messages.AbstractNetworkMessage;
import com.seibel.distanthorizons.core.util.TimerUtil;
import io.netty.buffer.ByteBuf;

import java.util.Timer;

/**
 * Used to send part of a {@link FullDataPayload}.
 * 
 * @see FullDataPayload
 */
public class FullDataSplitMessage extends AbstractNetworkMessage
{
	private static final long BUFFER_RELEASE_DELAY_MS = 5000L;
	
	public int bufferId;
	public ByteBuf buffer;
	public boolean isFirst;
	
	// Reference counting is unreliable here for some reason so this is a "fix"
	private static final Timer bufferReleaseTimer = TimerUtil.CreateTimer("FullDataBufferCleanupTimer");
	private boolean releaseScheduled = false;
	
	
	//==============//
	// constructors //
	//==============//
	
	public FullDataSplitMessage() { }
	public FullDataSplitMessage(int bufferId, ByteBuf buffer, boolean isFirst)
	{
		this.bufferId = bufferId;
		this.buffer = buffer;
		this.isFirst = isFirst;
	}
	
	
	
	//===============//
	// serialization //
	//===============//
	
	@Override
	public void encode(ByteBuf out)
	{
		out.writeInt(this.bufferId);
		
		out.writeInt(this.buffer.writerIndex());
		out.writeBytes(this.buffer.readerIndex(0));

		out.writeBoolean(this.isFirst);
		
		if (!this.releaseScheduled)
		{
			bufferReleaseTimer.schedule(TimerUtil.createTimerTask(this.buffer::release), BUFFER_RELEASE_DELAY_MS);
			this.releaseScheduled = true;
		}
	}
	
	@Override
	public void decode(ByteBuf in)
	{
		this.bufferId = in.readInt();
		
		int bufferSize = in.readInt();
		this.buffer = in.readBytes(bufferSize);

		this.isFirst = in.readBoolean();
	}
	
	
	
	//================//
	// base overrides //
	//================//
	
	@Override
	public MoreObjects.ToStringHelper toStringHelper()
	{
		return super.toStringHelper()
				.add("bufferId", this.bufferId)
				.add("buffer", this.buffer)
				.add("isFirst", this.isFirst);
	}
	
}