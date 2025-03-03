package com.seibel.distanthorizons.core.file.fullDatafile;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.RemovalCause;
import com.google.common.cache.RemovalNotification;
import com.seibel.distanthorizons.core.dataObjects.fullData.sources.FullDataSourceV2;
import com.seibel.distanthorizons.core.logging.DhLoggerBuilder;
import com.seibel.distanthorizons.core.pos.DhSectionPos;
import com.seibel.distanthorizons.core.util.KeyedLockContainer;
import com.seibel.distanthorizons.core.util.ThreadUtil;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;

import java.lang.ref.WeakReference;
import java.util.Collections;
import java.util.Set;
import java.util.concurrent.*;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Used to batch together multiple data source updates that all
 * affect the same position.
 */
public class DelayedFullDataSourceSaveCache implements AutoCloseable
{
	private static final Logger LOGGER = DhLoggerBuilder.getLogger();
	
	/** 
	 * a cache won't automatically clean itself unless we trigger it's clean method
	 * if not done then we'd only see the cache invalidate when new inserts happen,
	 * which causes weird behavior when placing/breaking blocks.
	 */
	private static final ThreadPoolExecutor BACKGROUND_CLEAN_UP_THREAD = ThreadUtil.makeSingleDaemonThreadPool("delayed save cache cleaner");
	private static final Set<WeakReference<DelayedFullDataSourceSaveCache>> SAVE_CACHE_SET = Collections.newSetFromMap(new ConcurrentHashMap<>());
	/** how long between clean up checks */
	private static final int CLEANUP_CHECK_TIME_IN_MS = 1_000;
	
	
	
	private final Cache<Long, FullDataSourceV2> dataSourceByPosition;
	
	/* don't let two threads load the same position at the same time */
	protected final KeyedLockContainer<Long> saveLockContainer = new KeyedLockContainer<>();
	
	private final ISaveDataSourceFunc onSaveTimeoutAsyncFunc;
	private final int saveDelayInMs;
	
	
	
	//=============//
	// constructor //
	//=============//
	
	static
	{
		BACKGROUND_CLEAN_UP_THREAD.execute(() -> runCleanupLoop());
	}
	
	public DelayedFullDataSourceSaveCache(@NotNull ISaveDataSourceFunc onSaveTimeoutAsyncFunc, int saveDelayInMs)
	{
		this.onSaveTimeoutAsyncFunc = onSaveTimeoutAsyncFunc;
		this.saveDelayInMs = saveDelayInMs;
		
		
		this.dataSourceByPosition =
			CacheBuilder.newBuilder()
				.expireAfterAccess(this.saveDelayInMs, TimeUnit.MILLISECONDS)
				.expireAfterWrite(this.saveDelayInMs, TimeUnit.MILLISECONDS)
				.removalListener(this::handleDataSourceRemoval)
				.<Long, FullDataSourceV2>build();
		
		SAVE_CACHE_SET.add(new WeakReference<>(this));
	}
	
	
	
	//==============//
	// update queue //
	//==============//
	
	/**
	 * Writing into memory is done synchronously so inputDataSource can 
	 * be closed after this method finishes.
	 */
	public void writeDataSourceToMemoryAndQueueSave(FullDataSourceV2 inputDataSource)
	{
		long inputPos = inputDataSource.getPos();
		
		ReentrantLock lock = this.saveLockContainer.getLockForPos(inputPos);
		try
		{
			lock.lock();
			
			FullDataSourceV2 memoryDataSource = this.dataSourceByPosition.getIfPresent(inputPos);
			if (memoryDataSource == null)
			{
				memoryDataSource = FullDataSourceV2.createEmpty(inputPos);
			}
			memoryDataSource.update(inputDataSource);
			this.dataSourceByPosition.put(inputPos, memoryDataSource);
		}
		finally
		{
			lock.unlock();
		}
	}
	
	public void handleDataSourceRemoval(RemovalNotification<Long, FullDataSourceV2> removalNotification)
	{
		RemovalCause cause = removalNotification.getCause();
		if (cause == RemovalCause.EXPIRED
			|| cause == RemovalCause.COLLECTED
			|| cause == RemovalCause.EXPLICIT
			|| cause == RemovalCause.SIZE)
		{
			// close the data source after it has expired from the cache
			FullDataSourceV2 dataSource = removalNotification.getValue();
			if (dataSource != null)
			{
				this.onSaveTimeoutAsyncFunc.saveAsync(dataSource)
					.handle((voidObj, throwable) ->
					{
						try
						{
							dataSource.close();
						}
						catch (Exception e)
						{
							LOGGER.error("Unable to close datasource ["+ DhSectionPos.toString(dataSource.getPos()) +"], removal cause: ["+cause+"], error: ["+e.getMessage()+"].", e);
						}
						
						return null;
					});
			}
			else
			{
				LOGGER.error("Unable to close null cached data source.");
			}
		}
	}
	
	
	
	//==============//
	// List methods //
	//==============//
	
	public int getUnsavedCount() { return (int)this.dataSourceByPosition.size(); }
	
	/** Removes everything from the memory cache and fires the {@link DelayedFullDataSourceSaveCache#onSaveTimeoutAsyncFunc} for each. */
	public void flush()
	{
		Set<Long> keySet = this.dataSourceByPosition.asMap().keySet();
		for (Long pos : keySet)
		{
			ReentrantLock lock = this.saveLockContainer.getLockForPos(pos);
			try
			{
				lock.lock();
				
				this.dataSourceByPosition.invalidate(pos);
			}
			finally
			{
				lock.unlock();
			}
			
		}
	}
	
	
	
	//================//
	// static cleanup //
	//================//
	
	private static void runCleanupLoop()
	{
		while (true)
		{
			try
			{
				try
				{
					Thread.sleep(CLEANUP_CHECK_TIME_IN_MS);
				}
				catch (InterruptedException ignore) { }
				
				SAVE_CACHE_SET.forEach((cacheRef) ->
				{
					DelayedFullDataSourceSaveCache cache = cacheRef.get();
					if (cache == null)
					{
						// shouldn't be necessary, but if we forget to manually close a cache, this will prevent leaking
						SAVE_CACHE_SET.remove(cacheRef);
					}
					else
					{
						cache.dataSourceByPosition.cleanUp();
					}
				});
			}
			catch (Exception e)
			{
				LOGGER.error("Unexpected error in cleanup thread: [" + e.getMessage() + "].", e);
			}
		}
	}
	
	
	
	//================//
	// base overrides //
	//================//
	
	@Override
	public void close()
	{
		// not the fastest way to handle removing,
		// but we shouldn't have more than 20 or so at once
		// so this should be just fine
		SAVE_CACHE_SET.removeIf((cacheRef) -> 
		{
			DelayedFullDataSourceSaveCache cache = cacheRef.get();
			return cache != null && cache.equals(this);
		});
	}
	
	
	
	//================//
	// helper classes //
	//================//
	
	@FunctionalInterface
	public interface ISaveDataSourceFunc
	{
		/** called after the timeout expires */
		CompletableFuture<Void> saveAsync(FullDataSourceV2 inputDataSource);
	}
	
}
