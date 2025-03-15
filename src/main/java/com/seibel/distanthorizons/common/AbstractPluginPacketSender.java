package com.seibel.distanthorizons.common;

import com.seibel.distanthorizons.core.config.Config;
import com.seibel.distanthorizons.core.logging.ConfigBasedLogger;
import com.seibel.distanthorizons.core.network.event.internal.IncompatibleMessageInternalEvent;
import com.seibel.distanthorizons.core.network.event.internal.ProtocolErrorInternalEvent;
import com.seibel.distanthorizons.core.network.messages.MessageRegistry;
import com.seibel.distanthorizons.core.network.messages.AbstractNetworkMessage;
import com.seibel.distanthorizons.core.network.messages.base.CloseReasonMessage;
import com.seibel.distanthorizons.core.wrapperInterfaces.misc.IPluginPacketSender;
import com.seibel.distanthorizons.core.wrapperInterfaces.misc.IServerPlayerWrapper;
import com.seibel.distanthorizons.coreapi.ModInfo;
import io.netty.buffer.ByteBufUtil;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import org.apache.logging.log4j.LogManager;

import java.io.IOException;
import java.util.Objects;

public abstract class AbstractPluginPacketSender implements IPluginPacketSender
{
	private static final ConfigBasedLogger LOGGER = new ConfigBasedLogger(LogManager.getLogger(),
			() -> Config.Common.Logging.logNetworkEvent.get());
	
	#if MC_VER >= MC_1_21_1
	public static final ResourceLocation WRAPPER_PACKET_RESOURCE = ResourceLocation.fromNamespaceAndPath(ModInfo.RESOURCE_NAMESPACE, ModInfo.WRAPPER_PACKET_PATH);
	#else
	public static final ResourceLocation WRAPPER_PACKET_RESOURCE = new ResourceLocation(ModInfo.RESOURCE_NAMESPACE, ModInfo.WRAPPER_PACKET_PATH);
	#endif
	
	
	@Override
	public final void sendToClient(IServerPlayerWrapper serverPlayer, AbstractNetworkMessage message)
	{
		this.sendToClient((ServerPlayer) serverPlayer.getWrappedMcObject(), message);
	}
	public abstract void sendToClient(ServerPlayer serverPlayer, AbstractNetworkMessage message);
	
	@Override
	public abstract void sendToServer(AbstractNetworkMessage message);
	
	public static AbstractNetworkMessage decodeMessage(FriendlyByteBuf in)
	{
		AbstractNetworkMessage message = null;
		
		try
		{
			in.markReaderIndex();
			
			int protocolVersion = in.readShort();
			if (protocolVersion != ModInfo.PROTOCOL_VERSION)
			{
				return new IncompatibleMessageInternalEvent(protocolVersion);
			}
			
			message = MessageRegistry.INSTANCE.createMessage(in.readUnsignedShort());
			message.decode(in);
			
			if (in.isReadable())
			{
				throw new IOException("Buffer has not been fully read");
			}
			
			return message;
		}
		catch (Exception e)
		{
			in.resetReaderIndex();
			
			LOGGER.error("Failed to decode message", e);
			LOGGER.error("Buffer: ["+in+"]");
			LOGGER.error("Buffer contents: ["+ByteBufUtil.hexDump(in)+"]");
			
			return new ProtocolErrorInternalEvent(e, message, true);
		}
		finally
		{
			// Prevent connection crashing if not entire buffer has been read
			in.readerIndex(in.writerIndex());
		}
	}
	
	public static void encodeMessage(FriendlyByteBuf out, AbstractNetworkMessage message)
	{
		// This is intentionally unhandled, because errors related to this are unlikely to appear in wild
		Objects.requireNonNull(message);
		out.writeShort(ModInfo.PROTOCOL_VERSION);
		
		try
		{
			out.markWriterIndex();
			out.writeShort(MessageRegistry.INSTANCE.getMessageId(message));
			message.encode(out);
		}
		catch (Exception e)
		{
			LOGGER.error("Failed to encode message", e);
			LOGGER.error("Message: ["+message+"]");
			
			message.getSession().tryHandleMessage(new ProtocolErrorInternalEvent(e, message, false));
			
			// Encode close reason message instead
			out.resetWriterIndex();
			message = new CloseReasonMessage("Internal error on other side");
			out.writeShort(MessageRegistry.INSTANCE.getMessageId(message));
			message.encode(out);
		}
	}
	
}