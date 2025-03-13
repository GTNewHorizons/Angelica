package com.seibel.distanthorizons.core.generation;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.RemovalCause;
import com.seibel.distanthorizons.core.api.internal.SharedApi;
import com.seibel.distanthorizons.core.config.Config;
import com.seibel.distanthorizons.core.file.fullDatafile.GeneratedFullDataSourceProvider;
import com.seibel.distanthorizons.core.logging.DhLoggerBuilder;
import com.seibel.distanthorizons.core.pos.DhSectionPos;
import com.seibel.distanthorizons.core.pos.blockPos.DhBlockPos2D;
import com.seibel.distanthorizons.core.util.LodUtil;
import com.seibel.distanthorizons.core.util.objects.RollingAverage;
import com.seibel.distanthorizons.core.wrapperInterfaces.world.IServerLevelWrapper;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.Nullable;

import java.text.MessageFormat;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

public class PregenManager
{
	protected static final Logger LOGGER = DhLoggerBuilder.getLogger();
	
	private final AtomicReference<PregenState> pregenFuture = new AtomicReference<>();
	
	
	public CompletableFuture<Void> startPregen(
			IServerLevelWrapper levelWrapper,
			DhBlockPos2D origin,
			int chunkRadius
	)
	{
		PregenState pregenState = new PregenState(
				(GeneratedFullDataSourceProvider) SharedApi.getIDhServerWorld().getLevel(levelWrapper).getFullDataProvider(),
				DhSectionPos.convertToDetailLevel(
						DhSectionPos.encode(LodUtil.BLOCK_DETAIL_LEVEL, origin.x, origin.z),
						DhSectionPos.SECTION_MINIMUM_DETAIL_LEVEL
				),
				(int) Math.pow(Math.ceil((double) chunkRadius / 4 * 2), 2)
		);
		
		if (!this.pregenFuture.compareAndSet(null, pregenState))
		{
			pregenState.completeExceptionally(new IllegalStateException("Pregen is already running."));
			return pregenState;
		}
		pregenState.whenComplete((result, throwable) -> {
			this.pregenFuture.set(null);
		});
		
		pregenState.fillPendingQueue();
		return pregenState;
	}
	
	public CompletableFuture<Void> getRunningPregen()
	{
		return this.pregenFuture.get();
	}
	
	@Nullable
	public String getStatusString()
	{
		PregenState pregenState = this.pregenFuture.get();
		if (pregenState != null)
		{
			return pregenState.getStatusString();
		}
		
		return null;
	}
	
	
	private static class PregenState extends CompletableFuture<Void>
	{
		private final GeneratedFullDataSourceProvider fullDataSourceProvider;
		private final long originSectionPos;
		private final int sectionsToGenerate;
		
		private final AtomicInteger nextSectionSpiralIndex = new AtomicInteger(0);
		
		private final AtomicLong lastTaskFinishTime = new AtomicLong(System.currentTimeMillis());
		private final RollingAverage averageTaskCompletionIntervalMs = new RollingAverage(1000);
		
		private final AtomicLong lastLogTime = new AtomicLong();
		
		private final DynamicNumberFormat generatedRadius = new DynamicNumberFormat(3);
		private final DynamicNumberFormat generatedPercentage = new DynamicNumberFormat(5);
		
		
		@SuppressWarnings("DataFlowIssue")
		private final Cache<Long, Long> pendingGenerations = CacheBuilder.newBuilder()
				.expireAfterWrite(2, TimeUnit.MINUTES)
				.<Long, Long>removalListener(removalNotification -> {
					if (removalNotification.getCause() == RemovalCause.EXPIRED)
					{
						LOGGER.warn("Generation for section " + DhSectionPos.toString(removalNotification.getKey()) + " has expired!");
					}
					
					long timeSincePreviousTaskFinish = System.currentTimeMillis() - this.lastTaskFinishTime.getAndSet(System.currentTimeMillis());
					this.averageTaskCompletionIntervalMs.addValue(timeSincePreviousTaskFinish);
					
					PregenState.this.fillPendingQueue();
				})
				.build();
		
		
		public PregenState(GeneratedFullDataSourceProvider fullDataSourceProvider, long originSectionPos, int sectionsToGenerate)
		{
			this.fullDataSourceProvider = fullDataSourceProvider;
			this.originSectionPos = originSectionPos;
			this.sectionsToGenerate = sectionsToGenerate;
		}
		
		
		private void fillPendingQueue()
		{
			while (!this.isDone() && this.pendingGenerations.size() < Config.Common.MultiThreading.numberOfThreads.get())
			{
				int nextSpiralIndex = this.nextSectionSpiralIndex.getAndIncrement();
				if (nextSpiralIndex > this.sectionsToGenerate)
				{
					this.complete(null);
					return;
				}
				
				long nextSectionPos = this.sectionPosOnSpiral(nextSpiralIndex);
				
				long lastLogTime = this.lastLogTime.get();
				if (System.currentTimeMillis() - lastLogTime >= TimeUnit.SECONDS.toMillis(Config.Common.WorldGenerator.generationProgressDisplayIntervalInSeconds.get())
						&& this.lastLogTime.compareAndSet(lastLogTime, System.currentTimeMillis()))
				{
					LOGGER.info(this.getStatusString());
				}
				
				this.pendingGenerations.put(nextSectionPos, System.currentTimeMillis());
				this.fullDataSourceProvider.getAsync(nextSectionPos).thenAccept(fullDataSource -> {
					if (this.fullDataSourceProvider.isFullyGenerated(fullDataSource.columnGenerationSteps))
					{
						this.pendingGenerations.invalidate(fullDataSource.getPos());
					}
					else
					{
						this.fullDataSourceProvider.queuePositionForRetrieval(fullDataSource.getPos()).thenAccept(result -> {
							if (!result.success)
							{
								LOGGER.warn("Failed to generate section " + DhSectionPos.toString(result.pos));
							}
							
							this.pendingGenerations.invalidate(result.pos);
						});
					}
					
					fullDataSource.close();
				});
			}
		}
		
