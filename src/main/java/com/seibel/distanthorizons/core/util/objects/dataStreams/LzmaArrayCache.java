package com.seibel.distanthorizons.core.util.objects.dataStreams;

import com.seibel.distanthorizons.core.logging.DhLoggerBuilder;
import it.unimi.dsi.fastutil.ints.Int2ReferenceArrayMap;
import org.apache.logging.log4j.Logger;
import org.tukaani.xz.ArrayCache;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.function.IntUnaryOperator;
import java.util.concurrent.atomic.AtomicInteger;

/** 
 * LZMA requires a custom object to cache it's backend arrays. 
 *
 * TODO there's a lot of duplicate code in this class since it has logic for both
 *  int[]'s and byte[]'s.
 */
public class LzmaArrayCache extends ArrayCache
{
	private static final Logger LOGGER = DhLoggerBuilder.getLogger();
	
	/**
	 * In James' testing the byte and int caches only ever had to store 2 and 4 arrays respectively.
	 * With the in mind we could take a few shortcuts, but if that changes then we need to be 
	 * notified as it might cause issues with the current logic.
	 */
	public static final int WARN_CACHE_LENGTH_EXCEEDED = 10;
	
	public static final AtomicInteger MAX_INT_CACHE_LENGTH_REF = new AtomicInteger(WARN_CACHE_LENGTH_EXCEEDED);
	public static final AtomicInteger MAX_BYTE_CACHE_LENGTH_REF = new AtomicInteger(WARN_CACHE_LENGTH_EXCEEDED);
	
	
	public final IntUnaryOperator maxByteCacheSizeUnaryOperator = (x) -> Math.max(this.byteCache.size(), x);
	public final IntUnaryOperator maxIntCacheSizeUnaryOperator = (x) -> Math.max(this.intCache.size(), x);
	
	/** 
	 * generally only 2 items long <br>
	 * {@link Int2ReferenceArrayMap} can be used since the cache should only be a few items long.
	 * If the array ends up being longer then this design will need to be changed.
	 */
	public final Int2ReferenceArrayMap<ArrayList<byte[]>> byteCache = new Int2ReferenceArrayMap<>();
	/** generally only 4 items long */
	public final Int2ReferenceArrayMap<ArrayList<int[]>> intCache = new Int2ReferenceArrayMap<>();
	
	
	
	//=============//
	// byte arrays //
	//=============//
	
	@Override
	public byte[] getByteArray(int size, boolean fillWithZeros) 
	{
		ArrayList<byte[]> cacheList = this.byteCache.computeIfAbsent(size, (newSize) -> new ArrayList<>(4));
		if (cacheList.isEmpty())
		{
			return new byte[size];
		}
		
		byte[] array = cacheList.remove(cacheList.size()-1);
		if (array == null)
		{
			return new byte[size];
		}
		// the array needs to be cleared to prevent accidentally sending dirty data
		Arrays.fill(array, (byte)0);
		return array;
	}
	
	@Override
	public void putArray(byte[] array) 
	{
		int size = array.length;
		this.byteCache.computeIfAbsent(size, (newSize) -> new ArrayList<>());
		this.byteCache.get(size).add(array);
		
		
		if (this.byteCache.size() > WARN_CACHE_LENGTH_EXCEEDED)
		{
			int previousMax = MAX_BYTE_CACHE_LENGTH_REF.getAndUpdate(this.maxByteCacheSizeUnaryOperator);
			int newMax = MAX_BYTE_CACHE_LENGTH_REF.get();
			if (newMax > previousMax)
			{
				LOGGER.warn("LZMA byte array cache expected size exceeded. Expected max length ["+WARN_CACHE_LENGTH_EXCEEDED+"], actual length ["+this.byteCache.size()+"].");
			}
		}
	}
	
	
	
	//============//
	// int arrays //
	//============//
	
	@Override
	public int[] getIntArray(int size, boolean fillWithZeros) 
	{
		ArrayList<int[]> cacheList = this.intCache.computeIfAbsent(size, (newSize) -> new ArrayList<>(4));
		if (cacheList.size() == 0)
		{
			return new int[size];
		}
		
		int[] array = cacheList.remove(cacheList.size()-1);
		if (array == null)
		{
			return new int[size];
		}
		// the array needs to be cleared to prevent accidentally sending dirty data
		Arrays.fill(array, (byte)0);
		return array;
	}
	
	@Override
	public void putArray(int[] array) 
	{
		int size = array.length;
		this.intCache.computeIfAbsent(size, (newSize) -> new ArrayList<>());
		this.intCache.get(size).add(array);
		
		
		if (this.intCache.size() > WARN_CACHE_LENGTH_EXCEEDED)
		{
			int previousMax = MAX_INT_CACHE_LENGTH_REF.getAndUpdate(this.maxIntCacheSizeUnaryOperator);
			int newMax = MAX_INT_CACHE_LENGTH_REF.get();
			if (newMax > previousMax)
			{
				LOGGER.warn("LZMA int array cache expected size exceeded. Expected max length ["+WARN_CACHE_LENGTH_EXCEEDED+"], actual length ["+this.intCache.size()+"].");
			}
		}
	}
	
	
}
