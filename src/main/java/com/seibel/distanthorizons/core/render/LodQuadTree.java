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

package com.seibel.distanthorizons.core.render;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.RemovalCause;
import com.google.common.cache.RemovalNotification;
import com.seibel.distanthorizons.core.config.Config;
import com.seibel.distanthorizons.core.dataObjects.render.CachedColumnRenderSource;
import com.seibel.distanthorizons.core.dataObjects.render.ColumnRenderSource;
import com.seibel.distanthorizons.core.dataObjects.render.bufferBuilding.ColumnRenderBuffer;
import com.seibel.distanthorizons.core.enums.EDhDirection;
import com.seibel.distanthorizons.core.file.fullDatafile.FullDataSourceProviderV2;
import com.seibel.distanthorizons.core.level.IDhClientLevel;
import com.seibel.distanthorizons.core.logging.DhLoggerBuilder;
import com.seibel.distanthorizons.core.pos.blockPos.DhBlockPos2D;
import com.seibel.distanthorizons.core.pos.DhSectionPos;
import com.seibel.distanthorizons.core.render.renderer.DebugRenderer;
import com.seibel.distanthorizons.core.render.renderer.IDebugRenderable;
import com.seibel.distanthorizons.core.render.renderer.generic.BeaconRenderHandler;
import com.seibel.distanthorizons.core.render.renderer.generic.GenericObjectRenderer;
import com.seibel.distanthorizons.core.util.KeyedLockContainer;
import com.seibel.distanthorizons.core.util.LodUtil;
import com.seibel.distanthorizons.core.util.ThreadUtil;
import com.seibel.distanthorizons.core.util.objects.quadTree.QuadNode;
import com.seibel.distanthorizons.core.util.objects.quadTree.QuadTree;
import com.seibel.distanthorizons.core.util.threading.ThreadPoolUtil;
import com.seibel.distanthorizons.coreapi.util.MathUtil;
import it.unimi.dsi.fastutil.longs.LongIterator;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.Nullable;

import javax.annotation.WillNotClose;
import java.awt.*;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReentrantLock;

/**
 * This quadTree structure is our core data structure and holds
 * all rendering data.
 */
public class LodQuadTree extends QuadTree<LodRenderSection> implements IDebugRenderable, AutoCloseable
{
	public static final byte TREE_LOWEST_DETAIL_LEVEL = ColumnRenderSource.SECTION_SIZE_OFFSET;
	
	private static final Logger LOGGER = DhLoggerBuilder.getLogger();
	/** there should only ever be one {@link LodQuadTree} so having the thread static should be fine */
	private static final ThreadPoolExecutor FULL_DATA_RETRIEVAL_QUEUE_THREAD = ThreadUtil.makeSingleThreadPool("QuadTree Full Data Retrieval Queue Populator");
	private static final int WORLD_GEN_QUEUE_UPDATE_DELAY_IN_MS = 1_000;
	
	
	public final int blockRenderDistanceDiameter;
	@WillNotClose
	private final FullDataSourceProviderV2 fullDataSourceProvider;
	
	/**
	 * This holds every {@link DhSectionPos} that should be reloaded next tick. <br>
	 * This is a {@link ConcurrentLinkedQueue} because new sections can be added to this list via the world generator threads.
	 */
	private final ConcurrentLinkedQueue<Long> sectionsToReload = new ConcurrentLinkedQueue<>();
	private final IDhClientLevel level; //FIXME: Proper hierarchy to remove this reference!
	private final ReentrantLock treeReadWriteLock = new ReentrantLock();
	private final AtomicBoolean fullDataRetrievalQueueRunning = new AtomicBoolean(false);
	
	private ArrayList<LodRenderSection> debugRenderSections = new ArrayList<>();
	private ArrayList<LodRenderSection> altDebugRenderSections = new ArrayList<>();
	private final ReentrantLock debugRenderSectionLock = new ReentrantLock();
	
	
	/** don't let two threads load the same position at the same time */
	protected final KeyedLockContainer<Long> renderLoadLockContainer = new KeyedLockContainer<>();
	