		public String getStatusString()
		{
			this.generatedRadius.update(Math.sqrt(this.nextSectionSpiralIndex.get()) / 2 * 4);
			this.generatedPercentage.update((double) this.nextSectionSpiralIndex.get() / this.sectionsToGenerate);
			
			double chunksToGenerate = Math.ceil(Math.sqrt(this.sectionsToGenerate) / 2 * 4 * 10) / 10; // ceil to nearest 0.1
			double etaMs = this.averageTaskCompletionIntervalMs.getAverage() * (this.sectionsToGenerate - this.nextSectionSpiralIndex.get());
			
			return MessageFormat.format("Generated radius: {0,number,#.###} / {1,number,#.#} chunks ({2,number,#.###%}), ETA: {3}",
					this.generatedRadius.getValue(),
					chunksToGenerate,
					this.generatedPercentage.getValue(),
					Duration.ofMillis((long) etaMs).toString()
							.substring(2)
							.replaceAll("(\\d[HMS])(?!$)", "$1 ")
							.replaceAll("\\.\\d+", "")
							.toLowerCase()
			);
		}
		
		private long sectionPosOnSpiral(int index)
		{
			if (index == 0)
			{
				return this.originSectionPos;
			}
			index--;
			
			int ringNumber = (int) Math.round(Math.sqrt(Math.floor((double) index / 4) + 1));
			index -= ringNumber * 8 * (ringNumber - 1) / 2;
			
			// 0 <= pos <= (ringNumber * 8) - 1
			// ringNumber * 8 - full ring
			// ringNumber * 4 - half-ring
			// ringNumber * 2 - quarter-ring, i.e. one side of it
			// ringNumber - half of quarter-ring
			
			int x = -ringNumber + 1 + Math.min(index % (ringNumber * 4), ringNumber * 2 - 1);
			int z = ringNumber - Math.max(0, index % (ringNumber * 4) - ringNumber * 2 + 1);
			
			if (index >= ringNumber * 4)
			{
				x = -x;
				z = -z;
			}
			
			x += DhSectionPos.getX(this.originSectionPos);
			z += DhSectionPos.getZ(this.originSectionPos);
			
			return DhSectionPos.encode(DhSectionPos.getDetailLevel(this.originSectionPos), x, z);
		}
		
	}
	
	private static class DynamicNumberFormat
	{
		private final int maxPrecision;
		
		private double value = 0;
		private int lastPrecision = 0;
		
		private DynamicNumberFormat(int maxPrecision)
		{
			this.maxPrecision = maxPrecision;
		}
		
		public synchronized void update(double newValue)
		{
			int precision = 0;
			
			while (precision < this.maxPrecision && (int) (newValue * Math.pow(10, precision)) == (int) (this.value * Math.pow(10, precision)))
			{
				precision++;
			}
			
			// Filter momentary false attempts to decrease precision
			if (precision < this.lastPrecision)
			{
				int tmpPrecision = this.lastPrecision;
				this.lastPrecision = precision;
				precision = tmpPrecision;
			}
			else
			{
				this.lastPrecision = precision;
			}
			
			this.value = (double) Math.round(newValue * Math.pow(10, precision)) / Math.pow(10, precision);
		}
		
		public double getValue()
		{
			return this.value;
		}
		
	}
	
}
