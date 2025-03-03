package com.seibel.distanthorizons.core.util.objects;

/**
 * Thrown when a DH handled resource or datasource isn't in the
 * correct format. <Br><Br>
 * 
 * IE: a blocklight with the value -4 when it should be between 0 and 15.
 */
public class DataCorruptedException extends Exception
{
	/** replaces this exception's stack trace with the incoming one */
	public DataCorruptedException(Exception e)
	{
		super(e.getMessage());
		this.setStackTrace(e.getStackTrace());
		this.addSuppressed(e);
	}
	
	public DataCorruptedException(String message) { super(message); }
	
	public DataCorruptedException(String message, Exception e)
	{
		super(message);
		this.setStackTrace(e.getStackTrace());
		this.addSuppressed(e);
	}
	
}
