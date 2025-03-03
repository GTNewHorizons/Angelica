package com.seibel.distanthorizons.core.api.external.methods.data;

import com.seibel.distanthorizons.api.interfaces.data.IDhApiTerrainDataCache;
import com.seibel.distanthorizons.core.dataObjects.fullData.sources.FullDataSourceV2;
import it.unimi.dsi.fastutil.longs.Long2ReferenceOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongSet;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.Nullable;

import java.lang.ref.SoftReference;

public class DhApiTerrainDataCache implements IDhApiTerrainDataCache
{
	private final Object modificationLock = new Object();
	private Long2ReferenceOpenHashMap<SoftReference<FullDataSourceV2>> posToFullDataRef = new Long2ReferenceOpenHashMap<>();
	
	private static final Logger LOGGER = LogManager.getLogger(DhApiTerrainDataCache.class.getSimpleName());
	
	
	
	//==================//
	// internal methods //
	//==================//
	
	public void add(long pos, FullDataSourceV2 dataSource)
	{
		synchronized (this.modificationLock)
		{
			this.posToFullDataRef.put(pos, new SoftReference<>(dataSource));
		}
	}
	
	@Nullable
	public FullDataSourceV2 get(long pos)
	{
		synchronized (this.modificationLock)
		{
			SoftReference<FullDataSourceV2> ref = this.posToFullDataRef.get(pos);
			if (ref != null)
			{
				return ref.get();
			}
			else
			{
				return null;
			}
		}
	}
	
	
	
	//=============//
	// API methods //
	//=============//
	
	@Override 
	public void clear()
	{
		synchronized (this.modificationLock)
		{
			LongSet keySet = this.posToFullDataRef.keySet();
			for (long pos : keySet)
			{
				SoftReference<FullDataSourceV2> dataRef = this.posToFullDataRef.remove(pos);
				if (dataRef != null)
				{
					FullDataSourceV2 dataSource = dataRef.get();
					if (dataSource != null)
					{
						try
						{
							dataSource.close();
						}
						catch (Exception e)
						{
							LOGGER.warn("Unable to close data source, error: [" + e.getMessage() + "].", e);
						}
					}
				}
			}
		}
	}
	
	
	
}
