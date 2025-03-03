package com.seibel.distanthorizons.core.util.threading;

import com.seibel.distanthorizons.core.config.Config;
import com.seibel.distanthorizons.core.config.types.ConfigEntry;
import com.seibel.distanthorizons.core.logging.DhLoggerBuilder;
import com.seibel.distanthorizons.core.util.objects.RollingAverage;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantLock;

public class PriorityTaskPicker
{
	private static final Logger LOGGER = DhLoggerBuilder.getLogger();
	
	
	private final ConfigEntry<Integer> threadCountConfig = Config.Common.MultiThreading.numberOfThreads;
	
	private final RateLimitedThreadPoolExecutor threadPoolExecutor = new RateLimitedThreadPoolExecutor(
			this.threadCountConfig.getMax(),
			new DhThreadFactory("PriorityTaskPicker", Thread.MIN_PRIORITY, false),
			new ArrayBlockingQueue<>(this.threadCountConfig.getMax())
	);
	
	// List of executors
	private final ArrayList<Executor> executors = new ArrayList<>();
	
	// Lock to ensure task picking logic is thread-safe
	private final ReentrantLock taskPickerLock = new ReentrantLock();
	// Tracks the number of active threads
	private final AtomicInteger occupiedThreads = new AtomicInteger(0);
	
	private final AtomicBoolean isShutDownRef = new AtomicBoolean(false);
	
	
	
	//==================//
	// executor methods //
	//==================//
	
	/**
	 * Creates an executor.
	 *
	 * @return a newly created Executor
	 */
	public Executor createExecutor()
	{
		Executor executor = new Executor();
		this.executors.add(executor);
		return executor;
	}
	
	/**
	 * Tries to start the next task by iterating over executors in the queue.
	 * Ensures thread limits are respected and only one thread iterates over the executorQueue at a time.
	 */
	private void tryStartNextTask()
	{
		if (this.taskPickerLock.tryLock())
		{
			try
			{
				for (Executor executor : (Iterable<? extends Executor>) this.executors.stream().sorted(Comparator.comparingLong(executor -> executor.totalRuntimeNanos.get()))::iterator)
				{
					TrackedRunnable task;
					
					while (this.occupiedThreads.get() < this.threadCountConfig.get() && (task = executor.tasks.poll()) != null)
					{
						try
						{
							// Attempt to start another task
							this.threadPoolExecutor.execute(task);
							
							// Update variables related to task status
							this.occupiedThreads.getAndIncrement();
							executor.runningTasks.getAndIncrement();
						}
						catch (RejectedExecutionException e)
						{
							if (this.isShutDownRef.get())
							{
								// Clear executor's tasks since we no longer expect anything to execute
								// Tasks from other executors will be cleared by the outer for loop
								executor.tasks.clear();
							}
							else
							{
								throw e;
							}
						}
					}
				}
			}
			finally
			{
				this.taskPickerLock.unlock();
				
				// If someone else manages to pick up a lock before us, we'll leave early, and they will do our work
			}
		}
	}
	
	/** Shuts down the thread pool immediately, stopping all tasks. */
	public void shutdown()
	{
		LOGGER.info("Shutting down PriorityTaskPicker thread pool...");
		this.isShutDownRef.set(true);
		
		try
		{
			this.threadPoolExecutor.shutdown();
			if (!this.threadPoolExecutor.awaitTermination(5, TimeUnit.SECONDS))
			{
				this.threadPoolExecutor.shutdownNow();
			}
		}
		catch (InterruptedException e)
		{
			throw new RuntimeException(e);
		}
	}
	
	
	
	//================//
	// helper classes //
	//================//
	
	public class Executor extends AbstractExecutorService
	{
		private final Queue<TrackedRunnable> tasks = new ConcurrentLinkedQueue<>();
		
		private final AtomicInteger runningTasks = new AtomicInteger(0);
		private final AtomicInteger completedTasks = new AtomicInteger(0);
		private final RollingAverage runTimeInMsRollingAverage = new RollingAverage(200);
		private final AtomicLong totalRuntimeNanos = new AtomicLong(0);
		
		
		@Override
		public void execute(@NotNull Runnable command)
		{
			this.tasks.add(new TrackedRunnable(this, command));
			
			// Attempt to pick up the task immediately
			PriorityTaskPicker.this.tryStartNextTask();
		}
		
		
		public int getQueueSize() { return this.tasks.size(); }
		public int getPoolSize() { return PriorityTaskPicker.this.threadCountConfig.get(); }
		
		public int getRunningTaskCount() { return this.runningTasks.get(); }
		public int getCompletedTaskCount() { return this.completedTasks.get(); }
		/** Will return NaN if nothing has been submitted yet */
		public double getAverageRunTimeInMs() { return this.runTimeInMsRollingAverage.getAverage(); }
		
		
		/** The passed in {@link Runnable} must be exactly the same as the one passed into {@link PriorityTaskPicker.Executor#execute(Runnable)} */
		public void remove(@NotNull Runnable command) { this.tasks.removeIf(trackedRunnable -> trackedRunnable.command == command); }
		
		@Override
		public void shutdown() { throw new UnsupportedOperationException(); }
		
		@Override
		public @NotNull List<Runnable> shutdownNow() { throw new UnsupportedOperationException(); }
		
		@Override
		public boolean isShutdown() { return false; }
		@Override
		public boolean isTerminated() { return false; }
		
		@Override
		public boolean awaitTermination(long timeout, @NotNull TimeUnit unit) { return false; }
		
	}
	
	/** used so we can {@link PriorityTaskPicker.Executor#remove(Runnable)} using the original {@link Runnable} */
	private class TrackedRunnable implements Runnable
	{
		private final Executor executor;
		
		/** the runnable passed into {@link PriorityTaskPicker.Executor#execute(Runnable)} */
		public final Runnable command;
		
		public TrackedRunnable(Executor executor, Runnable command)
		{
			this.executor = executor;
			this.command = command;
		}
		
		@Override
		public void run()
		{
			long startTime = System.nanoTime();
			try
			{
				this.command.run();
			}
			finally
			{
				long timeElapsed = System.nanoTime() - startTime;
				this.executor.runTimeInMsRollingAverage.addValue(TimeUnit.NANOSECONDS.toMillis(timeElapsed));
				
				// Update variables related to task status
				PriorityTaskPicker.this.occupiedThreads.getAndDecrement();
				this.executor.runningTasks.getAndDecrement();
				this.executor.completedTasks.getAndIncrement();
				this.executor.totalRuntimeNanos.addAndGet(timeElapsed);
				
				// Attempt to start another task
				PriorityTaskPicker.this.tryStartNextTask();
			}
		}
		
	}
	
}