	/**
	 * caching is done at the QuadTree level to prevent caching LODs for different levels.
	 * (Although the incorrect terrain that renders is quite entertaining). <br><br>
	 * 
	 * caching the loaded positions significantly improves initial loading performance
	 * since the same position doesn't need to be loaded 5 times.
	 */
	private final Cache<Long, CachedColumnRenderSource> cachedRenderSourceByPos
			= CacheBuilder.newBuilder()
			// availableProcessors() : each process may need to be loading a render source
			// +1 : add 1 thread count buffer to reduce the chance of accidentally unloading a render source before it's used
			// *5 : each render source needs it's 4 adjacent sides, so a total of 5 render sources are needed per load
			.maximumSize((Runtime.getRuntime().availableProcessors() + 1) * 5L)
			// No closing logic since the CachedColumnRenderSource is in charge
			// of freeing the underlying ColumnRenderSource.
			// That way we don't have to worry about accidentally closing an in-use object.
			.<Long, CachedColumnRenderSource>build();
	
	@Nullable
	public final BeaconRenderHandler beaconRenderHandler;
	
	
	/** the smallest numerical detail level number that can be rendered */
	private byte maxRenderDetailLevel;
	/** the largest numerical detail level number that can be rendered */
	private byte minRenderDetailLevel;
	
	/** used to calculate when a detail drop will occur */
	private double detailDropOffDistanceUnit;
	/** used to calculate when a detail drop will occur */
	private double detailDropOffLogBase;
	
	
	
	//==============//
	// constructors //
	//==============//
	
	public LodQuadTree(
			IDhClientLevel level, int viewDiameterInBlocks,
			int initialPlayerBlockX, int initialPlayerBlockZ,
			FullDataSourceProviderV2 fullDataSourceProvider)
	{
		super(viewDiameterInBlocks, new DhBlockPos2D(initialPlayerBlockX, initialPlayerBlockZ), TREE_LOWEST_DETAIL_LEVEL);
		
		DebugRenderer.register(this, Config.Client.Advanced.Debugging.DebugWireframe.showQuadTreeRenderStatus);
		
		this.level = level;
		this.fullDataSourceProvider = fullDataSourceProvider;
		this.blockRenderDistanceDiameter = viewDiameterInBlocks;
		
		GenericObjectRenderer genericObjectRenderer = this.level.getGenericRenderer();
		this.beaconRenderHandler = (genericObjectRenderer != null) ? new BeaconRenderHandler(genericObjectRenderer) : null;
		
	}
	
	
	
	//=============//
	// tick update //
	//=============//
	
