package com.seibel.distanthorizons.core.render.renderer.generic;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.RemovalNotification;
import com.seibel.distanthorizons.core.logging.DhLoggerBuilder;
import org.apache.logging.log4j.Logger;

import java.util.concurrent.ConcurrentMap;

/**
 * @see RenderableBoxGroup
 */
public class RenderBoxArrayCache
{
	private static final Logger LOGGER = DhLoggerBuilder.getLogger();
	
	public static final int ARRAY_LENGTH_WIDTH = 24;
	public static final int ARRAY_ID_WIDTH = 8;
	
	public static final int ARRAY_LENGTH_OFFSET = 0;
	public static final int ARRAY_ID_OFFSET = ARRAY_LENGTH_OFFSET + ARRAY_LENGTH_WIDTH;
	
	public static final int ARRAY_LENGTH_MASK = (int) Math.pow(2, ARRAY_LENGTH_WIDTH) - 1;
	public static final int ARRAY_ID_MASK = (int) Math.pow(2, ARRAY_ID_WIDTH) - 1;
	
	
	private static final ConcurrentMap<Integer, float[]> FLOAT_ARRAY_BY_KEY = CacheBuilder.newBuilder()
			// This number needs to be high enough so that
			// the number of generic object groups won't cause array thrashing.
			// For now 512 should be way more than needed, unless
			// someone adds a boatload of random generic objects.
			.maximumSize(512)
			.removalListener((RemovalNotification<Integer, float[]> notification) -> { /* TODO log a warning if arrays start getting removed, that means we may need to re-think how the caching here works */ })
			.build().asMap();
	private static final ConcurrentMap<Integer, int[]> INT_ARRAY_BY_KEY = CacheBuilder.newBuilder()
			.maximumSize(512)
			.removalListener((RemovalNotification<Integer, int[]> notification) -> {})
			.build().asMap();
	
	
	
	//============//
	// get arrays //
	//============//
	
	/** 
	 * The ID parameter is to prevent returning the same array
	 * multiple times when the same length is requested.
	 */
	public static float[] getCachedFloatArray(int length, int id)
	{
		int key = encodeKey(length, id);
		return FLOAT_ARRAY_BY_KEY.computeIfAbsent(key, (newKey) ->
		{
			int newLength = getLengthFromKey(newKey);
			return new float[newLength];
		});
	}
	public static int[] getCachedIntArray(int length, int id)
	{
		int key = encodeKey(length, id);
		return INT_ARRAY_BY_KEY.computeIfAbsent(key, (newKey) ->
		{
			int newLength = getLengthFromKey(newKey);
			return new int[newLength];
		});
	}
	
	
	
	//==============//
	// key encoding //
	//==============//
	
	private static int encodeKey(int arrayLength, int id)
	{
		if (id > Byte.MAX_VALUE)
		{
			throw new IndexOutOfBoundsException("The array's ID can only be 8 bytes long.");
		}
		
		int data = 0;
		data |= (arrayLength & ARRAY_LENGTH_MASK);
		data |= (id & ARRAY_ID_MASK) << ARRAY_ID_OFFSET;
		return data;
	}
	
	private static int getLengthFromKey(int key) { return (key & ARRAY_LENGTH_MASK); }
	private static int getIdFromKey(int key) { return ((key >> ARRAY_ID_OFFSET) & ARRAY_ID_MASK); }
	
}
