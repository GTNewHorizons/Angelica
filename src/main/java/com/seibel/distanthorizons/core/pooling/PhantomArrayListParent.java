package com.seibel.distanthorizons.core.pooling;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.lang.ref.PhantomReference;

/**
 * Any object that needs pooled arrays should extend this object.
 * This handles setting up and tracking the necessary {@link PhantomReference}'s
 * needed to make sure none of the arrays are leaked. 
 * However, if possible, the implementing object should be closed
 * instead via a try-resource block as that will reduce the number of
 * unnecessary arrays created.
 * 
 * @see PhantomArrayListCheckout
 * @see PhantomArrayListPool
 */
public abstract class PhantomArrayListParent implements AutoCloseable
{
	private static final Logger LOGGER = LogManager.getLogger();
	
	
	private final PhantomArrayListPool phantomArrayListPool;
	private final PhantomReference<PhantomArrayListParent> phantomReference;

	/** 
	 * It's recommended to set this as null after the child's constructor 
	 * finishes to show the pooled arrays have all been accessed 
	 */
	protected final PhantomArrayListCheckout pooledArraysCheckout; 
	
	
	
	//=============//
	// constructor //
	//=============//
	
	/** The Array counts can be 0 or greater. */
	public PhantomArrayListParent(PhantomArrayListPool phantomArrayListPool, int byteArrayCount, int shortArrayCount, int longArrayCount) 
	{
		if (byteArrayCount < 0 || shortArrayCount < 0 || longArrayCount < 0)
		{
			throw new IllegalArgumentException("Can't get a negative number of pooled arrays.");
		}
		
		this.phantomArrayListPool = phantomArrayListPool;
		this.phantomReference = new PhantomReference<>(this, this.phantomArrayListPool.phantomRefQueue);
		this.pooledArraysCheckout = this.phantomArrayListPool.checkoutArrays(byteArrayCount, shortArrayCount, longArrayCount);
		this.phantomArrayListPool.phantomRefToCheckout.put(this.phantomReference, this.pooledArraysCheckout);
	}
	
	
	
	//================//
	// base overrides //
	//================//
	
	@Override 
	public void close() { this.phantomArrayListPool.returnParentPhantomRef(this.phantomReference); }
	
	
}