	/**
	 * This function updates the quadTree based on the playerPos and the current game configs (static and global)
	 *
	 * @param playerPos the reference position for the player
	 */
	public void tick(DhBlockPos2D playerPos)
	{
		if (this.level == null)
		{
			// the level hasn't finished loading yet
			// TODO sometimes null pointers still happen, when logging back into a world (maybe the old level isn't null but isn't valid either?)
			return;
		}
		
		
		
		// this shouldn't be updated while the tree is being iterated through
		this.updateDetailLevelVariables();
		
		// don't traverse the tree if it is being modified
		if (this.treeReadWriteLock.tryLock())
		{
			try
			{
				// recenter if necessary, removing out of bounds sections
				this.setCenterBlockPos(playerPos, LodRenderSection::close);
				
				this.updateAllRenderSections(playerPos);
			}
			catch (Exception e)
			{
				LOGGER.error("Quad Tree tick exception for level: [" + this.level.getLevelWrapper().getDhIdentifier() + "], error: [" + e.getMessage() + "].", e);
			}
			finally
			{
				this.treeReadWriteLock.unlock();
			}
		}
	}
	private void updateAllRenderSections(DhBlockPos2D playerPos)
	{
		if (Config.Client.Advanced.Debugging.DebugWireframe.showQuadTreeRenderStatus.get())
		{
			try
			{
				// lock to prevent accidentally rendering an array that's being populated/cleared
				this.debugRenderSectionLock.lock();
				
				// swap the debug arrays
				this.debugRenderSections.clear();
				ArrayList<LodRenderSection> temp = this.debugRenderSections;
				this.debugRenderSections = this.altDebugRenderSections;
				this.altDebugRenderSections = temp;
			}
			finally
			{
				this.debugRenderSectionLock.unlock();
			}
		}
		
		
		
		// walk through each root node
		HashSet<LodRenderSection> nodesNeedingRetrieval = new HashSet<>();
		HashSet<LodRenderSection> nodesNeedingLoading = new HashSet<>();
		LongIterator rootPosIterator = this.rootNodePosIterator();
		while (rootPosIterator.hasNext())
		{
			// make sure all root nodes have been created
			long rootPos = rootPosIterator.nextLong();
			if (this.getNode(rootPos) == null)
			{
				this.setValue(rootPos, new LodRenderSection(rootPos, this, this.level, this.fullDataSourceProvider, this.cachedRenderSourceByPos, this.renderLoadLockContainer));
			}
			
			QuadNode<LodRenderSection> rootNode = this.getNode(rootPos);
			this.recursivelyUpdateRenderSectionNode(playerPos, rootNode, rootNode, rootNode.sectionPos, false, nodesNeedingRetrieval, nodesNeedingLoading);
		}
		
		
		// full data retrieval (world gen)
		if (!this.fullDataRetrievalQueueRunning.get())
		{
			this.fullDataRetrievalQueueRunning.set(true);
			FULL_DATA_RETRIEVAL_QUEUE_THREAD.execute(() -> this.queueFullDataRetrievalTasks(playerPos, nodesNeedingRetrieval));
		}
		
		
		// reloading is for sections that have been loaded once already
		this.reloadQueuedSections();
		
		// loading is for sections that haven't rendered yet
		this.loadQueuedSections(playerPos, nodesNeedingLoading);
		
	}
	/** @return whether the current position is able to render (note: not if it IS rendering, just if it is ABLE to.) */
	private boolean recursivelyUpdateRenderSectionNode(
			DhBlockPos2D playerPos, 
			QuadNode<LodRenderSection> rootNode, QuadNode<LodRenderSection> quadNode, long sectionPos, 
			boolean parentSectionIsRendering,
			HashSet<LodRenderSection> nodesNeedingRetrieval,
			HashSet<LodRenderSection> nodesNeedingLoading)
	{
		//=====================//
		// get/create the node //
		// and render section  //
		//=====================//
		
		// create the node
		if (quadNode == null && this.isSectionPosInBounds(sectionPos)) // the position bounds should only fail when at the edge of the user's render distance
		{
			rootNode.setValue(sectionPos, new LodRenderSection(sectionPos, this, this.level, this.fullDataSourceProvider, this.cachedRenderSourceByPos, this.renderLoadLockContainer));
			quadNode = rootNode.getNode(sectionPos);
		}
		if (quadNode == null)
		{
			// this node must be out of bounds, or there was an issue adding it to the tree
			return false;
		}
		
		// make sure the render section is created (shouldn't be necessary, but just in case)
		LodRenderSection renderSection = quadNode.value;
		if (renderSection == null)
		{
			renderSection = new LodRenderSection(sectionPos, this, this.level, this.fullDataSourceProvider, this.cachedRenderSourceByPos, this.renderLoadLockContainer);
			quadNode.setValue(sectionPos, renderSection);
		}
		
		
		
		//===============================//
		// handle enabling, loading,     //
		// and disabling render sections //
		//===============================//
		
		//byte expectedDetailLevel = DhSectionPos.SECTION_MINIMUM_DETAIL_LEVEL + 3; // can be used instead of the following logic for testing
		byte expectedDetailLevel = this.calculateExpectedDetailLevel(playerPos, sectionPos);
		expectedDetailLevel = (byte) Math.min(expectedDetailLevel, this.minRenderDetailLevel);
		expectedDetailLevel += DhSectionPos.SECTION_MINIMUM_DETAIL_LEVEL;
		
		if (DhSectionPos.getDetailLevel(sectionPos) > expectedDetailLevel)
		{
			//=======================//
			// detail level too high //
			//=======================//
			
			boolean thisPosIsRendering = renderSection.getRenderingEnabled();
			boolean allChildrenSectionsAreLoaded = true;
			
			// recursively update each child render section
			for (int i = 0; i < 4; i++)
			{
				QuadNode<LodRenderSection> childNode = quadNode.getChildByIndex(i);
				boolean childSectionLoaded = this.recursivelyUpdateRenderSectionNode(playerPos, rootNode, childNode, DhSectionPos.getChildByIndex(sectionPos, i), thisPosIsRendering || parentSectionIsRendering, nodesNeedingRetrieval, nodesNeedingLoading);
				allChildrenSectionsAreLoaded = childSectionLoaded && allChildrenSectionsAreLoaded;
			}
			
			
			if (!allChildrenSectionsAreLoaded)
			{
				// not all child positions are loaded yet, or this section is out of render range
				return thisPosIsRendering;
			}
			else
			{
				// children are all loaded, unload this and parents
				
				if (renderSection.getRenderingEnabled())
				{
					// needs to be fired before the children are enabled so beacons render correctly
					renderSection.onRenderingDisabled();
					
					
					// unload parent sections so they don't become
					// outdated when child LODs are updated.
					// (They'd have to be reloaded from file anyway during an update)
					long parentPos = renderSection.pos;
					while (DhSectionPos.getDetailLevel(parentPos) <= this.treeMinDetailLevel)
					{
						QuadNode<LodRenderSection> parentNode = this.getNode(parentPos);
						if (parentNode != null)
						{
							LodRenderSection parentRenderSection = parentNode.value;
							if (parentRenderSection != null)
							{
								// onRenderDisabled doesn't need to be 
								// called since these sections shouldn't be loaded
								parentRenderSection.setRenderingEnabled(false);
								ColumnRenderBuffer buffer = parentRenderSection.renderBuffer;
								if (buffer != null)
								{
									buffer.close();
									parentRenderSection.renderBuffer = null;
								}
							}
						}
						
						parentPos = DhSectionPos.getParentPos(parentPos);
					}
					
					// this position's rendering has been disabled due to children being rendered
					if (Config.Client.Advanced.Debugging.DebugWireframe.showRenderSectionToggling.get())
					{
						DebugRenderer.makeParticle(new DebugRenderer.BoxParticle(new DebugRenderer.Box(renderSection.pos, 128f, 156f, 0.09f, Color.WHITE), 0.2, 32f));
					}
				}
				
				
				// walk back down the tree and enable each child section
				for (int i = 0; i < 4; i++)
				{
					QuadNode<LodRenderSection> childNode = quadNode.getChildByIndex(i);
					this.recursivelyUpdateRenderSectionNode(playerPos, rootNode, childNode, DhSectionPos.getChildByIndex(sectionPos, i), parentSectionIsRendering, nodesNeedingRetrieval, nodesNeedingLoading);
				}
				
				// disabling rendering must be done after the children are enabled
				// otherwise holes may appear in the world, overlaps are less noticeable
				renderSection.setRenderingEnabled(false);
				
				// this section is now being rendered via its children
				return true;
			}
		}
		// TODO this should only equal the expected detail level, the (expectedDetailLevel-1) is a temporary fix to prevent corners from being cut out 
		else if (DhSectionPos.getDetailLevel(sectionPos) == expectedDetailLevel || DhSectionPos.getDetailLevel(sectionPos) == expectedDetailLevel - 1)
		{
			//======================//
			// desired detail level //
			//======================//
			
			
			// prepare this section for rendering
			if (!renderSection.gpuUploadInProgress()
				&& renderSection.renderBuffer == null
				// this check is specifically for N-sized world generators where the higher quality
				// data source may not exist yet, this is done to prevent holes while waiting for said generator
				&& renderSection.getFullDataSourceExists()
				)
			{
				nodesNeedingLoading.add(renderSection);
			}
			
			// queue world gen if needed
			if (!renderSection.isFullyGenerated())
			{
				nodesNeedingRetrieval.add(renderSection);
			}
			
			// update debug if needed
			if (Config.Client.Advanced.Debugging.DebugWireframe.showQuadTreeRenderStatus.get())
			{
				this.debugRenderSections.add(renderSection);
			}
			
			
			
			// wait for the parent to disable before enabling this section, so we don't have a hole
			if (!parentSectionIsRendering && renderSection.canRender())
			{
				// if rendering is already enabled we don't have to re-enable it
				if (!renderSection.getRenderingEnabled())
				{
					renderSection.setRenderingEnabled(true);
					
					// disabling rendering must be done after the parent is enabled
					// otherwise holes may appear in the world, overlaps are less noticeable
					quadNode.deleteAllChildren((childRenderSection) ->
					{
						if (childRenderSection != null)
						{
							if (childRenderSection.getRenderingEnabled())
							{
								// this position's rendering has been disabled due to a parent rendering
								if (Config.Client.Advanced.Debugging.DebugWireframe.showRenderSectionToggling.get())
								{
									DebugRenderer.makeParticle(new DebugRenderer.BoxParticle(new DebugRenderer.Box(childRenderSection.pos, 128f, 156f, 0.09f, Color.MAGENTA), 0.2, 32f));
								}
							}
							
							childRenderSection.setRenderingEnabled(false);
							childRenderSection.onRenderingDisabled();
							childRenderSection.close();
						}
					});
					
					// needs to be fired after the children are disabled so beacons render correctly
					renderSection.onRenderingEnabled();
					
				}
			}
			
			return renderSection.canRender();
		}
		else
		{
			throw new IllegalStateException("LodQuadTree shouldn't be updating renderSections below the expected detail level: [" + expectedDetailLevel + "].");
		}
	}
	private void reloadQueuedSections()
	{
		Long pos;
		HashSet<Long> positionsToRequeue = new HashSet<>();
		while ((pos = this.sectionsToReload.poll()) != null)
		{
			if (positionsToRequeue.contains(pos))
			{
				// don't attempt to re-load positions that are already in the process of reloading
				continue;
			}
			
			try
			{
				// the section only needs to be updated if a buffer is currently present 
				LodRenderSection renderSection = this.getValue(pos);
				if (renderSection != null)
				{
					// this data source may now exist
					renderSection.updateFullDataSourceExists();
					
					if (renderSection.canRender())
					{
						if (renderSection.gpuUploadInProgress()
							|| !renderSection.uploadRenderDataToGpuAsync())
						{
							// if a section is already loading or failed to start upload
							// we need to wait to trigger it again
							// if we don't trigger it again the LOD will be out of date
							// and may be invisible/missing
							positionsToRequeue.add(pos);
						}
					}
				}
			}
			catch (IndexOutOfBoundsException e)
			{ /* the section is now out of bounds, it doesn't need to be reloaded */ }
		}
		this.sectionsToReload.addAll(positionsToRequeue);
	}
	private void loadQueuedSections(DhBlockPos2D playerPos, HashSet<LodRenderSection> nodesNeedingLoading)
	{
		ArrayList<LodRenderSection> loadSectionList = new ArrayList<>(nodesNeedingLoading);
		loadSectionList.sort((a, b) ->
		{
			int aDist = DhSectionPos.getManhattanBlockDistance(a.pos, playerPos);
			int bDist = DhSectionPos.getManhattanBlockDistance(b.pos, playerPos);
			return Integer.compare(aDist, bDist);
		});
		
		for (int i = 0; i < loadSectionList.size(); i++)
		{
			LodRenderSection renderSection = loadSectionList.get(i);
			if (!renderSection.gpuUploadInProgress() && renderSection.renderBuffer == null)
			{
				renderSection.uploadRenderDataToGpuAsync();
			}
		}
	}
	
	
	
