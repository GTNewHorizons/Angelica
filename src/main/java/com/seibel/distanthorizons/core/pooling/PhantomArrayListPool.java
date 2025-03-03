package com.seibel.distanthorizons.core.pooling;

import com.seibel.distanthorizons.core.api.internal.ClientApi;
import com.seibel.distanthorizons.core.config.Config;
import com.seibel.distanthorizons.core.logging.DhLoggerBuilder;
import com.seibel.distanthorizons.core.logging.f3.F3Screen;
import com.seibel.distanthorizons.core.util.ThreadUtil;
import com.seibel.distanthorizons.core.util.objects.Pair;
import com.seibel.distanthorizons.coreapi.ModInfo;
import com.seibel.distanthorizons.coreapi.util.StringUtil;
import it.unimi.dsi.fastutil.bytes.ByteArrayList;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.shorts.ShortArrayList;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.ref.PhantomReference;
import java.lang.ref.Reference;
import java.lang.ref.ReferenceQueue;
import java.lang.ref.SoftReference;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * DH uses a lot of potentially large arrays of {@link Byte}s and {@link Long}s.
 * In order to reduce Garbage Collector (GC) stuttering and array allocation overhead
 * we pool these arrays when possible. <br><br>
 * 
 * How pooled arrays can be returned: <br>
 * 1. <b> Closing the {@link PhantomArrayListParent} </b> <br>
 * The fastest and most efficient method of returning pooled arrays
 * is to call {@link AutoCloseable#close()}. <br><br>
 * 
 * 2. <b> {@link PhantomArrayListParent} Garbage Collection </b> <br>
 * Some objects are used across many different threads and
 * cleanly closing them is impossible, so when the {@link PhantomArrayListParent}
 * is automatically garbage collected we recover and recycle any
 * arrays it checked out.
 * This is less efficient since it may allow a lot of additional arrays to
 * be created while we wait for the garbage collector to run, but 
 * does prevent any leaks from {@link PhantomArrayListParent} that weren't closed.
 * 
 * <br><br>
 * <strong>Use Notes: </strong><br>
 * If possible all checkouts for a given pool should be the same size,
 * since {@link PhantomArrayListCheckout}'s are shared, getting the same size checkout each time
 * prevents accidentally returning a larger checkout than necessary, which wastes memory.
 */
public class PhantomArrayListPool
{
	private static final Logger LOGGER = DhLoggerBuilder.getLogger();
	
	/** 
	 * the recycler thread needs to be triggered relatively frequently to prevent
	 * build up of GC'ed arrays.
	 * However, some JVM's will wait a while before collecting lost objects,
	 * so in general it is still much better to use the try-finally.
	 */
	private static final int PHANTOM_REF_CHECK_TIME_IN_MS = 5_000;
	private static final ThreadPoolExecutor RECYCLER_THREAD = ThreadUtil.makeSingleDaemonThreadPool("Phantom Array Recycler");
	private static final ArrayList<PhantomArrayListPool> POOL_LIST = new ArrayList<>();
	
	/** if enabled the number of GC'ed arrays will be logged */
	private static final boolean LOG_ARRAY_RECOVERY = ModInfo.IS_DEV_BUILD;
	
	
	private static boolean lowMemoryWarningLogged = false; 
	
	
	
	/** used for debugging and tracking what the pool contains */
	public final String name;
	/** 
	 * Getting stack traces is very slow.
	 * If we know which pool is leaking objects we can enable tracking for that specific
	 * pool and prevent slow-downs in other pools.
	 */
	public final boolean logGarbageCollectedStacks;
	
	public final ConcurrentHashMap<Reference<? extends PhantomArrayListParent>, PhantomArrayListCheckout>
			phantomRefToCheckout = new ConcurrentHashMap<>();
	public final ReferenceQueue<PhantomArrayListParent> phantomRefQueue = new ReferenceQueue<>();
	
	
	private final ConcurrentLinkedQueue<SoftReference<PhantomArrayListCheckout>> pooledCheckoutsRefs = new ConcurrentLinkedQueue<>();
	
	/** counts how many byte arrays have been created by this pool */
	private final AtomicInteger totalByteArrayCountRef = new AtomicInteger(0);
	/** counts how many short arrays have been created by this pool */
	private final AtomicInteger totalShortArrayCountRef = new AtomicInteger(0);
	/** counts how many long arrays have been created by this pool */
	private final AtomicInteger totalLongArrayCountRef = new AtomicInteger(0);
	
	/** used for debugging, represents an estimate for how many bytes the byte[] pool contains */
	private long lastBytePoolSizeInBytes = -1;
	/** used for debugging, represents an estimate for how many bytes the short[] pool contains */
	private long lastShortPoolSizeInBytes = -1;
	/** used for debugging, represents an estimate for how many bytes the long[] pool contains */
	private long lastLongPoolSizeInBytes = -1;
	
	/** used for debugging, represents an estimate for how many byte[]'s are currently in this pool*/
	private int lastBytePoolCount = 0;
	/** used for debugging, represents an estimate for how many short[]'s are currently in this pool*/
	private int lastShortPoolCount = 0;
	/** used for debugging, represents an estimate for how many long[]'s are currently in this pool*/
	private int lastLongPoolCount = 0;
	/** used for debugging, represents an estimate for how many checkouts are currently in this pool*/
	private int lastCheckoutPoolCount = 0;
	
	/** For pools backed by {@link SoftReference}'s we may need to decrease the size when elements are garbage collected */
	private boolean clearLastRefPoolSizes = false;
	
	
	
	//==============//
	// constructors //
	//==============//
	
	// shared setup used by all pools
	static
	{
		RECYCLER_THREAD.execute(() -> runPhantomReferenceCleanupLoop());
	}
	
	
	public PhantomArrayListPool(String name) { this(name, false); }
	public PhantomArrayListPool(String name, boolean logGarbageCollectedStacks)
	{
		POOL_LIST.add(this);
		this.name = name;
		this.logGarbageCollectedStacks = logGarbageCollectedStacks;
	}
	
	
	
	//==============//
	// get checkout //
	//==============//
	
	/** 
	 * If possible all checkouts for a given pool should be the same size,
	 * since {@link PhantomArrayListCheckout}'s are shared, returning the same size
	 * prevents accidentally returning a larger checkout than necessary, which wastes memory.
	 */
	public PhantomArrayListCheckout checkoutArrays(int byteArrayCount, int shortArrayCount, int longArrayCount)
	{
		PhantomArrayListCheckout checkout = null;
		while (checkout == null)
		{
			SoftReference<PhantomArrayListCheckout> checkoutRef = this.pooledCheckoutsRefs.poll();
			if (checkoutRef == null)
			{
				// pool is empty, create new checkout
				checkout = new PhantomArrayListCheckout(this);
			}
			else
			{
				checkout = checkoutRef.get();
				if (checkout != null)
				{
					// use pooled checkout
				}
				else
				{
					// this reference is pointing to null,
					// the checkout must have been garbage collected,
					// that means we don't have enough memory
					if (!lowMemoryWarningLogged)
					{
						lowMemoryWarningLogged = true;
						
						// orange text
						String message = "\u00A76" + "Distant Horizons: Insufficient memory detected." + "\u00A7r \n" +
								"This may cause stuttering or crashing. \n" +
								"Potential causes: \n" +
								"1. your allocated memory isn't high enough \n" +
								"2. your DH CPU preset is too high \n" +
								"3. your DH quality preset is too high";
						
						LOGGER.warn(message);
						if (Config.Common.Logging.Warning.showPoolInsufficientMemoryWarning.get())
						{
							ClientApi.INSTANCE.showChatMessageNextFrame(message);
						}
					}
					
					this.clearLastRefPoolSizes = true;
				}
			}
		}
		
		
		// get any missing arrays
		
		// byte
		for (int i = checkout.getByteArrayCount(); i < byteArrayCount; i++)
		{
			checkout.addByteArrayList(this.createEmptyByteArrayList());
		}
		
		// short
		for (int i = checkout.getShortArrayCount(); i < shortArrayCount; i++)
		{
			checkout.addShortArrayList(this.createEmptyShortArrayList());
		}
		
		// long
		for (int i = checkout.getLongArrayCount(); i < longArrayCount; i++)
		{
			checkout.addLongArrayListRef(this.createEmptyLongArrayList());
		}
		
		return checkout;
	}
	
	
	// array constructors //
	
	private ByteArrayList createEmptyByteArrayList()
	{
		//LOGGER.error("created new byte array");
		this.totalByteArrayCountRef.getAndIncrement();
		return new ByteArrayList(0);
	}
	private ShortArrayList createEmptyShortArrayList()
	{
		//LOGGER.error("created new short array");
		this.totalShortArrayCountRef.getAndIncrement();
		return new ShortArrayList(0);
	}
	private LongArrayList createEmptyLongArrayList()
	{
		//LOGGER.error("created new long array");
		this.totalLongArrayCountRef.getAndIncrement();
		return new LongArrayList(0);
	}
	
	
	
	//==================//
	// phantom recovery //
	//==================//
	
	private static void runPhantomReferenceCleanupLoop()
	{
		while (true)
		{
			// these arrays are stored here so they don't have to be re-allocated each loop
			ArrayList<Pair<String, AtomicInteger>> allocationStackTraceCountPairList = new ArrayList<>();
			
			try
			{
				try
				{
					Thread.sleep(PHANTOM_REF_CHECK_TIME_IN_MS);
				}
				catch (InterruptedException ignore) { }
				
				
				for (int poolIndex = 0; poolIndex < POOL_LIST.size(); poolIndex++)
				{
					PhantomArrayListPool pool = POOL_LIST.get(poolIndex);
					
					int returnedByteArrayCount = 0;
					int returnedShortArrayCount = 0;
					int returnedLongArrayCount = 0;
					int checkoutCount = 0;
					
					allocationStackTraceCountPairList.clear();
					
					Reference<? extends PhantomArrayListParent> phantomRef = pool.phantomRefQueue.poll();
					while (phantomRef != null)
					{
						// return the pooled arrays
						PhantomArrayListCheckout checkout = pool.phantomRefToCheckout.remove(phantomRef);
						if (checkout != null)
						{
							returnedByteArrayCount += checkout.getByteArrayCount();
							returnedShortArrayCount += checkout.getShortArrayCount();
							returnedLongArrayCount += checkout.getLongArrayCount();
							checkoutCount++;
							pool.returnCheckout(checkout);
							
							if (pool.logGarbageCollectedStacks
									&& checkout.allocationStackTrace != null) // stack trace shouldn't be null, but just in case
							{
								putAndIncrementTrackingString(checkout.allocationStackTrace, allocationStackTraceCountPairList);
							}
						}
						else
						{
							// shouldn't happen, but just in case
							LOGGER.warn("Pool: ["+pool.name+"]. Unable to find checkout for phantom reference ["+phantomRef+"], arrays will need to be recreated.");
						}
						
						phantomRef = pool.phantomRefQueue.poll();
					}
					
					if (LOG_ARRAY_RECOVERY || pool.logGarbageCollectedStacks)
					{
						// we only want to log when something has been returned
						if (checkoutCount != 0
								|| returnedByteArrayCount != 0
								|| returnedShortArrayCount != 0
								|| returnedLongArrayCount != 0)
						{
							LOGGER.warn("Pool: ["+ pool.name+"] phantom recovery. Returned checkouts:["+F3Screen.NUMBER_FORMAT.format(checkoutCount)+"], byte:["+F3Screen.NUMBER_FORMAT.format(returnedByteArrayCount)+"], short:["+F3Screen.NUMBER_FORMAT.format(returnedShortArrayCount)+"], long:["+F3Screen.NUMBER_FORMAT.format(returnedLongArrayCount)+"].");
							
							// log stack traces if present
							if (pool.logGarbageCollectedStacks)
							{
								// high numbers first
								allocationStackTraceCountPairList.sort((a, b) -> Integer.compare(b.second.get(), a.second.get()));
								
								StringBuilder stringBuilder = new StringBuilder();
								for (int j = 0; j < allocationStackTraceCountPairList.size(); j++)
								{
									int count = allocationStackTraceCountPairList.get(j).second.get();
									String stack = allocationStackTraceCountPairList.get(j).first;
									
									stringBuilder.append(count).append(". ").append(stack).append("\n");
								}
								LOGGER.warn("Stacks: ["+ allocationStackTraceCountPairList.size()+"]\n" + stringBuilder.toString());
							}
						}
					}
					
					// since this is just for debugging it only needs to be recalculated once in a while
					pool.recalculateSizeForDebugging();
				}
			}
			catch (Exception e)
			{
				LOGGER.error("Unexpected error in phantom pool return thread, error: [" + e.getMessage() + "].", e);
			}
		}
	}
	/**
	 * This was separated out so it could be used for other string pair lists.
	 * James originally had an idea to add a shorter static string
	 * ID to each allocated {@link PhantomArrayListCheckout} as a simpler version of the stack trace,
	 * however it became a bit more difficult and messy than he wanted to deal with, so for now we just
	 * have the stack trace.
	 */
	private static void putAndIncrementTrackingString(
			String key,
			ArrayList<Pair<String, AtomicInteger>> allocationStackTraceCountPairList)
	{
		// sequential search, for the number of elements we're dealing with (less than 20)
		// this should be sufficiently fast
		boolean pairFound = false;
		for (int i = 0; i < allocationStackTraceCountPairList.size(); i++)
		{
			Pair<String, AtomicInteger> possiblePair = allocationStackTraceCountPairList.get(i);
			if (possiblePair.first.equals(key))
			{
				possiblePair.second.getAndIncrement();
				pairFound = true;
				break;
			}
		}
		
		if (!pairFound)
		{
			allocationStackTraceCountPairList.add(new Pair<>(key, new AtomicInteger(1)));
		}
	}
	
	
	
	//=================//
	// return checkout //
	//=================//
	
	public void returnParentPhantomRef(@NotNull PhantomReference<PhantomArrayListParent> parentRef)
	{
		try
		{
			parentRef.clear();
			// will be null if the this parent has already been returned
			PhantomArrayListCheckout checkout = this.phantomRefToCheckout.remove(parentRef);
			this.returnCheckout(checkout);
		}
		catch (Exception e)
		{
			LOGGER.error("Unable to close Phantom Array, error: ["+e.getMessage()+"].", e);
		}
	}
	public void returnCheckout(@Nullable PhantomArrayListCheckout checkout)
	{ 
		if (checkout == null)
		{
			throw new IllegalArgumentException("Null phantom checkout, object is being closed multiple times.");
		}
		
		SoftReference<PhantomArrayListCheckout> checkoutRef = checkout.ownerSoftReference;
		this.pooledCheckoutsRefs.add(checkoutRef);
		
		//LOGGER.info("Returned ["+checkout.byteArrayLists.size()+"/"+this.pooledByteArrays.size()+"] bytes and ["+checkout.longArrayLists.size()+"/"+this.pooledLongArrays.size()+"] longs.");\
	}

	
	
	//===============//
	// debug methods //
	//===============//
	
	public static void addDebugMenuStringsToListForCombinedPools(List<String> messageList)
	{
		int totalByteArrayCount = 0, totalShortArrayCount = 0, totalLongArrayCount = 0;
		int pooledByteArraySize = 0, pooledShortArraySize = 0, pooledLongArraySize = 0;
		long lastBytePoolSizeInBytes = 0, lastShortPoolSizeInBytes = 0, lastLongPoolSizeInBytes = 0;
		
		for (int i = 0; i < POOL_LIST.size(); i++)
		{
			PhantomArrayListPool pool = POOL_LIST.get(i);
			
			totalByteArrayCount += pool.totalByteArrayCountRef.get();
			totalShortArrayCount += pool.totalShortArrayCountRef.get();
			totalLongArrayCount += pool.totalLongArrayCountRef.get();
			
			pooledByteArraySize += pool.lastBytePoolCount;
			pooledShortArraySize += pool.lastShortPoolCount;
			pooledLongArraySize += pool.lastLongPoolCount;
			
			lastBytePoolSizeInBytes += pool.lastBytePoolSizeInBytes;
			lastShortPoolSizeInBytes += pool.lastShortPoolSizeInBytes;
			lastLongPoolSizeInBytes += pool.lastLongPoolSizeInBytes;
		}
		
		addDebugMenuStringsToList(messageList,
			"Combined",
			totalByteArrayCount, totalShortArrayCount, totalLongArrayCount,
			pooledByteArraySize, pooledShortArraySize, pooledLongArraySize,
			lastBytePoolSizeInBytes, lastShortPoolSizeInBytes, lastLongPoolSizeInBytes
		);
	}
	
	public static void addDebugMenuStringsToListForSeparatePools(List<String> messageList)
	{
		for (int i = 0; i < POOL_LIST.size(); i++)
		{
			PhantomArrayListPool pool = POOL_LIST.get(i);
			pool.addDebugMenuStringsToList(messageList);
		}
	}
	
	public void addDebugMenuStringsToList(List<String> messageList)
	{
		addDebugMenuStringsToList(messageList,
			this.name,
			this.totalByteArrayCountRef.get(), this.totalShortArrayCountRef.get(), this.totalLongArrayCountRef.get(),
			this.lastBytePoolCount, this.lastShortPoolCount, this.lastLongPoolCount,
			this.lastBytePoolSizeInBytes, this.lastShortPoolSizeInBytes, this.lastLongPoolSizeInBytes
		);
	}
	private static void addDebugMenuStringsToList(List<String> messageList, 
			String name,
			int totalByteArrayCount, int totalShortArrayCount, int totalLongArrayCount,
			int numbOfByteArraysInPool, int numbOfShortArraysInPool, int numbOfLongArraysInPool,
			long lastBytePoolSizeInBytes, long lastShortPoolSizeInBytes, long lastLongPoolSizeInBytes)
	{
		// total (all time created) count
		String byteArrayTotalCount = F3Screen.NUMBER_FORMAT.format(totalByteArrayCount);
		String shortArrayTotalCount = F3Screen.NUMBER_FORMAT.format(totalShortArrayCount);
		String longArrayTotalCount = F3Screen.NUMBER_FORMAT.format(totalLongArrayCount);
		
		// inactive items in pool
		String bytePoolCount = F3Screen.NUMBER_FORMAT.format(numbOfByteArraysInPool);
		String shortPoolCount = F3Screen.NUMBER_FORMAT.format(numbOfShortArraysInPool);
		String longPoolCount = F3Screen.NUMBER_FORMAT.format(numbOfLongArraysInPool);
		
		// pool byte size
		String bytePoolSizeInBytes = (lastBytePoolSizeInBytes != -1)
				? " ~" + StringUtil.convertBytesToHumanReadable(lastBytePoolSizeInBytes)
				: "";
		String shortPoolSizeInBytes = (lastShortPoolSizeInBytes != -1)
				? " ~" + StringUtil.convertBytesToHumanReadable(lastShortPoolSizeInBytes)
				: "";
		String longPoolSizeInBytes = (lastLongPoolSizeInBytes != -1)
				? " ~" + StringUtil.convertBytesToHumanReadable(lastLongPoolSizeInBytes)
				: "";
		
		
		messageList.add(name + " - Pools:");
		if (totalByteArrayCount != 0)
		{
			messageList.add("byte[]: " + bytePoolCount + "/" + byteArrayTotalCount + bytePoolSizeInBytes);
		}
		if (totalShortArrayCount != 0)
		{
			messageList.add("short[]: " + shortPoolCount + "/" + shortArrayTotalCount + shortPoolSizeInBytes);
		}
		if (totalLongArrayCount != 0)
		{
			messageList.add("long[]: " + longPoolCount + "/" + longArrayTotalCount + longPoolSizeInBytes);
		}
	}
	
	
	/**
	 *  shouldn't be called on the render thread as it can
	 *  take 10's of milliseconds to complete.
	 */
	public void recalculateSizeForDebugging()
	{
		long bytePoolByteSize = 0;
		long shortPoolByteSize = 0;
		long longPoolByteSize = 0;
		
		int bytePoolCount = 0;
		int shortPoolCount = 0;
		int longPoolCount = 0;
		
		
		// checkouts //
		for (SoftReference<PhantomArrayListCheckout> pooledCheckoutRef : this.pooledCheckoutsRefs)
		{
			PhantomArrayListCheckout pooledCheckout = pooledCheckoutRef.get();
			if (pooledCheckout == null)
			{
				continue;
			}
			
			bytePoolByteSize += estimateMemoryUsage(pooledCheckout.getAllByteArrays(), Byte.BYTES);
			bytePoolCount += pooledCheckout.getAllByteArrays().size();
			shortPoolByteSize += estimateMemoryUsage(pooledCheckout.getAllShortArrays(), Short.BYTES);
			shortPoolCount += pooledCheckout.getAllShortArrays().size();
			longPoolByteSize += estimateMemoryUsage(pooledCheckout.getAllLongArrays(), Long.BYTES);
			longPoolCount += pooledCheckout.getAllLongArrays().size();
		}
		
		
		// clear old values if something was garbage collected
		if (this.clearLastRefPoolSizes)
		{
			this.lastBytePoolSizeInBytes = 0;
			this.lastShortPoolSizeInBytes = 0;
			this.lastLongPoolSizeInBytes = 0;
			this.clearLastRefPoolSizes = false;
		}
		
		this.lastCheckoutPoolCount = this.pooledCheckoutsRefs.size();
		
		// byte //
		// math.max is used since the pool should only grow until a soft reference is freed, 
		// and it's easier to understand if this constantly grows instead of jumping around
		this.lastBytePoolSizeInBytes = Math.max(bytePoolByteSize, this.lastBytePoolSizeInBytes);
		this.lastBytePoolCount = bytePoolCount;
		
		// short //
		this.lastShortPoolSizeInBytes = Math.max(shortPoolByteSize, this.lastShortPoolSizeInBytes);
		this.lastShortPoolCount = shortPoolCount;
		
		// long //
		this.lastLongPoolSizeInBytes = Math.max(longPoolByteSize, this.lastLongPoolSizeInBytes);
		this.lastLongPoolCount = longPoolCount;
	}
	
	private static <T extends Collection<?>> long estimateMemoryUsage(Iterable<T> pool, long elementSizeInBytes)
	{
		long longByteSize = 0;
		for (T array : pool)
		{
			// Object overhead + capacity of underlying array * size of Long (8 bytes)
			long overhead = Byte.SIZE * 4;
			
			long elementCount = getCollectionCount(array);
			long arraySize = elementCount * elementSizeInBytes;
			longByteSize += overhead + arraySize;
		}
		return longByteSize;
	}
	private static <T extends Collection<?>> long estimateRefMemoryUsage(ConcurrentLinkedQueue<SoftReference<T>> pool, long elementSizeInBytes)
	{
		long longByteSize = 0;
		for (SoftReference<T> arrayRef : pool)
		{
			// Object overhead + capacity of underlying array * size of Long (8 bytes)
			long overhead = Byte.SIZE * 4;
			T array = arrayRef.get();
			if (array == null)
			{
				continue;
			}
			
			long elementCount = getCollectionCount(array);
			long arraySize = elementCount * elementSizeInBytes;
			longByteSize += overhead + arraySize;
		}
		return longByteSize;
	}
	private static long getCollectionCount(@NotNull Collection<?> array)
	{
		long elementCount;
		if (array instanceof ByteArrayList)
		{
			elementCount = ((ByteArrayList)array).elements().length;
		}
		else if (array instanceof ShortArrayList)
		{
			elementCount = ((ShortArrayList)array).elements().length;
		}
		else if (array instanceof LongArrayList)
		{
			elementCount = ((LongArrayList)array).elements().length;
		}
		else
		{
			throw new UnsupportedOperationException("Not implemented for type ["+array.getClass().getSimpleName()+"].");
		}
		
		return elementCount;
	}
	
	
	
}
