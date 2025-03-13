package com.seibel.distanthorizons.core.util;

import java.util.concurrent.locks.ReentrantLock;

/**
 * Can be used to allow an infinite number of keys to
 * map to a finite number of locks.
 * Useful when loading/modifying positional LOD data and wanting to
 * prevent concurrent modifications. <br>
 * 
 * Based on the stack overflow post: https://stackoverflow.com/a/45909920
 */
public class KeyedLockContainer<TKey>
{
	protected final ReentrantLock[] lockArray;
	
	
	//==============//
	// constructors //
	//==============//
	
	public KeyedLockContainer()
	{
		// the lock array's length is 2x the number of CPU cores so the number of collisions
		// should be relatively low without having too many extra locks
		this(Runtime.getRuntime().availableProcessors() * 2);
	}
	public KeyedLockContainer(int lockCount)
	{
		this.lockArray = new ReentrantLock[lockCount];
		for (int i = 0; i < lockCount; i++)
		{
			this.lockArray[i] = new ReentrantLock();
		}
	}
	
	
	
	//=========//
	// getters //
	//=========//
	
	public ReentrantLock getLockForPos(TKey key) { return this.lockArray[Math.abs(key.hashCode()) % this.lockArray.length]; }
	
}