	//====================//
	// detail level logic //
	//====================//
	
	/**
	 * This method will compute the detail level based on player position and section pos
	 * Override this method if you want to use a different algorithm
	 *
	 * @param playerPos player position as a reference for calculating the detail level
	 * @param sectionPos section position
	 * @return detail level of this section pos
	 */
	public byte calculateExpectedDetailLevel(DhBlockPos2D playerPos, long sectionPos) { return this.getDetailLevelFromDistance(playerPos.dist(DhSectionPos.getCenterBlockPosX(sectionPos), DhSectionPos.getCenterBlockPosZ(sectionPos))); }
	private byte getDetailLevelFromDistance(double distance)
	{
		double maxDetailDistance = this.getDrawDistanceFromDetail(Byte.MAX_VALUE - 1);
		if (distance > maxDetailDistance)
		{
			return Byte.MAX_VALUE - 1;
		}
		
		
		int detailLevel = (int) (Math.log(distance / this.detailDropOffDistanceUnit) / this.detailDropOffLogBase);
		return (byte) MathUtil.clamp(this.maxRenderDetailLevel, detailLevel, Byte.MAX_VALUE - 1);
	}
	private double getDrawDistanceFromDetail(int detail)
	{
		if (detail <= this.maxRenderDetailLevel)
		{
			return 0;
		}
		else if (detail >= Byte.MAX_VALUE)
		{
			return this.blockRenderDistanceDiameter * 2;
		}
		
		
		double base = Config.Client.Advanced.Graphics.Quality.horizontalQuality.get().quadraticBase;
		return Math.pow(base, detail) * this.detailDropOffDistanceUnit;
	}
	
