package com.seibel.distanthorizons.coreapi.util.jar;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.net.URLDecoder;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.TimeUnit;

/**
 * Attempts to delete a given file path repeatedly until the file is unlocked. <br>
 * NOTE: don't move this class. If this class is moved, then old jars won't be able to find it.
 * 
 * @author coolgi 
 */
public class DeleteOnUnlock
{
	public static int SUCCESS_EXIT_CODE = 0;
	public static int FAIL_EXIT_CODE = 1;
	public static int ERROR_EXIT_CODE = 2;
	
	/** How long to wait after attempting once (milliseconds) */
	private static final int ATTEMPT_SPEED_IN_MS = 100;
	/** 
	 * How many minutes of attempting before it stops <br>
	 * If the file isn't unlocked by then, the computer must be extremely slow or there is a bigger issue.
	 */
	private static final int TIMEOUT_IN_MINUTES = 60;
	
	/** can be null */
	private static FileWriter logFileWriter;
	
	
	
	/**
	 * <strong>args[0]</strong> the file path to delete. Should be encoded in UTF-8.
	 * <strong>args[1]</strong> the file path to write logs to, can be null.
	 */
	public static void main(String[] args)
	{
		String filePathToDelete = args[0];
		
		// can be null, should only be used when debugging
		String logFilePath = null;
		if (args.length >= 2)
		{
			logFilePath = args[1]; // example: "C:/Users/James/Desktop/delete.log"
		}
		
		
		try
		{
			//===============//
			// logging setup //
			//===============//
			
			// General logging note:
			// system.err/out will only show up when debugging this program, 
			// when running DH as a whole it won't be shown.
			// This is why file logging is necessary
			
			// does nothing if no log path was given
			if (logFilePath != null && logFilePath.trim().length() != 0)
			{
				// create the file writer
				File logFile = new File(logFilePath);
				logFileWriter = new FileWriter(logFile, true);
				
				// create the log file if necessary
				try
				{
					if (!logFile.createNewFile() && !logFile.exists())
					{
						System.err.println("Unable to create log file at: [" + logFile.getPath() + "]");
					}
				}
				catch (IOException e)
				{
					System.err.println(e.getMessage());
				}
			}
			
			
			
			//====================//
			// file deletion loop //
			//====================//
			
			File fileToDelete = new File(URLDecoder.decode(filePathToDelete, "UTF-8"));
			log("starting deletion loop... Attempting to delete: ["+fileToDelete.getPath()+"].");
			
			for (int i = 0; i < (60 / ((float) ATTEMPT_SPEED_IN_MS /1000) ) * TIMEOUT_IN_MINUTES; i++)
			{
				log("delete attempt ["+i+"]");
				
				// If the file can be renamed then it is unlocked and can be deleted
				if (fileToDelete.exists() && fileToDelete.renameTo(fileToDelete))
				{
					try
					{
						Files.delete(fileToDelete.toPath());
						
						if (!fileToDelete.exists())
						{
							log("success");
							break;
						}
						else
						{
							// shouldn't normally happen, but just in case
							log("failed to delete without error");
						}
					}
					catch (NoSuchFileException e)
					{
						// accidental success, the file is no longer there
						log("no file found");
						break;
					}
					catch (Exception e)
					{
						log("failed to delete with error: "+e.getMessage());
					}
				}
				
				TimeUnit.MILLISECONDS.sleep(ATTEMPT_SPEED_IN_MS);
			}
			
			
			//==================//
			// cleanup and exit //
			//==================//
			
			boolean programSuccess = !fileToDelete.exists();
			log("delete program completed " + (programSuccess ? "successfully" : "unsuccessfully"));
			System.exit(programSuccess ? SUCCESS_EXIT_CODE : FAIL_EXIT_CODE);
		}
		catch (Exception e)
		{
			String stackTrace = "";
			for (StackTraceElement stackTraceElement : e.getStackTrace())
			{
				stackTrace += stackTraceElement.toString() + "\n";
			}
			String message = "Unexpected exception occurred: " + e.getMessage() + "\n\n" + stackTrace;
			log(message);
			System.err.println(message);
			System.exit(ERROR_EXIT_CODE);
		}
	}
	
	
	/** writes the given message to the log file if a log file is present. */
	private static void log(String message)
	{
		if (logFileWriter != null)
		{
			try
			{
				String localDateTime = LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE) + " " + LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_TIME);
				logFileWriter.write(localDateTime + " - " + message + "\n");
				
				// necessary to make sure the log file is written to
				logFileWriter.flush();
			}
			catch (IOException e)
			{
				// Note: this will only show up when debugging this program, when running DH as a whole it won't be shown
				System.err.println("Error writing to log: "+e.getMessage());
				e.printStackTrace();
			}
		}
	}
	
}
