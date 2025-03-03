package com.seibel.distanthorizons.core.multiplayer.fullData;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.RemovalCause;
import com.google.common.cache.RemovalNotification;
import com.seibel.distanthorizons.core.config.Config;
import com.seibel.distanthorizons.core.logging.ConfigBasedLogger;
import com.seibel.distanthorizons.core.network.INetworkObject;
import com.seibel.distanthorizons.core.network.messages.fullData.FullDataSplitMessage;
import com.seibel.distanthorizons.core.sql.dto.FullDataSourceV2DTO;
import com.seibel.distanthorizons.core.util.LodUtil;
import io.netty.buffer.ByteBufAllocator;
import io.netty.buffer.CompositeByteBuf;
import io.netty.buffer.UnpooledByteBufAllocator;
import org.apache.logging.log4j.LogManager;

import java.util.Objects;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;

public class FullDataPayloadReceiver implements AutoCloseable
{
	private static final ConfigBasedLogger LOGGER = new ConfigBasedLogger(LogManager.getLogger(),
			() -> Config.Common.Logging.logNetworkEvent.get());
	
	private final ConcurrentMap<Integer, CompositeByteBuf> buffersById = CacheBuilder.newBuilder()
			.expireAfterAccess(10, TimeUnit.SECONDS)
			.removalListener((RemovalNotification<Integer, CompositeByteBuf> notification) ->
			{
				// If an entry was replaced without removing, the buffer has to be released manually
				if (notification.getCause() != RemovalCause.REPLACED)
				{
					Objects.requireNonNull(notification.getValue()).release();
				}
			})
			.build().asMap();
	
	
	
	@Override
	public void close()
	{
		this.buffersById.clear();
	}
	
	public void receiveChunk(FullDataSplitMessage message)
	{
		this.buffersById.compute(message.bufferId, (bufferId, composite) ->
		{
			if (message.isFirst)
			{
				if (composite != null)
				{
					composite.release();
					LOGGER.debug("Released existing full data buffer [" + message.bufferId + "]");
				}
				

				// TODO composite = ByteBufAllocator.DEFAULT.compositeBuffer();
                composite = UnpooledByteBufAllocator.DEFAULT.compositeBuffer();
				LOGGER.debug("Created new full data buffer [" + message.bufferId + "]: [" + composite + "]");
			}
			else if (composite == null)
			{
				LOGGER.debug("Received non-first full data chunk for empty buffer [" + message.bufferId + "]: [" + message.buffer + "].");
				return null;
			}
			

			// TODO composite.addComponent(true, message.buffer);
            composite.addComponent(message.buffer);
			LOGGER.debug("Updated full data buffer [" + message.bufferId + "]: [" + composite + "].");
			return composite;
		});
	}
	
	public FullDataSourceV2DTO decodeDataSourceAndReleaseBuffer(FullDataPayload msg)
	{
		CompositeByteBuf compositeByteBuffer = this.buffersById.get(msg.dtoBufferId);
		LodUtil.assertTrue(compositeByteBuffer != null);
		
		try
		{
			return INetworkObject.decodeToInstance(FullDataSourceV2DTO.CreateEmptyDataSourceForDecoding(), compositeByteBuffer);
		}
		finally
		{
			// Releasing the buffer is handled by cache
			this.buffersById.remove(msg.dtoBufferId);
		}
	}
	
}
