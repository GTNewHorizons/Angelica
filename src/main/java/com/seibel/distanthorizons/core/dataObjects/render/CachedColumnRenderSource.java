package com.seibel.distanthorizons.core.dataObjects.render;

import com.google.common.cache.Cache;
import com.seibel.distanthorizons.core.dataObjects.fullData.sources.FullDataSourceV2;
import com.seibel.distanthorizons.core.dataObjects.transformers.FullDataToRenderDataTransformer;
import com.seibel.distanthorizons.core.file.fullDatafile.FullDataSourceProviderV2;
import com.seibel.distanthorizons.core.util.threading.PriorityTaskPicker;
import com.seibel.distanthorizons.core.util.threading.ThreadPoolUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;

/** 
 * Wrapper for {@link ColumnRenderSource} that handles reference counting
 * and cache tracking.
 */
public class CachedColumnRenderSource implements AutoCloseable
{
	/** an externally handled future that will complete once the {@link CachedColumnRenderSource#columnRenderSource} has finished loading */
	public final CompletableFuture<CachedColumnRenderSource> loadFuture;
	/** will be null initially, should be non-null once the corresponding load future is done */
	@Nullable
	public ColumnRenderSource columnRenderSource = null;
	
	private final AtomicInteger referenceCount;
	private final Cache<Long, CachedColumnRenderSource> cachedRenderSourceByPos;
	private final ReentrantLock getterLock;
	
	
	
	//=============//
	// constructor //
	//=============//
	
	public CachedColumnRenderSource(
			@NotNull CompletableFuture<CachedColumnRenderSource> loadFuture,
			@NotNull ReentrantLock getterLock,
			@NotNull Cache<Long, CachedColumnRenderSource> cachedRenderSourceByPos)
	{
		this.loadFuture = loadFuture;
		this.getterLock = getterLock;
		this.referenceCount = new AtomicInteger(1);
		this.cachedRenderSourceByPos = cachedRenderSourceByPos;
	}
	
	
	
	//====================//
	// reference counting //
	//====================//
	
	public void markInUse() { this.referenceCount.getAndIncrement(); }
	
	
	
	//================//
	// base overrides //
	//================//
	
	/** 
	 * Will be called multiple times,
	 * however it will only close the underlying data once
	 * all references have closed.
	 */
	@Override 
	public void close() throws IllegalStateException
	{
		try
		{
			// lock to prevent other threads for accessing the cache if we invalidate it
			this.getterLock.lock();
			
			// should only happen if something goes wrong up-stream
			if (this.columnRenderSource == null)
			{
				return;
			}
			
			
			// only close once everyone is done with this datasource
			int refCount = this.referenceCount.decrementAndGet();
			if (refCount == 0)
			{
				this.cachedRenderSourceByPos.invalidate(this.columnRenderSource.pos);
				this.columnRenderSource.close();
			}
			else if (refCount < 0)
			{
				throw new IllegalStateException("Render source ["+this.columnRenderSource.pos+"] reference count incorrect. Object already closed.");
			}
		}
		finally
		{
			this.getterLock.unlock();
		}
	}
	
}
