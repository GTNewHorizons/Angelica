package com.seibel.distanthorizons.core.util.ratelimiting;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Limits concurrent tasks based on a given limit supplier. <br>
 * If the limit of concurrent tasks is exceeded, acquisitions will fail and the provided failure handler will be called instead.
 * @param <TFailObj> Type of the object used as context for the failure handler.
 */
public class SupplierBasedConcurrencyLimiter<TFailObj>
{
	private final Supplier<Integer> maxConcurrentTasksSupplier;
	private final Consumer<TFailObj> onFailureConsumer;
	
	private final AtomicInteger pendingTasks = new AtomicInteger();
	
	
	
	//=============//
	// constructor //
	//=============//
	
	public SupplierBasedConcurrencyLimiter(Supplier<Integer> maxConcurrentTasksSupplier, Consumer<TFailObj> onFailureConsumer)
	{
		this.maxConcurrentTasksSupplier = maxConcurrentTasksSupplier;
		this.onFailureConsumer = onFailureConsumer;
	}
	
	
	
	//===============//
	// lock handling //
	//===============//
	
	@SuppressWarnings("BooleanMethodIsAlwaysInverted")
	public boolean tryAcquire(TFailObj context)
	{
		if (this.pendingTasks.incrementAndGet() > this.maxConcurrentTasksSupplier.get())
		{
			this.pendingTasks.decrementAndGet();
			this.onFailureConsumer.accept(context);
			return false;
		}
		
		return true;
	}
	
	public void release() { this.pendingTasks.decrementAndGet(); }
	
	
}