	private void updateDetailLevelVariables()
	{
		this.detailDropOffDistanceUnit = Config.Client.Advanced.Graphics.Quality.horizontalQuality.get().distanceUnitInBlocks * LodUtil.CHUNK_WIDTH;
		this.detailDropOffLogBase = Math.log(Config.Client.Advanced.Graphics.Quality.horizontalQuality.get().quadraticBase);
		
		this.maxRenderDetailLevel = Config.Client.Advanced.Graphics.Quality.maxHorizontalResolution.get().detailLevel;
		
		// The minimum detail level is done to prevent single corner sections rendering 1 detail level lower than the others.
		// If not done corners may not be flush with the other LODs, which looks bad.
		byte minSectionDetailLevel = this.getDetailLevelFromDistance(this.blockRenderDistanceDiameter); // get the minimum allowed detail level
		minSectionDetailLevel -= 1; // -1 so corners can't render lower than their adjacent neighbors. space
		minSectionDetailLevel = (byte) Math.min(minSectionDetailLevel, this.treeMinDetailLevel); // don't allow rendering lower detail sections than what the tree contains
		this.minRenderDetailLevel = (byte) Math.max(minSectionDetailLevel, this.maxRenderDetailLevel); // respect the user's selected max resolution if it is lower detail (IE they want 2x2 block, but minSectionDetailLevel is specifically for 1x1 block render resolution)
	}
	
	
	
