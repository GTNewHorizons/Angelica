package com.seibel.distanthorizons.core.multiplayer.fullData;

import com.seibel.distanthorizons.core.network.messages.fullData.FullDataSplitMessage;
import com.seibel.distanthorizons.core.network.session.NetworkSession;
import com.seibel.distanthorizons.core.util.TimerUtil;
import io.netty.buffer.ByteBuf;

import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.*;

public class FullDataPayloadSender implements AutoCloseable
{
	private static final int TICK_RATE = 4;
	
	/** 1 Mebibyte minus 576 bytes for other info */
	public static final int FULL_DATA_SPLIT_SIZE_IN_BYTES = 1_048_000;
	
	
	private static final Timer UPLOAD_TIMER = TimerUtil.CreateTimer("FullDataPayloadSender");
	private final TimerTask tickTimerTask = TimerUtil.createTimerTask(this::tick);
	
	private final NetworkSession session;
	private final IntSupplier maxKBpsSupplier;
	private final ConcurrentLinkedQueue<PendingTransfer> transferQueue = new ConcurrentLinkedQueue<>();
	
	
	public FullDataPayloadSender(NetworkSession session, IntSupplier maxKBpsSupplier)
	{
		this.session = session;
		this.maxKBpsSupplier = maxKBpsSupplier;
		UPLOAD_TIMER.scheduleAtFixedRate(this.tickTimerTask, 0, 1000 / TICK_RATE);
	}
	
	@Override
	public void close()
	{
		this.tickTimerTask.cancel();
		
		PendingTransfer pendingTransfer;
		while ((pendingTransfer = this.transferQueue.poll()) != null)
		{
			pendingTransfer.close();
		}
	}
	
	
	public void sendInChunks(FullDataPayload payload, Runnable sendFinalMessage)
	{
		this.transferQueue.add(new PendingTransfer(payload, sendFinalMessage));
	}
	
	private void tick()
	{
		int convertedMaxRate = this.maxKBpsSupplier.getAsInt();
		convertedMaxRate = convertedMaxRate > 0 ? convertedMaxRate : Integer.MAX_VALUE / 1000;
		
		// + 1 to account for rounding errors on values of < 4
		int bytesToSend = (convertedMaxRate * 1000) / TICK_RATE + 1;
		while (bytesToSend > 0)
		{
			PendingTransfer pendingTransfer = this.transferQueue.peek();
			if (pendingTransfer == null)
			{
				return;
			}
			
			int chunkSize = Math.min(Math.min(bytesToSend, FULL_DATA_SPLIT_SIZE_IN_BYTES), pendingTransfer.buffer.readableBytes());
			boolean isFirstChunk = pendingTransfer.buffer.readerIndex() == 0;
			

			// TODO FullDataSplitMessage chunkMessage = new FullDataSplitMessage(pendingTransfer.bufferId, pendingTransfer.buffer.readRetainedSlice(chunkSize), isFirstChunk);
            FullDataSplitMessage chunkMessage = new FullDataSplitMessage(pendingTransfer.bufferId, pendingTransfer.buffer.readSlice(chunkSize).retain(), isFirstChunk);
            this.session.sendMessage(chunkMessage);

			bytesToSend -= chunkSize;
			
			if (pendingTransfer.buffer.readableBytes() == 0)
			{
				pendingTransfer.sendFinalMessage.run();
				pendingTransfer.close();
				this.transferQueue.poll();
			}
		}
	}
	
	
	private static class PendingTransfer implements AutoCloseable
	{
		public final int bufferId;
		public final ByteBuf buffer;
		public final Runnable sendFinalMessage;
		private final AtomicBoolean isClosed = new AtomicBoolean();
		
		private PendingTransfer(FullDataPayload payload, Runnable sendFinalMessage)
		{
			this.bufferId = payload.dtoBufferId;
			// TODO this.buffer = payload.dtoBuffer.retainedDuplicate().readerIndex(0);
            this.buffer = payload.dtoBuffer.duplicate().retain().readerIndex(0);
			this.sendFinalMessage = sendFinalMessage;
		}
		
		@Override
		public void close()
		{
			if (this.isClosed.compareAndSet(false, true))
			{
				this.buffer.release();
			}
		}
		
	}
	
}
