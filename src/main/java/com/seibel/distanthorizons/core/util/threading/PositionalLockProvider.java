package com.seibel.distanthorizons.core.util.threading;

import com.seibel.distanthorizons.core.logging.DhLoggerBuilder;
import com.seibel.distanthorizons.core.pos.DhSectionPos;
import com.seibel.distanthorizons.core.util.ThreadUtil;
import org.apache.logging.log4j.Logger;

import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReentrantLock;

/** 
 * Handles creating and destroying {@link ReentrantLock}'s for 
 * a given {@link DhSectionPos}.
 * This is necessary since we need an unlimited number of locks
 * when handling data updating, but we don't want to infinitely create locks.
 * This provider will create/destroy locks as necessary given the current requirements by the file handlers.
 */
public class PositionalLockProvider
{
	private static final Logger LOGGER = DhLoggerBuilder.getLogger();
	
	private static final ThreadPoolExecutor LOCK_CLEANUP_THREAD = ThreadUtil.makeSingleThreadPool("Positional Lock Cleanup");
	private static final int CLEANUP_THREAD_MAX_FREQUENCY_IN_MS = 1000;
	
	/** How long a lock can be unused before it is eligible for deletion */
	private static final long UNUSED_LOCK_TIMEOUT_IN_MS = 10_000; // 10 seconds
	private static final int MAX_NUMBER_OF_LOCKS = 100;
	
	
	private final ConcurrentHashMap<Long, ExpiringLock> lockByPos = new ConcurrentHashMap<>();
	
	private final AtomicBoolean lockRemovalThreadRunning = new AtomicBoolean(false);
	
	
	
	//=============//
	// constructor //
	//=============//
	
	public PositionalLockProvider() {}
	
	
	
	//========//
	// getter //
	//========//
	
	public ReentrantLock getLock(long pos)
	{
		return this.lockByPos.compute(pos, (ignorePos, lock) ->
		{
			if (lock == null)
			{
				lock = new ExpiringLock();
			}
			
			if (this.lockByPos.size() > MAX_NUMBER_OF_LOCKS
				&& this.lockRemovalThreadRunning.getAndSet(true))
			{
				LOCK_CLEANUP_THREAD.execute(this::removeExpiredLocks);
			}
			
			return lock;
		});
	}
	private void removeExpiredLocks()
	{
		try
		{
			// we don't need to trigger this every time the lock count is over the limit
			Thread.sleep(CLEANUP_THREAD_MAX_FREQUENCY_IN_MS);
			
			// walk over every lock and check which ones need to be removed
			Iterator<Long> keySet = this.lockByPos.keySet().iterator();
			while (keySet.hasNext())
			{
				try
				{
					long currentTime = System.currentTimeMillis();
					
					long pos = keySet.next();
					ExpiringLock lock = this.lockByPos.get(pos);
					
					// don't try removing a lock that's currently in use
					if (lock.tryLockWithoutUpdatingExpirationTime())
					{
						if (currentTime > lock.expirationTimeInMs)
						{
							this.lockByPos.remove(pos);
							//LOGGER.info("removed lock: "+DhSectionPos.toString(pos));
						}
						lock.unlock();
					}
					
				}
				catch (NoSuchElementException ignore) { }
			}
		}
		catch (Exception e)
		{
			LOGGER.error("PositionLockProvider unexpected error when removing expired locks. Error: "+e.getMessage(), e);
		}
		finally
		{
			this.lockRemovalThreadRunning.set(false);
			//LOGGER.info("update lock count: "+this.lockByPos.size());
		}
	}
	
	
	
	//================//
	// helper classes //
	//================//
	
	/** a lock that tracks when it was last used */
	private static class ExpiringLock extends ReentrantLock
	{
		/** 
		 * Indicates the system time in Milliseconds when this lock has expired. <br>
		 * Should update whenever the lock is used. 
		 */
		public long expirationTimeInMs;
		
		
		
		//=============//
		// constructor //
		//=============//
		
		public ExpiringLock() { this.resetExpirationTime(); }
		
		
		
		//===========//
		// overrides //
		//===========//
		
		@Override 
		public void lock()
		{
			this.resetExpirationTime();
			super.lock();
		}
		@Override 
		public boolean tryLock()
		{
			this.resetExpirationTime();
			return super.tryLock();
		}
		@Override
		public boolean tryLock(long timeout, TimeUnit unit) throws InterruptedException
		{
			this.resetExpirationTime();
			return super.tryLock(timeout, unit);
		}
		
		@Override 
		public void unlock()
		{
			this.resetExpirationTime();
			super.unlock();
		}
		
		
		
		//================//
		// helper methods //
		//================//
		
		/** should be called whenever this lock is needed */
		private void resetExpirationTime()
		{
			this.expirationTimeInMs = System.currentTimeMillis() + UNUSED_LOCK_TIMEOUT_IN_MS;
		}
		
		public boolean tryLockWithoutUpdatingExpirationTime() { return super.tryLock(); }
		
		
	}
	
}