	//=============//
	// render data //
	//=============//
	
	/**
	 * Re-creates the color, render data.
	 * This method should be called after resource packs are changed or LOD settings are modified.
	 */
	public void clearRenderDataCache()
	{
		if (this.treeReadWriteLock.tryLock()) // TODO make async, can lock render thread
		{
			try
			{
				LOGGER.info("Disposing render data...");
				
				// clear the tree
				Iterator<QuadNode<LodRenderSection>> nodeIterator = this.nodeIterator();
				while (nodeIterator.hasNext())
				{
					QuadNode<LodRenderSection> quadNode = nodeIterator.next();
					if (quadNode.value != null)
					{
						quadNode.value.close();
						quadNode.value = null;
					}
				}
				
				LOGGER.info("Render data cleared, please wait a moment for everything to reload...");
			}
			catch (Exception e)
			{
				LOGGER.error("Unexpected error when clearing LodQuadTree render cache: " + e.getMessage(), e);
			}
			finally
			{
				this.treeReadWriteLock.unlock();
			}
		}
	}
	
	/**
	 * Can be called whenever a render section's data needs to be refreshed. <br>
	 * This should be called whenever a world generation task is completed or if the connected server has new data to show.
	 */
	public void reloadPos(long pos)
	{
		// clear cache //
		
		this.clearRenderCacheForPos(pos);
		for (EDhDirection direction : EDhDirection.ADJ_DIRECTIONS)
		{
			long adjacentPos = DhSectionPos.getAdjacentPos(pos, direction);
			this.clearRenderCacheForPos(adjacentPos);
		}
		
		
		// queue reloads //
		
		// only queue each section for reloading
		// after the cache has been cleared,
		// this is done to prevent accidentally using old cached data
		
		this.sectionsToReload.add(pos);
		
		// the adjacent locations also need to be updated to make sure lighting
		// and water updates correctly, otherwise oceans may have walls
		// and lights may not show up over LOD borders
		for (EDhDirection direction : EDhDirection.ADJ_DIRECTIONS)
		{
			long adjacentPos = DhSectionPos.getAdjacentPos(pos, direction);
			this.sectionsToReload.add(adjacentPos);
		}
	}
	private void clearRenderCacheForPos(long pos)
	{
		// locking is needed to prevent another thread
		// from accessing the cache while it's being cleared
		ReentrantLock lock = this.renderLoadLockContainer.getLockForPos(pos);
		try
		{
			lock.lock();
			this.cachedRenderSourceByPos.invalidate(pos);
		}
		finally
		{
			lock.unlock();
		}
	}
	
	
	
