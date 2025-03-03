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

package com.seibel.distanthorizons.core.network.messages.base;

import com.google.common.base.MoreObjects;
import com.seibel.distanthorizons.core.network.messages.AbstractNetworkMessage;
import io.netty.buffer.ByteBuf;

public class CodecCrashMessage extends AbstractNetworkMessage
{
	public ECrashPhase crashPhase;
	
	
	
	//==============//
	// constructors //
	//==============//
	
	public CodecCrashMessage() { }
	public CodecCrashMessage(ECrashPhase crashPhase) { this.crashPhase = crashPhase; }
	
	
	
	//===============//
	// serialization //
	//===============//
	
	@Override
	public void encode(ByteBuf out)
	{
		if (this.crashPhase == ECrashPhase.ENCODE)
		{
			throw new RuntimeException("encode force crash");
		}
	}

	@Override
	public void decode(ByteBuf in) { throw new RuntimeException("decode force crash"); }
	
	
	
	//================//
	// base overrides //
	//================//
	
	@Override
	public MoreObjects.ToStringHelper toStringHelper()
	{
		return super.toStringHelper()
				.add("crashPhase", this.crashPhase);
	}
	
	
	
	//================//
	// helper classes //
	//================//
	
	/**
	 * ENCODE, <br>
	 * DECODE, <br>
	 */
	public enum ECrashPhase
	{
		ENCODE,
		DECODE
	}
	
}