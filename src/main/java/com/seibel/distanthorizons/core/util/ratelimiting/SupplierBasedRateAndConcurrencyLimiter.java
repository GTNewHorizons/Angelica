package com.seibel.distanthorizons.core.util.ratelimiting;

import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Combines both a {@link SupplierBasedRateLimiter} and a {@link SupplierBasedConcurrencyLimiter} for combined limiting.
 * 
 * @param <TFailObj> Type of the object used as context for the failure handler.
 * @see SupplierBasedRateLimiter
 * @see SupplierBasedConcurrencyLimiter
 */
public class SupplierBasedRateAndConcurrencyLimiter<TFailObj>
{
	private final SupplierBasedRateLimiter<TFailObj> rateLimiter;
	private final SupplierBasedConcurrencyLimiter<TFailObj> concurrencyLimiter;
	
	
	
	//=============//
	// constructor //
	//=============//
	
	public SupplierBasedRateAndConcurrencyLimiter(Supplier<Integer> maxRateSupplier, Consumer<TFailObj> onFailureConsumer)
	{
		this.rateLimiter = new SupplierBasedRateLimiter<>(maxRateSupplier, onFailureConsumer);
		this.concurrencyLimiter = new SupplierBasedConcurrencyLimiter<>(maxRateSupplier, onFailureConsumer);
	}
	
	
	
	//===============//
	// lock handling //
	//===============//
	
	@SuppressWarnings("BooleanMethodIsAlwaysInverted")
	public boolean tryAcquire(TFailObj context)
	{
		if (!this.concurrencyLimiter.tryAcquire(context))
		{
			return false;
		}
		
		if (!this.rateLimiter.tryAcquire(context))
		{
			this.concurrencyLimiter.release();
			return false;
		}
		
		return true;
	}
	
	public void release() { this.concurrencyLimiter.release(); }
	
}
