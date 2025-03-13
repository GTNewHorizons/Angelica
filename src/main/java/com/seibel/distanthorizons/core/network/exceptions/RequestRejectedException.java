package com.seibel.distanthorizons.core.network.exceptions;

/** 
 * Fired if the client attempts an operation currently forbidden by the server. <Br>
 * For example attempting to request LODs when world generation is disabled on the server. 
 */
public class RequestRejectedException extends Exception
{
	public RequestRejectedException(String message) { super(message); }
	
}
