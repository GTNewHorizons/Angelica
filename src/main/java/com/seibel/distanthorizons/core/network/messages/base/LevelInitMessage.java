package com.seibel.distanthorizons.core.network.messages.base;

import com.google.common.base.MoreObjects;
import com.seibel.distanthorizons.core.network.messages.AbstractNetworkMessage;
import io.netty.buffer.ByteBuf;

public class LevelInitMessage extends AbstractNetworkMessage
{
	public static final int MAX_LENGTH = 150;
	
	public static final String PART_ALLOWED_CHARS_REGEX = "a-zA-Z0-9-_";
	
	// prefix@namespace:path
	// 1-150 characters in total, all parts except namespace can be omitted
	public static final String VALIDATION_REGEX = String.format("^(?=.{1,%s}$)([%s]+@)?[%s]+(:[%s]+)?$",
			MAX_LENGTH, PART_ALLOWED_CHARS_REGEX, PART_ALLOWED_CHARS_REGEX, PART_ALLOWED_CHARS_REGEX);
	
	
	public String levelKey;
	public long serverTime;
	
	
	
	//==============//
	// constructors //
	//==============//
	
	public LevelInitMessage() { }
	public LevelInitMessage(String levelKey)
	{
		this.levelKey = levelKey;
		this.serverTime = System.currentTimeMillis();
	}
	
	
	
	//===============//
	// serialization //
	//===============//
	
	@Override
	public void encode(ByteBuf out)
	{
		this.writeString(this.levelKey, out);
		out.writeLong(this.serverTime);
	}
	
	@Override
	public void decode(ByteBuf in)
	{
		this.levelKey = this.readString(in);
		this.serverTime = in.readLong();
	}
	
	
	
	//================//
	// base overrides //
	//================//
	
	@Override
	public MoreObjects.ToStringHelper toStringHelper()
	{
		return super.toStringHelper()
				.add("levelKey", this.levelKey)
				.add("serverTime", this.serverTime);
	}
	
}