	//=================================//
	// full data retrieval (world gen) //
	//=================================//
	
	private void queueFullDataRetrievalTasks(DhBlockPos2D playerPos, HashSet<LodRenderSection> nodesNeedingRetrieval)
	{
		try
		{
			// add a slight delay since we don't need to check the world gen queue every tick
			Thread.sleep(WORLD_GEN_QUEUE_UPDATE_DELAY_IN_MS);
			
			// sort the nodes from nearest to farthest
			ArrayList<LodRenderSection> nodeList = new ArrayList<>(nodesNeedingRetrieval);
			nodeList.sort((a, b) ->
			{
				int aDist = DhSectionPos.getManhattanBlockDistance(a.pos, playerPos);
				int bDist = DhSectionPos.getManhattanBlockDistance(b.pos, playerPos);
				return Integer.compare(aDist, bDist);
			});
			
			// add retrieval tasks to the queue
			for (int i = 0; i < nodeList.size(); i++)
			{
				LodRenderSection renderSection = nodeList.get(i);
				if (!this.fullDataSourceProvider.canQueueRetrieval())
				{
					break;
				}
				
				renderSection.tryQueuingMissingLodRetrieval();
			}
			
			// calculate an estimate for the max number of chunks for the queue
			int totalWorldGenChunkCount = 0;
			int totalWorldGenTaskCount = 0;
			for (int i = 0; i < nodeList.size(); i++)
			{
				LodRenderSection renderSection = nodeList.get(i);
				if (!renderSection.missingPositionsCalculated())
				{
					// chunk count
					int sectionWidthInChunks = DhSectionPos.getChunkWidth(renderSection.pos);
					totalWorldGenChunkCount += sectionWidthInChunks * sectionWidthInChunks;
					
					// task count
					totalWorldGenTaskCount += renderSection.ungeneratedPositionCount();
				}
				else
				{
					totalWorldGenChunkCount += renderSection.ungeneratedChunkCount();
					
					// 1 since we assume the position can be generated in a single go
					// TODO this is a bad assumption, can we determine what the world gen supports and determine it from that?
					totalWorldGenTaskCount += 1;
				}
			}
			
			this.fullDataSourceProvider.setEstimatedRemainingRetrievalChunkCount(totalWorldGenChunkCount);
			this.fullDataSourceProvider.setTotalRetrievalPositionCount(totalWorldGenTaskCount);
		}
		catch (Exception e)
		{
			LOGGER.error("Unexpected error: "+e.getMessage(), e);
		}
		finally
		{
			this.fullDataRetrievalQueueRunning.set(false);
		}
	}
	
	
	
