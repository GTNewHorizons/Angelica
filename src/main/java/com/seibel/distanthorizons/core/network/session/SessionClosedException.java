package com.seibel.distanthorizons.core.network.session;

import java.io.IOException;

/** The exception thrown if DH's networking session has been shut down. */
public class SessionClosedException extends IOException
{
    public SessionClosedException(String message) { super(message); }
	
}