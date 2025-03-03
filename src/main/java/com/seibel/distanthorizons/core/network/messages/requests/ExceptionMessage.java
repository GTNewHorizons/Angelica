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

package com.seibel.distanthorizons.core.network.messages.requests;

import com.google.common.base.MoreObjects;
import com.seibel.distanthorizons.core.network.exceptions.RateLimitedException;
import com.seibel.distanthorizons.core.network.exceptions.RequestOutOfRangeException;
import com.seibel.distanthorizons.core.network.exceptions.RequestRejectedException;
import com.seibel.distanthorizons.core.network.exceptions.SectionRequiresSplittingException;
import com.seibel.distanthorizons.core.network.messages.AbstractTrackableMessage;
import io.netty.buffer.ByteBuf;

import java.util.ArrayList;
import java.util.List;

public class ExceptionMessage extends AbstractTrackableMessage
{
	private static final List<Class<? extends Exception>> EXCEPTION_LIST = new ArrayList<Class<? extends Exception>>()
	{{
		// All exceptions here must include constructor: (String)
		this.add(RateLimitedException.class);
		this.add(RequestOutOfRangeException.class);
		this.add(RequestRejectedException.class);
		this.add(SectionRequiresSplittingException.class);
	}};
	
	public Exception exception;
	
	
	
	//==============//
	// constructors //
	//==============//
	
	public ExceptionMessage() { }
	public ExceptionMessage(Exception exception) { this.exception = exception; }
	
	
	
	//===============//
	// serialization //
	//===============//
	
	@Override 
	protected void encodeInternal(ByteBuf out)
	{
		out.writeInt(EXCEPTION_LIST.indexOf(this.exception.getClass()));
		this.writeString(this.exception.getMessage(), out);
	}
	
	@Override 
	protected void decodeInternal(ByteBuf in) throws Exception
	{
		int id = in.readInt();
		String message = this.readString(in);
		this.exception = EXCEPTION_LIST.get(id).getDeclaredConstructor(String.class).newInstance(message);
	}
	
	
	
	//================//
	// base overrides //
	//================//
	
	@Override
	public MoreObjects.ToStringHelper toStringHelper()
	{
		return super.toStringHelper()
				.add("exception", this.exception);
	}
	
}