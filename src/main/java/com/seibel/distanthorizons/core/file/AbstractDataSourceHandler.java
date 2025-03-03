package com.seibel.distanthorizons.core.file;

import com.seibel.distanthorizons.core.dataObjects.fullData.sources.FullDataSourceV2;
import com.seibel.distanthorizons.core.file.structure.ISaveStructure;
import com.seibel.distanthorizons.core.level.IDhLevel;
import com.seibel.distanthorizons.core.logging.DhLoggerBuilder;
import com.seibel.distanthorizons.core.pos.DhSectionPos;
import com.seibel.distanthorizons.core.sql.repo.AbstractDhRepo;
import com.seibel.distanthorizons.core.sql.dto.IBaseDTO;
import com.seibel.distanthorizons.core.util.LodUtil;
import com.seibel.distanthorizons.core.util.objects.DataCorruptedException;
import com.seibel.distanthorizons.core.util.threading.PositionalLockProvider;
import com.seibel.distanthorizons.core.util.threading.ThreadPoolUtil;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;

// TODO is there a reason this is separate from FullDataSourceProviderV2?
//  We shouldn't need multiple data source handlers
public abstract class AbstractDataSourceHandler
		<TDataSource extends IDataSource<TDhLevel>, 
				TDTO extends IBaseDTO<Long>,
				TRepo extends AbstractDhRepo<Long, TDTO>,
				TDhLevel extends IDhLevel>
		implements AutoCloseable
{
	private static final Logger LOGGER = DhLoggerBuilder.getLogger();
	private static final Set<String> CORRUPT_DATA_ERRORS_LOGGED = Collections.newSetFromMap(new ConcurrentHashMap<>());
	
	
	/**
	 * The highest numerical detail level possible. 
	 * Used when determining which positions to update. 
	 * 
	 * @see AbstractDataSourceHandler#MIN_SECTION_DETAIL_LEVEL
	 */
	public static final byte TOP_SECTION_DETAIL_LEVEL = DhSectionPos.SECTION_MINIMUM_DETAIL_LEVEL + LodUtil.REGION_DETAIL_LEVEL;
	/** 
	 * The lowest numerical detail level possible. 
	 *
	 * @see AbstractDataSourceHandler#TOP_SECTION_DETAIL_LEVEL
	 */
	public static final byte MIN_SECTION_DETAIL_LEVEL = DhSectionPos.SECTION_MINIMUM_DETAIL_LEVEL;
	
	
	protected final PositionalLockProvider updateLockProvider = new PositionalLockProvider();
	/** 
	 * generally just used for debugging,
	 * keeps track of which positions are currently locked.
	 */
	public final Set<Long> lockedPosSet = ConcurrentHashMap.newKeySet();
	public final ConcurrentHashMap<Long, AtomicInteger> queuedUpdateCountsByPos = new ConcurrentHashMap<>();
	
	
	protected final ReentrantLock closeLock = new ReentrantLock();
	protected volatile boolean isShutdown = false;
	
	protected final TDhLevel level;
	protected final File saveDir;
	
	public final TRepo repo;
	
	public final ArrayList<IDataSourceUpdateFunc<TDataSource>> dateSourceUpdateListeners = new ArrayList<>();
	
	
	
	//=============//
	// constructor //
	//=============//
	
	public AbstractDataSourceHandler(TDhLevel level, ISaveStructure saveStructure) { this(level, saveStructure, null); }
	public AbstractDataSourceHandler(TDhLevel level, ISaveStructure saveStructure, @Nullable File saveDirOverride)
	{
		this.level = level;
		this.saveDir = (saveDirOverride == null) ? saveStructure.getSaveFolder(level.getLevelWrapper()) : saveDirOverride;
		this.repo = this.createRepo();
	}
	
	
	
	
	//==================//
	// abstract methods //
	//==================//
	
	/** When this is called the parent folders should be created */
	protected abstract TRepo createRepo();
	
	protected abstract TDataSource createDataSourceFromDto(TDTO dto) throws InterruptedException, IOException, DataCorruptedException;
	protected abstract TDTO createDtoFromDataSource(TDataSource dataSource);
	
	protected abstract TDataSource makeEmptyDataSource(long pos);
	
	
	
	//==============//
	// data reading //
	//==============//
	
	/**
	 * Returns the {@link TDataSource} for the given section position. <Br>
	 * The returned data source may be null if repo is in the process of shutting down. <Br> <Br>
	 *
	 * This call is concurrent. I.e. it supports being called by multiple threads at the same time.
	 */
	public CompletableFuture<TDataSource> getAsync(long pos)
	{
		AbstractExecutorService executor = ThreadPoolUtil.getFileHandlerExecutor();
		if (executor == null || executor.isTerminated())
		{
			return CompletableFuture.completedFuture(null);
		}
		
		
		try
		{
			return CompletableFuture.supplyAsync(() -> this.get(pos), executor);
		}
		catch (RejectedExecutionException ignore) 
		{
			// the thread pool was probably shut down because it's size is being changed, just wait a sec and it should be back
			return CompletableFuture.completedFuture(null);
		}
	}
	/**
	 * Should only be used in internal file handler methods where we are already running on a file handler thread.
	 * Can return null if the repo is in the process of being shut down
	 * @see AbstractDataSourceHandler#getAsync(long)
	 */
	@Nullable
	public TDataSource get(long pos)
	{
		TDataSource dataSource = null;
		try(TDTO dto = this.repo.getByKey(pos))
		{
			if (dto != null)
			{
				try
				{
					// load from database
					dataSource = this.createDataSourceFromDto(dto);
				}
				catch (DataCorruptedException e)
				{
					// there's a rare issue where the exception doesn't
					// have a message, which can cause problems
					String message = (e.getMessage() == null) ? e.getMessage() : "No Error message for exception ["+e.getClass().getSimpleName()+"]";
					
					// Only log each message type once.
					// This is done to prevent logging "No compression mode with the value [2]" 10,000 times 
					// if the user is migrating from a nightly build and used ZStd.
					if (CORRUPT_DATA_ERRORS_LOGGED.add(message))
					{
						LOGGER.warn("Corrupted data found at pos [" + DhSectionPos.toString(pos) + "]. Data at position will be deleted so it can be re-generated to prevent issues. Future errors with this same message won't be logged. Error: [" + message + "].", e);
					}
					
					this.repo.deleteWithKey(pos);
				}
			}
			else
			{
				dataSource = this.makeEmptyDataSource(pos);
			}
		}
		catch (InterruptedException ignore) { }
		catch (IOException e)
		{
			LOGGER.warn("File read Error for pos ["+ DhSectionPos.toString(pos)+"], error: "+e.getMessage(), e);
		}
		
		return dataSource;
	}
	
	
	
	//===============//
	// data updating //
	//===============//
	
	/** 
	 * Can be used if the same thread is already handling IO and/or LOD generation.
	 * Otherwise the async version {@link AbstractDataSourceHandler#updateDataSourceAsync(FullDataSourceV2)} may be a better choice.
	 */
	public void updateDataSource(@NotNull FullDataSourceV2 inputDataSource)
	{ this.updateDataSourceAtPos(inputDataSource.getPos(), inputDataSource, true); }
	
	/**
	 * Can be used if you don't want to lock the current thread
	 * Otherwise the sync version {@link AbstractDataSourceHandler#updateDataSource(FullDataSourceV2)} may be a better choice.
	 */
	public CompletableFuture<Void> updateDataSourceAsync(@NotNull FullDataSourceV2 inputDataSource)
	{
		AbstractExecutorService executor = ThreadPoolUtil.getChunkToLodBuilderExecutor();
		if (executor == null || executor.isTerminated())
		{
			return CompletableFuture.completedFuture(null);
		}
		
		
		try
		{
			this.markUpdateStart(inputDataSource.getPos());
			return CompletableFuture.runAsync(() ->
			{
				try
				{
					this.updateDataSourceAtPos(inputDataSource.getPos(), inputDataSource, true);
				}
				catch (Exception e)
				{
					LOGGER.error("Unexpected error in async data source update at pos: ["+DhSectionPos.toString(inputDataSource.getPos())+"], error: ["+e.getMessage()+"].", e);
				}
				finally
				{
					this.markUpdateEnd(inputDataSource.getPos());
				}
			}, executor);
		}
		catch (RejectedExecutionException ignore)
		{
			// can happen if the executor was shutdown while this task was queued
			this.markUpdateEnd(inputDataSource.getPos());
			return CompletableFuture.completedFuture(null);
		}
	}
	
	/** 
	 * After this method returns the inputData will be written to file.
	 *
	 * @param updatePos the position to update
	 */
	protected void updateDataSourceAtPos(long updatePos, @NotNull FullDataSourceV2 inputData, boolean lockOnUpdatePos)
	{
		boolean methodLocked = false;
		// a lock is necessary to prevent two threads from writing to the same position at once,
		// if that happens only the second update will apply and the LOD will end up with hole(s)
		ReentrantLock updateLock = this.updateLockProvider.getLock(updatePos);
		
		try
		{
			if (lockOnUpdatePos)
			{
				methodLocked = true;
				updateLock.lock();
				this.lockedPosSet.add(updatePos);
			}
			
			
			// get or create the data source
			try (TDataSource recipientDataSource = this.get(updatePos))
			{
				if (recipientDataSource != null)
				{
					boolean dataModified = recipientDataSource.update(inputData, this.level);
					if (dataModified)
					{
						// save the updated data to the database
						try (TDTO dto = this.createDtoFromDataSource(recipientDataSource))
						{
							this.repo.save(dto);
						}
						
						
						for (IDataSourceUpdateFunc<TDataSource> listener : this.dateSourceUpdateListeners)
						{
							if (listener != null)
							{
								listener.OnDataSourceUpdated(recipientDataSource);
							}
						}
					}
				}
			}
		}
		catch (Exception e)
		{
			LOGGER.error("Error updating pos ["+DhSectionPos.toString(updatePos)+"], error: "+e.getMessage(), e);
		}
		finally
		{
			if (methodLocked)
			{
				updateLock.unlock();
				this.lockedPosSet.remove(updatePos);
			}
		}
	}
	
	
	
	//================//
	// helper methods //
	//================//
	
	/** used for debugging to track which positions are queued for updating */
	private void markUpdateStart(long dataSourcePos)
	{
		this.queuedUpdateCountsByPos.compute(dataSourcePos, (pos, atomicCount) ->
		{
			if (atomicCount == null)
			{
				atomicCount = new AtomicInteger(0);
			}
			atomicCount.incrementAndGet();
			return atomicCount;
		});
	}
	/** used for debugging to track which positions are queued for updating */
	private void markUpdateEnd(long dataSourcePos)
	{
		this.queuedUpdateCountsByPos.compute(dataSourcePos, (pos, atomicCount) ->
		{
			if (atomicCount != null && atomicCount.decrementAndGet() <= 0)
			{
				atomicCount = null;
			}
			return atomicCount;
		});
	}
	
	
	
	//=========//
	// cleanup //
	//=========//
	
	@Override
	public void close()
	{
		try
		{
			this.closeLock.lock();
			this.isShutdown = true;
			
			// wait a moment so any queued saves can finish queuing, 
			// otherwise we might not see everything that needs saving and attempt to use a closed repo
			Thread.sleep(200);
			
			LOGGER.info("Closing [" + this.getClass().getSimpleName() + "] for level: [" + this.level + "].");
			
			this.repo.close();
		}
		catch (InterruptedException ignore) { }
		finally
		{
			this.closeLock.unlock();
		}
	}
	
	
	
	//================//
	// helper classes //
	//================//
	
	@FunctionalInterface
	public interface IDataSourceUpdateFunc<TDataSource>
	{
		void OnDataSourceUpdated(TDataSource updatedFullDataSource);
	}
	
}
