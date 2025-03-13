package com.seibel.distanthorizons.core.generation;

import com.seibel.distanthorizons.core.config.Config;
import com.seibel.distanthorizons.core.generation.tasks.IWorldGenTaskTracker;
import com.seibel.distanthorizons.core.generation.tasks.WorldGenResult;
import com.seibel.distanthorizons.core.level.DhClientLevel;
import com.seibel.distanthorizons.core.logging.DhLoggerBuilder;
import com.seibel.distanthorizons.core.multiplayer.client.AbstractFullDataNetworkRequestQueue;
import com.seibel.distanthorizons.core.multiplayer.client.ClientNetworkState;
import com.seibel.distanthorizons.core.pos.DhSectionPos;
import com.seibel.distanthorizons.core.pos.blockPos.DhBlockPos2D;
import com.seibel.distanthorizons.core.render.renderer.IDebugRenderable;
import com.seibel.distanthorizons.core.util.LodUtil;
import com.seibel.distanthorizons.core.util.objects.RollingAverage;
import org.apache.logging.log4j.Logger;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.*;

public class RemoteWorldRetrievalQueue extends AbstractFullDataNetworkRequestQueue implements IFullDataSourceRetrievalQueue, IDebugRenderable
{
	private static final Logger LOGGER = DhLoggerBuilder.getLogger();
	
	private int estimatedRemainingTaskCount;
	private int estimatedTotalChunkCount;
	
	private final RollingAverage rollingAverageChunkGenTimeInMs = new RollingAverage(1_000);
	@Override public RollingAverage getRollingAverageChunkGenTimeInMs() { return this.rollingAverageChunkGenTimeInMs; }
	
	
	
	//=============//
	// constructor //
	//=============//
	
	public RemoteWorldRetrievalQueue(ClientNetworkState networkState, DhClientLevel level)
	{ super(networkState, level, false, Config.Client.Advanced.Debugging.DebugWireframe.showWorldGenQueue); }
	
	
	
	//===========================//
	// retrieval queue overrides //
	//===========================//
	
	@Override
	public void startAndSetTargetPos(DhBlockPos2D targetPos) { super.tick(targetPos); }
	
	@Override
	public byte lowestDataDetail()
	{
		return Config.Server.Experimental.enableNSizedGeneration.get()
				? LodUtil.BLOCK_DETAIL_LEVEL + 12
				: LodUtil.BLOCK_DETAIL_LEVEL;
	} // TODO should be the same as what the server's update propagator can provide
	@Override
	public byte highestDataDetail() { return LodUtil.BLOCK_DETAIL_LEVEL; }
	
	@Override
	public CompletableFuture<WorldGenResult> submitRetrievalTask(long sectionPos, byte requiredDataDetail, IWorldGenTaskTracker tracker)
	{
		long generationStartMsTime = System.currentTimeMillis();
		
		
		return super.submitRequest(sectionPos, fullDataSource -> {
					Objects.requireNonNull(tracker.getDataSourceConsumer()).accept(fullDataSource);
					fullDataSource.close();
				})
				.thenApply(requestResult ->
				{
					long totalGenTimeInMs = System.currentTimeMillis() - generationStartMsTime;
					
					int chunkWidth = DhSectionPos.getChunkWidth(sectionPos);
					int chunkCount = chunkWidth * chunkWidth;
					double timePerChunk = (double)totalGenTimeInMs / (double)chunkCount;
					this.rollingAverageChunkGenTimeInMs.addValue(timePerChunk);
					
					switch (requestResult)
					{
						case SUCCEEDED:
							return WorldGenResult.CreateSuccess(sectionPos);
						case FAILED:
							return WorldGenResult.CreateFail();
						case REQUIRES_SPLITTING:
							List<CompletableFuture<WorldGenResult>> childFutures = new ArrayList<>(4);
							DhSectionPos.forEachChild(sectionPos, childPos -> {
								tracker.shouldGenerateSplitChild(childPos).thenAccept(shouldGenerate -> {
									if (shouldGenerate)
									{
										childFutures.add(this.submitRetrievalTask(childPos, requiredDataDetail, tracker));
									}
								});
							});
							return WorldGenResult.CreateSplit(childFutures);
					}
					
					LodUtil.assertNotReach();
					return WorldGenResult.CreateFail();
				});
	}
	
	@Override
	public CompletableFuture<Void> startClosingAsync(boolean cancelCurrentGeneration, boolean alsoInterruptRunning)
	{ return super.startClosingAsync(alsoInterruptRunning); }
	
	
	
	//=================================//
	// network request queue overrides //
	//=================================//
	
	@Override
	protected int getRequestRateLimit() { return this.networkState.sessionConfig.getGenerationRequestRateLimit(); }
	@Override
	protected boolean isSectionAllowedToGenerate(long sectionPos, DhBlockPos2D targetPos)
	{
		if (this.networkState.sessionConfig.getGenerationBoundsRadius() > 0)
		{
			if (DhSectionPos.getChebyshevSignedBlockDistance(sectionPos, new DhBlockPos2D(
					this.networkState.sessionConfig.getGenerationBoundsX(),
					this.networkState.sessionConfig.getGenerationBoundsZ()
			)) > this.networkState.sessionConfig.getGenerationBoundsRadius())
			{
				return false;
			}
		}
		
		return DhSectionPos.getChebyshevSignedBlockDistance(sectionPos, targetPos) <= this.networkState.sessionConfig.getMaxGenerationRequestDistance() * 16;
	}
	
	@Override
	protected String getQueueName() { return "World Remote Generation Queue"; }
	
	
	
	//===============//
	// debug display //
	//===============//
	
	@Override
	public int getEstimatedRemainingTaskCount() { return this.estimatedRemainingTaskCount; }
	@Override
	public void setEstimatedRemainingTaskCount(int newEstimate) { this.estimatedRemainingTaskCount = newEstimate; }
	
	@Override
	public int getRetrievalEstimatedRemainingChunkCount() { return this.estimatedTotalChunkCount; }
	@Override
	public void setRetrievalEstimatedRemainingChunkCount(int newEstimate) { this.estimatedTotalChunkCount = newEstimate; }
	
	@Override 
	public int getQueuedChunkCount() { return 0; }
	
	
}