	//===========//
	// debugging //
	//===========//
	
	@Override
	public void debugRender(DebugRenderer debugRenderer)
	{
		try
		{
			// lock to prevent accidentally rendering the array that's being cleared
			this.debugRenderSectionLock.lock();
			
			
			for (int i = 0; i < this.debugRenderSections.size(); i++)
			{
				LodRenderSection renderSection = this.debugRenderSections.get(i);
				
				Color color = Color.BLACK;
				if (renderSection.gpuUploadInProgress())
				{
					color = Color.ORANGE;
				}
				else if (renderSection.renderBuffer == null)
				{
					// uploaded but the buffer is missing
					color = Color.PINK;
				}
				else if (renderSection.renderBuffer.hasNonNullVbos())
				{
					if (renderSection.renderBuffer.vboBufferCount() != 0)
					{
						color = Color.GREEN;
					}
					else
					{
						// This section is probably rendering an empty chunk
						color = Color.RED;
					}
				}
				
				debugRenderer.renderBox(new DebugRenderer.Box(renderSection.pos, 400, 400f, Objects.hashCode(this), 0.05f, color));
			}
		}
		finally
		{
			this.debugRenderSectionLock.unlock();
		}
	}
	
	
	
	//==============//
	// base methods //
	//==============//
	
	@Override
	public void close()
	{
		LOGGER.info("Shutting down LodQuadTree...");
		
		DebugRenderer.unregister(this, Config.Client.Advanced.Debugging.DebugWireframe.showQuadTreeRenderStatus);
		
		ThreadPoolExecutor mainCleanupExecutor = ThreadPoolUtil.getCleanupExecutor();
		// closing every node may take a few moments
		// so this is run on a separate thread to prevent lagging the render thread
		mainCleanupExecutor.execute(() -> 
		{
			this.treeReadWriteLock.lock();
			try
			{
				// walk through each node
				Iterator<QuadNode<LodRenderSection>> nodeIterator = this.nodeIterator();
				ArrayList<CompletableFuture<Void>> renderDataBuildFutures = new ArrayList<>();
				while (nodeIterator.hasNext())
				{
					QuadNode<LodRenderSection> quadNode = nodeIterator.next();
					LodRenderSection renderSection = quadNode.value;
					if (renderSection != null)
					{
						// we need to wait for the render data to finish building before we can close the cache
						CompletableFuture<Void> future = renderSection.getRenderDataBuildFuture();
						if (future != null)
						{
							renderDataBuildFutures.add(future);
						}
						
						renderSection.close();
						quadNode.value = null;
					}
				}
				
				
				// close the render cache after it is done being used
				LOGGER.info("waiting for ["+renderDataBuildFutures.size()+"] futures before closing render cache...");
				CompletableFuture.allOf(renderDataBuildFutures.toArray(new CompletableFuture[0]))
					.handle((voidObj, throwable) ->
					{
						// run on a separate thread so we don't lock up the main cleanup thread
						// with the sleep() call
						new Thread(() -> 
						{
							// Sleep shouldn't be necessary, but James found a few cases where
							// the futures incorrectly claimed they were done.
							// Sleeping solved those issues.
							try { Thread.sleep(5_000); } catch (InterruptedException ignore) {  }

							LOGGER.debug("closing render cache");
							this.cachedRenderSourceByPos.invalidateAll();
						}).start();
						
						return null;
					});
			}
			finally
			{
				this.treeReadWriteLock.unlock();
			}
		});
		
		
		LOGGER.info("Finished shutting down LodQuadTree");
	}
	
	
	
}
