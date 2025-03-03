package com.seibel.distanthorizons.core.multiplayer.fullData;

import com.google.common.base.MoreObjects;
import com.seibel.distanthorizons.api.enums.config.EDhApiDataCompressionMode;
import com.seibel.distanthorizons.core.config.Config;
import com.seibel.distanthorizons.core.dataObjects.fullData.sources.FullDataSourceV2;
import com.seibel.distanthorizons.core.network.INetworkObject;
import com.seibel.distanthorizons.core.network.messages.fullData.FullDataSplitMessage;
import com.seibel.distanthorizons.core.sql.dto.BeaconBeamDTO;
import com.seibel.distanthorizons.core.sql.dto.FullDataSourceV2DTO;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.buffer.UnpooledByteBufAllocator;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

/**
 * @see FullDataSplitMessage
 */
public class FullDataPayload implements INetworkObject, AutoCloseable
{
	private static final AtomicInteger lastBufferId = new AtomicInteger();
	
	public int dtoBufferId;
	public ByteBuf dtoBuffer;
	
	public List<BeaconBeamDTO> beaconBeams;
	
	
	
	//==============//
	// constructors //
	//==============//
	
	public FullDataPayload() { }
	public FullDataPayload(@NotNull FullDataSourceV2 fullDataSource, List<BeaconBeamDTO> beaconBeams)
	{
		Objects.requireNonNull(fullDataSource);
		
		this.dtoBufferId = lastBufferId.getAndIncrement();
		
		try
		{
			EDhApiDataCompressionMode compressionMode = Config.Common.LodBuilding.dataCompression.get();
			try (FullDataSourceV2DTO dataSourceDto = FullDataSourceV2DTO.CreateFromDataSource(fullDataSource, compressionMode))
			{
				// TODO this.dtoBuffer = ByteBufAllocator.DEFAULT.buffer();
                this.dtoBuffer = UnpooledByteBufAllocator.DEFAULT.buffer();
				dataSourceDto.encode(this.dtoBuffer);
			}
		}
		catch (IOException e)
		{
			throw new RuntimeException(e);
		}
		
		this.beaconBeams = beaconBeams;
	}
	
	
	
	//===============//
	// serialization //
	//===============//
	
	@Override
	public void encode(ByteBuf out)
	{
		out.writeInt(this.dtoBufferId);
		this.writeCollection(out, this.beaconBeams);
	}
	
	@Override
	public void decode(ByteBuf in)
	{
		this.dtoBufferId = in.readInt();
		this.beaconBeams = this.readCollection(in, new ArrayList<>(), () -> new BeaconBeamDTO(null, null));
	}
	
	
	
	//================//
	// base overrides //
	//================//
	
	@Override
	public void close()
	{
		this.dtoBuffer.release();
	}
	
	@Override
	public String toString()
	{
		return MoreObjects.toStringHelper(this)
				.add("dtoBufferId", this.dtoBufferId)
				.add("dtoBuffer", this.dtoBuffer)
				.add("beaconBeams", this.beaconBeams)
				.toString();
	}
	
}
