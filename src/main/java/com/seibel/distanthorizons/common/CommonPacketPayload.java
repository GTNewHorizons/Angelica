package com.seibel.distanthorizons.common;

#if MC_VER >= MC_1_20_6

import com.seibel.distanthorizons.core.network.messages.AbstractNetworkMessage;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public record CommonPacketPayload(@Nullable AbstractNetworkMessage message) implements CustomPacketPayload
{
	public static final Type<CommonPacketPayload> TYPE = new Type<>(AbstractPluginPacketSender.WRAPPER_PACKET_RESOURCE);
	
	@NotNull
	@Override
	public Type<? extends CustomPacketPayload> type() { return TYPE; }
	
	
	public static class Codec implements StreamCodec<FriendlyByteBuf, CommonPacketPayload>
	{
		@NotNull
		@Override
		public CommonPacketPayload decode(@NotNull FriendlyByteBuf in)
		{ return new CommonPacketPayload(AbstractPluginPacketSender.decodeMessage(in)); }
		
		@Override
		public void encode(@NotNull FriendlyByteBuf out, CommonPacketPayload payload)
		{ AbstractPluginPacketSender.encodeMessage(out, payload.message()); }
		
	}
	
}

#endif