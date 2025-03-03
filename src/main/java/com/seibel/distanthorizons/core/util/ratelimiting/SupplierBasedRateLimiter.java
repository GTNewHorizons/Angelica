package com.seibel.distanthorizons.core.util.ratelimiting;

import com.google.common.annotations.Beta;
import com.google.common.util.concurrent.RateLimiter;

import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Limits rate of tasks based on a given limit supplier. <br>
 * If the rate limit is exceeded, acquisitions will fail and the provided failure handler will be called instead.
 * @param <TFailObject> Type of the object sent to the failure handler.
 *
 * @apiNote <b>UnstableApiUsage</b> warning suppression is due to Google having marked {@link RateLimiter} as {@link Beta}
 *          for the past 5 years. Considering how long it's been "unstable" we probably don't have anything to worry about. <br>
 *          <a href="https://github.com/google/guava/issues/2797"> https://github.com/google/guava/issues/2797 </a>
 */
@SuppressWarnings("UnstableApiUsage")
public class SupplierBasedRateLimiter<TFailObject>
{
	private final Supplier<Integer> maxRateSupplier;
	private final Consumer<TFailObject> onFailureConsumer;
	
	private final RateLimiter rateLimiter = RateLimiter.create(/*permits per second*/Double.POSITIVE_INFINITY);
	
	
	
	//=============//
	// constructor //
	//=============//
	
	public SupplierBasedRateLimiter(Supplier<Integer> maxRateSupplier) { this(maxRateSupplier, ignored -> { }); }
	public SupplierBasedRateLimiter(Supplier<Integer> maxRateSupplier, Consumer<TFailObject> onFailureConsumer)
	{
		this.maxRateSupplier = maxRateSupplier;
		this.onFailureConsumer = onFailureConsumer;
	}
	
	
	
	//==================//
	// lock acquisition //
	//==================//
	
	@SuppressWarnings("BooleanMethodIsAlwaysInverted")
	public boolean tryAcquire(TFailObject failContext) { return this.tryAcquire(failContext, 1); }
	public boolean tryAcquire() { return this.tryAcquire(null, 1); }
	public boolean tryAcquire(TFailObject failContext, int permits)
	{
		this.rateLimiter.setRate(this.maxRateSupplier.get());
		
		if (!this.rateLimiter.tryAcquire(permits))
		{
			this.onFailureConsumer.accept(failContext);
			return false;
		}
		
		return true;
	}
	
	/** can be used to prevent any locks from being acquired for one second */
	public void acquireAll() { int ignored = this.acquireOrDrain(Integer.MAX_VALUE); }
	/** @return the number of locks acquired */
	public int acquireOrDrain(int requestedPermits)
	{
		this.rateLimiter.setRate(this.maxRateSupplier.get());
		
		int acquiredCount = 0;
		while (requestedPermits > 0 && this.rateLimiter.tryAcquire())
		{
			acquiredCount++;
			requestedPermits--;
		}
		return acquiredCount;
	}
	
}
