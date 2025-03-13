package com.seibel.distanthorizons.core.util.objects;

import java.util.Arrays;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * This can be used for easily profiling methods to get the average execution time. <br>
 * This is a thread safe implementation. 
 */
public class RollingAverage
{
	private final double[] values;
	private final int maxSize;
	
	private int currentSize = 0;
	private int index = 0;
	private double sum = 0.0;
	private final Lock arrayLock = new ReentrantLock();
	
	
	
	//=============//
	// constructor //
	//=============//
	
	public RollingAverage(int size)
	{
		if (size <= 0)
		{
			throw new IllegalArgumentException("Size must be greater than 0");
		}
		
		this.maxSize = size;
		this.values = new double[size];
	}
	
	
	
	//=======//
	// input //
	//=======//
	
	public void addValue(double value)
	{
		this.arrayLock.lock();
		try
		{
			// Subtract the oldest value from the sum
			this.sum -= this.values[this.index];
			// Update the buffer with the new value
			this.values[this.index] = value;
			// Add the new value to the sum
			this.sum += value;
			// Move to the next index
			this.index = (this.index + 1) % this.maxSize;
			
			this.currentSize = Math.max(this.index+1, this.currentSize);
		}
		finally
		{
			this.arrayLock.unlock();
		}
	}
	
	public void clear()
	{
		this.arrayLock.lock();
		try
		{
			this.sum = 0;
			this.index = 0;
			this.currentSize = 0;
			Arrays.fill(this.values, 0);
		}
		finally
		{
			this.arrayLock.unlock();
		}
	}
	
	
	
	//========//
	// output //
	//========//
	
	/** Gets the current rolling average. */
	public double getAverage()
	{
		this.arrayLock.lock();
		try
		{
			return (this.sum / this.currentSize);
		}
		finally
		{
			this.arrayLock.unlock();
		}
	}
	/** rounded to two decimals*/
	public String getAverageRoundedString() { return String.format("%.2f", this.getAverage()); }
	
	
	
	//================//
	// base overrides //
	//================//
	
	@Override 
	public String toString() { return "avg: ["+this.getAverageRoundedString()+"], count: ["+this.currentSize+"], max count: ["+this.maxSize+"]."; }
	
}

