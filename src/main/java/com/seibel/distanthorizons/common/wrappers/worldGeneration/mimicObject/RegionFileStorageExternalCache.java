package com.seibel.distanthorizons.common.wrappers.worldGeneration.mimicObject;

import com.seibel.distanthorizons.common.wrappers.worldGeneration.BatchGenerationEnvironment;
import com.seibel.distanthorizons.core.logging.DhLoggerBuilder;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.storage.RegionFile;
import net.minecraft.world.level.chunk.storage.RegionFileStorage;
import org.apache.logging.log4j.Logger;

import org.jetbrains.annotations.Nullable;
import java.io.DataInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.locks.ReentrantLock;

#if MC_VER >= MC_1_20_6
import net.minecraft.world.level.chunk.storage.RegionStorageInfo;
#endif

/** 
 * Shouldn't be used when the C2ME mod is present,
 * otherwise there may be potential file corruption.
 * When C2ME is present use (via MC ServerLevel) level.getChunkSource().chunkMap.worker#loadAsync()
 * instead.
 */
public class RegionFileStorageExternalCache implements AutoCloseable
{
	private static final Logger LOGGER = DhLoggerBuilder.getLogger();
	
	/** Can be null due to the C2ME mod */
	@Nullable
	public final RegionFileStorage storage;
	public static final int MAX_CACHE_SIZE = 16;
	
	public static boolean regionCacheNullPointerWarningSent = false;
	
	/**
	 * Present to reduce the chance that we accidentally break underlying MC code that isn't thread safe, 
	 * specifically: "it.unimi.dsi.fastutil.longs.Long2ObjectLinkedOpenHashMap.getAndMoveToFirst()"
	 */
	ReentrantLock getRegionFileLock = new ReentrantLock();
	
	
	
	
	
	private final ConcurrentLinkedQueue<RegionFileCache> regionFileCache = new ConcurrentLinkedQueue<>();
	
	
	
	public RegionFileStorageExternalCache(RegionFileStorage storage) { this.storage = storage; }
	
	@Nullable
	public RegionFile getRegionFile(ChunkPos pos) throws IOException
	{
		if (this.storage == null)
		{
			if (!regionCacheNullPointerWarningSent)
			{
				regionCacheNullPointerWarningSent = true;
				LOGGER.warn("Unable to access Minecraft's chunk cache. This may be due to another mod changing said cache. DH will be unable to access any Minecraft chunk data until said mod is removed.");
			}
			
			return null;
		}
		
		
		
		long posLong = ChunkPos.asLong(pos.getRegionX(), pos.getRegionZ());
		RegionFile rFile = null;
		
		// Check vanilla cache
		int retryCount = 0;
		int maxRetryCount = 8;
		while (retryCount < maxRetryCount)
		{
			retryCount++;
			
			try
			{
				this.getRegionFileLock.lock();
				
				#if MC_VER == MC_1_16_5 || MC_VER == MC_1_17_1
				rFile = this.storage.getRegionFile(pos);
				
				// keeping the region cache size low helps prevent concurrency issues
				if (this.storage.regionCache.size() > 150) // max 256
				{
					RegionFile removedFile = this.storage.regionCache.removeLast();
					if (removedFile != null)
					{
						removedFile.close();
					}
				}
				#else
				rFile = this.storage.regionCache.getOrDefault(posLong, null);	
				#endif
				
				break;
			}
			catch (ArrayIndexOutOfBoundsException e)
			{
				#if MC_VER == MC_1_16_5 || MC_VER == MC_1_17_1
				// the file just wasn't cached
				break;
				#else
				// potential concurrency issue, wait a second and try to get the file again
				try
				{
					Thread.sleep(250);
				}
				catch (InterruptedException ignored)
				{
				}
				#endif
			}
			catch (NullPointerException e)
			{
				// Can sometimes happen when other mods modify the region cache system (IE C2ME)
				// instead of blowing up, just use DH's cache instead
				
				if (!regionCacheNullPointerWarningSent)
				{
					regionCacheNullPointerWarningSent = true;
					LOGGER.warn("Unable to access Minecraft's chunk cache. This may be due to another mod changing said cache. Falling back to DH's internal cache.");
				}
				
				break;
			}
			finally
			{
				this.getRegionFileLock.unlock();
			}
		}
		
		if (retryCount >= maxRetryCount)
		{
			BatchGenerationEnvironment.LOAD_LOGGER.warn("Concurrency issue detected when getting region file for chunk at [" + pos + "].");
		}
		
		
		if (rFile != null)
		{
			return rFile;
		}
		
		// Then check our custom cache
		for (RegionFileCache cache : this.regionFileCache)
		{
			if (cache.pos == posLong)
			{
				return cache.file;
			}
		}
		
		// Otherwise, check if file exist, and if so, add it to the cache
		Path storageFolderPath;
		#if MC_VER == MC_1_16_5 || MC_VER == MC_1_17_1
		storageFolderPath = this.storage.folder.toPath();
		#else
		storageFolderPath = this.storage.folder;
		#endif
		
		if (!Files.exists(storageFolderPath))
		{
			return null;
		}
		
		Path regionFilePath = storageFolderPath.resolve("r." + pos.getRegionX() + "." + pos.getRegionZ() + ".mca");
		#if MC_VER == MC_1_16_5 || MC_VER == MC_1_17_1
		rFile = new RegionFile(regionFilePath.toFile(), storageFolderPath.toFile(), false);
		#elif MC_VER <= MC_1_20_4
		rFile = new RegionFile(regionFilePath, storageFolderPath, false);
		#else
		rFile = new RegionFile(new RegionStorageInfo("level", null, "level type"), regionFilePath, storageFolderPath, false);
		#endif
		
		this.regionFileCache.add(new RegionFileCache(ChunkPos.asLong(pos.getRegionX(), pos.getRegionZ()), rFile));
		while (this.regionFileCache.size() > MAX_CACHE_SIZE)
		{
			this.regionFileCache.poll().file.close();
		}
		
		return rFile;
	}
	
	
	@Nullable
	public CompoundTag read(ChunkPos pos) throws IOException
	{
		RegionFile file = this.getRegionFile(pos);
		if (file == null)
		{
			return null;
		}
		
		
		try (DataInputStream stream = file.getChunkDataInputStream(pos))
		{
			if (stream == null)
			{
				return null;
			}
			
			return NbtIo.read(stream);
		}
		catch (Throwable e)
		{
			return null;
		}
	}
	
	
	@Override
	public void close() throws IOException
	{
		RegionFileCache cache;
		while ((cache = this.regionFileCache.poll()) != null)
		{
			cache.file.close();
		}
	}
	
	
	
	//================//
	// helper classes //
	//================//
	
	private static class RegionFileCache
	{
		public final long pos;
		public final RegionFile file;
		
		public RegionFileCache(long pos, RegionFile file)
		{
			this.pos = pos;
			this.file = file;
		}
		
	}
	
}
