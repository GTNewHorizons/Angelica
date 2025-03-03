/*
 *    This file is part of the Distant Horizons mod
 *    licensed under the GNU LGPL v3 License.
 *
 *    Copyright (C) 2020 James Seibel
 *
 *    This program is free software: you can redistribute it and/or modify
 *    it under the terms of the GNU Lesser General Public License as published by
 *    the Free Software Foundation, version 3.
 *
 *    This program is distributed in the hope that it will be useful,
 *    but WITHOUT ANY WARRANTY; without even the implied warranty of
 *    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *    GNU Lesser General Public License for more details.
 *
 *    You should have received a copy of the GNU Lesser General Public License
 *    along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.seibel.distanthorizons.core.jar.updater;

import com.seibel.distanthorizons.api.enums.config.EDhApiUpdateBranch;
import com.seibel.distanthorizons.core.config.Config;
import com.seibel.distanthorizons.core.dependencyInjection.SingletonInjector;
import com.seibel.distanthorizons.core.jar.JarUtils;
import com.seibel.distanthorizons.core.jar.ModJarInfo;
import com.seibel.distanthorizons.core.jar.installer.GitlabGetter;
import com.seibel.distanthorizons.core.jar.installer.ModrinthGetter;
import com.seibel.distanthorizons.core.jar.installer.WebDownloader;
import com.seibel.distanthorizons.core.logging.DhLoggerBuilder;
import com.seibel.distanthorizons.core.logging.f3.F3Screen;
import com.seibel.distanthorizons.core.wrapperInterfaces.IVersionConstants;
import com.seibel.distanthorizons.coreapi.ModInfo;
import com.seibel.distanthorizons.coreapi.util.StringUtil;
import com.seibel.distanthorizons.coreapi.util.jar.DeleteOnUnlock;
import org.apache.logging.log4j.Logger;
import org.lwjgl.util.tinyfd.TinyFileDialogs;

import javax.swing.*;
import java.awt.*;
import java.io.*;
import java.net.URLEncoder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.text.NumberFormat;
import java.util.Collections;
import java.util.Comparator;
import java.util.zip.CRC32;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Used to update the mod automatically
 *
 * @author coolGi
 */
public class SelfUpdater
{
	private static final Logger LOGGER = DhLoggerBuilder.getLogger();
	
	/** As we cannot delete(or replace) the jar while the mod is running, we just have this to delete it once the game closes */
	public static boolean deleteOldJarOnJvmShutdown = false;
	
	private static String currentJarSha = "";
	private static String mcVersion = SingletonInjector.INSTANCE.get(IVersionConstants.class).getMinecraftVersion();
	
	public static File newFileLocation;
	
	
	/**
	 * Should be called on the game starting.
	 * (After the config has been initialised)
	 *
	 * @return Whether it should open the update ui
	 */
	public static boolean onStart()
	{
		LOGGER.info("Checking for Distant Horizons update");
		
		try
		{
			currentJarSha = JarUtils.getFileChecksum(MessageDigest.getInstance("SHA"), JarUtils.jarFile);
		}
		catch (Exception e)
		{
			LOGGER.error("Unable to get existing jar checksum, error: ["+e.getMessage()+"].", e);
			return false;
		}
		
		boolean returnValue = false;
		try
		{
			EDhApiUpdateBranch updateBranch = EDhApiUpdateBranch.convertAutoToStableOrNightly(Config.Client.Advanced.AutoUpdater.updateBranch.get());
			returnValue = (updateBranch == EDhApiUpdateBranch.STABLE) ? onStableStart() : onNightlyStart();
		}
		catch (Exception e) // Shouldn't be needed, but just in case
		{
			LOGGER.warn(e);
		}
		return returnValue;
	}
	private static boolean onStableStart()
	{
		// Some init stuff
		// We use sha1 to check the version as our versioning system is different to the one on modrinth
		if (!ModrinthGetter.init())
		{
			LOGGER.warn("Unable to find any nightly build pipelines, auto update will be unavailable.");
			return false;
		}
		if (!ModrinthGetter.mcVersions.contains(mcVersion))
		{
			LOGGER.warn("Minecraft version ["+ mcVersion +"] is not findable on Modrinth, only findable versions are ["+ StringUtil.join(",", ModrinthGetter.mcVersions) +"]");
			return false;
		}
		
		try
		{
			newFileLocation = JarUtils.jarFile.getParentFile().toPath().resolve("update").resolve(ModInfo.NAME + "-" + ModrinthGetter.getLatestNameForVersion(mcVersion) + "-" + mcVersion + ".jar").toFile();
		}
		catch (Exception e)
		{
			LOGGER.warn("Unable to get file location to download auto updated file to.", e);
			return false;
		}
		
		// Check the sha's of both our stuff
		if (currentJarSha.equals(ModrinthGetter.getLatestShaForVersion(mcVersion)))
		{
			LOGGER.info("Distant Horizons already up to date.");
			return false;
		}
		if (JarUtils.jarFile == null)
		{
			LOGGER.warn("Unable to get the Distant Horizons jar file, self updating disabled.");
			return false;
		}
		
		
		LOGGER.info("New version (" + ModrinthGetter.getLatestNameForVersion(mcVersion) + ") of Distant Horizons is available");
		if (Config.Client.Advanced.AutoUpdater.enableSilentUpdates.get())
		{
			// Auto-update mod
			updateMod(mcVersion, newFileLocation);
			return false;
		}
		else
		{
			LOGGER.info("Download link: " + ModrinthGetter.getLatestDownloadForVersion(mcVersion));
		}
		return true;
	}
	private static boolean onNightlyStart()
	{
		if (GitlabGetter.INSTANCE.projectPipelines.size() == 0)
		{
			LOGGER.info("Unable to find any nightly build pipelines, auto update will be unavailable.");
			return false;
		}
		com.electronwill.nightconfig.core.Config pipeline = GitlabGetter.INSTANCE.projectPipelines.get(0);
		
		if (!pipeline.get("ref").equals(ModJarInfo.Git_Branch))
		{
			LOGGER.warn("Latest pipeline was found for branch ["+ pipeline.get("ref") +"], but we are on branch ["+ ModJarInfo.Git_Branch +"].");
			return false;
		}
		
		if (!pipeline.get("status").equals("success"))
		{
			LOGGER.warn("Pipeline for branch ["+ ModJarInfo.Git_Branch +"], pipeline ID ["+ pipeline.get("id") +"], has either failed to build, or is still building.");
			return false;
		}
		
		if (!GitlabGetter.INSTANCE.getDownloads(pipeline.get("id")).containsKey(mcVersion))
		{
			LOGGER.warn("Minecraft version ["+ mcVersion +"] is not findable on Gitlab, findable versions are ["+ StringUtil.join(",", GitlabGetter.INSTANCE.getDownloads(pipeline.get("id")).keySet().toArray()) +"].");
			return false;
		}
		
		String latestCommit = pipeline.get("sha");
		try
		{
			newFileLocation = JarUtils.jarFile.getParentFile().toPath().resolve("update").resolve(ModInfo.NAME + "-" + latestCommit + ".jar").toFile();
		}
		catch (Exception e)
		{
			LOGGER.warn("Unable to get file location to download auto updated file to.", e);
			return false;
		}
		
		
		if (ModJarInfo.Git_Commit.equals(latestCommit)) // If we are already on the latest commit, then dont update
		{
			LOGGER.info("Distant Horizons already up to date.");
			return false;
		}
		
		
		LOGGER.info("New version [" + latestCommit + "] of Distant Horizons is available");
		if (Config.Client.Advanced.AutoUpdater.enableSilentUpdates.get())
		{
			// Auto-update mod
			updateMod(mcVersion, newFileLocation);
			return false;
		}
		else
		{
			LOGGER.info("Download link: " + GitlabGetter.getLatestForVersion(mcVersion));
		}
		return true;
	}
	
	
	
	
	public static boolean updateMod()
	{
		String mcVer = SingletonInjector.INSTANCE.get(IVersionConstants.class).getMinecraftVersion();
		return updateMod(
				mcVer,
				newFileLocation
		);
	}
	public static boolean updateMod(String minecraftVersion, File file)
	{
		EDhApiUpdateBranch updateBranch = EDhApiUpdateBranch.convertAutoToStableOrNightly(Config.Client.Advanced.AutoUpdater.updateBranch.get());
		if (updateBranch == EDhApiUpdateBranch.STABLE)
		{
			return updateStableMod(minecraftVersion, file);
		}
		else if (updateBranch == EDhApiUpdateBranch.NIGHTLY)
		{
			return updateNightlyMod(minecraftVersion, file);
		}
		else
		{
			LOGGER.error("Unable to update due to unimplemented update branch ["+updateBranch+"].");
			return false;
		}
	}
	
	public static boolean updateStableMod(String minecraftVersion, File file)
	{
		try
		{
			LOGGER.info("Attempting to auto update Distant Horizons");
			
			Files.createDirectories(file.getParentFile().toPath());
			WebDownloader.downloadAsFile(ModrinthGetter.getLatestDownloadForVersion(minecraftVersion), file);
			
			// Check if the checksum of the downloaded jar is correct (not required, but good to have to prevent corruption or interception)
			if (!JarUtils.getFileChecksum(MessageDigest.getInstance("SHA"), file).equals(ModrinthGetter.getLatestShaForVersion(minecraftVersion)))
			{
				LOGGER.warn("Distant Horizons update checksum failed, aborting install");
				throw new Exception("Checksum failed");
			}
			
			deleteOldJarOnJvmShutdown = true;
			
			String message = "Distant Horizons successfully updated. It will apply on game's relaunch"; 
			LOGGER.info(message);
			new Thread(() -> 
			{
				try
				{
					TinyFileDialogs.tinyfd_messageBox(ModInfo.READABLE_NAME, message, "ok", "info", false);
				}
				catch (Exception ignore) { }
			}).start();
			return true;
		}
		catch (Exception e)
		{
			// delete the update file to prevent issues with a corrupt jar floating around
			try
			{
				Files.deleteIfExists(file.toPath());
			}
			catch (Exception deleteCorruptFileException)
			{
				LOGGER.error("Unable to delete corrupted update file at ["+file.toPath()+"], error: ["+deleteCorruptFileException.getMessage()+"].", deleteCorruptFileException);
			}
			
			
			String message = "Failed to update Distant Horizons to version [" + ModrinthGetter.getLatestNameForVersion(minecraftVersion) + "], error: ["+e.getMessage()+"].";
			
			LOGGER.error(message, e);
			try
			{
				TinyFileDialogs.tinyfd_messageBox(ModInfo.READABLE_NAME, message, "ok", "error", false);
			}
			catch (Exception ignore) { }
			
			return false;
		}
	}
	
	public static boolean updateNightlyMod(String minecraftVersion, File file)
	{
		if (GitlabGetter.INSTANCE.projectPipelines.isEmpty())
		{
			LOGGER.warn("Failed to find any nightly builds for the minecraft version ["+minecraftVersion+"] update canceled.");
			return false;
		}
		
		
		Path mergedZipPath = null;
		try
		{
			LOGGER.info("Attempting to auto update Distant Horizons.");
			
			Files.createDirectories(file.getParentFile().toPath());
			
			mergedZipPath = file.getParentFile().toPath().resolve("merged.zip");
			WebDownloader.downloadAsFile(GitlabGetter.INSTANCE.getDownloads(GitlabGetter.INSTANCE.projectPipelines.get(0).get("id")).get(minecraftVersion), mergedZipPath.toFile());
			
			try (ZipFile zipFile = new ZipFile(mergedZipPath.toFile()))
			{
				ZipEntry zipEntry = 
						Collections.list(zipFile.entries()).stream()
						.max(Comparator.comparingInt(entry -> entry.getName().length()))
						// shouldn't happen, but just in case
						.orElseThrow(() -> new Exception("Unable to find jar in zip. Is the downloaded zip empty?"));
				
				// expected values as defined by the zip
				long expectedCheckSum = zipEntry.getCrc();
				int expectedSize = (int)zipEntry.getSize();
				
				
				// read in the file content
				byte[] buffer = new byte[expectedSize];
				CRC32 crcCheckSumGenerator = new CRC32();
				InputStream inputStream = zipFile.getInputStream(zipEntry);
				
				int byteReadIndex = 0;
				try
				{
					NumberFormat outputFormat = NumberFormat.getNumberInstance();
					
					int nextByte = inputStream.read();
					while (nextByte != -1)
					{
						buffer[byteReadIndex] = (byte) nextByte;
						crcCheckSumGenerator.update(nextByte);
						nextByte = inputStream.read();
						byteReadIndex++;
						
						// TODO it would be better to change this divisor based on the expected size,
						//  so it would always be split up into 100 1% increments
						//  but this will work for now when the expected size is about 17 MB, this will log about 170 times
						if (byteReadIndex % 100_000 == 0)
						{
							LOGGER.info("Decompressing ["+outputFormat.format(((double)byteReadIndex / expectedSize)*100.0)+"]%");
						}
					}
				}
				catch (EOFException ignore) { /* shouldn't happen, but just in case */ }
				
				// confirm we read the whole file
				if (byteReadIndex != expectedSize) // +1 on the index isn't necessary since the readIndex will always end +1 from where it started
				{
					LOGGER.warn("Distant Horizons update decompression failed, aborting install");
					throw new Exception("Decompression failed");
				}
				
				// confirm the checksum is correct (IE we decompressed correctly)
				long actualChecksum = crcCheckSumGenerator.getValue();
				if (actualChecksum != expectedCheckSum)
				{
					LOGGER.warn("Distant Horizons checksum mismatch, aborting install");
					throw new Exception("Checksum Mismatch");
				}
				
				Files.write(file.toPath(), buffer);
			}
			
			Files.deleteIfExists(mergedZipPath);
			
			deleteOldJarOnJvmShutdown = true;
			
			
			String message = "Distant Horizons updated, this will be applied on game restart.";
			LOGGER.info(message);
			new Thread(() ->
			{
				try
				{
					TinyFileDialogs.tinyfd_messageBox(ModInfo.READABLE_NAME, message, "ok", "info", false);
				}
				catch (Exception ignore) { }
			}).start();
			
			return true;
		}
		catch (Exception e)
		{
			// delete the update jar to prevent issues with a corrupt jar floating around
			try
			{
				Files.deleteIfExists(file.toPath());
			}
			catch (Exception deleteCorruptFileException)
			{
				LOGGER.error("Unable to delete corrupted update jar file at ["+file.toPath()+"], error: ["+deleteCorruptFileException.getMessage()+"].", deleteCorruptFileException);
			}
			
			// delete the update zip so we can clean up
			try
			{
				if (mergedZipPath != null)
				{
					Files.deleteIfExists(mergedZipPath);
				}
			}
			catch (Exception deleteCorruptFileException)
			{
				LOGGER.error("Unable to delete corrupted update zip file at ["+mergedZipPath+"], error: ["+deleteCorruptFileException.getMessage()+"].", deleteCorruptFileException);
			}
			
			
			
			String message = "Failed to update [" + ModInfo.READABLE_NAME + "] to version [" + GitlabGetter.INSTANCE.projectPipelines.get(0).get("sha") + "], error: ["+e.getMessage()+"].";
			
			LOGGER.error(message, e);
			try
			{
				TinyFileDialogs.tinyfd_messageBox(ModInfo.READABLE_NAME, message, "ok", "error", false);
			}
			catch (Exception ignore) { }
			
			return false;
		}
	}
	
	
	
	
	
	/**
	 * Should be called when the game is closed.
	 * This is ued to delete the previous file if it is required at the end.
	 */
	public static void onClose()
	{
		if (!deleteOldJarOnJvmShutdown)
		{
			return;
		}
		if (JarUtils.jarFile == null)
		{
			return;
		}
		
		
		
		Path newJarPath = newFileLocation.toPath();
		Path finalJarPath = JarUtils.jarFile.getParentFile().toPath().resolve(newFileLocation.getName());
		
		try
		{
			// if a jar with the same already exists in the final location, delete it first (otherwise file move issues will occur)
			Files.deleteIfExists(finalJarPath);
			
			// move the new jar...
			Files.move(newJarPath, finalJarPath);
			// ...and delete the temp folder
			Files.delete(newFileLocation.getParentFile().toPath());
		}
		catch (Exception e)
		{
			LOGGER.warn("Failed to move updated fire from [" + newFileLocation.getAbsolutePath() + "] " +
					"to [" + JarUtils.jarFile.getParentFile().getAbsolutePath() + "], " +
					"please move it manually", e);
		}
		
		
		try
		{
			// Get the Java binary
			String javaHome = System.getProperty("java.home");
			String javaBin = javaHome + File.separator + "bin" + File.separator + "java";
			
			// Run the file deletion jar in a new OS process, 
			// this is done to allow for deleting the current jar if the OS has a lock on it
			String execCommand = "\""+ javaBin +"\" -cp \""+
					finalJarPath.toAbsolutePath()+"\" " // run the deletion code from the new jar
					+DeleteOnUnlock.class.getCanonicalName()+" "+
					URLEncoder.encode(JarUtils.jarFile.getAbsolutePath(), "UTF-8"); // Encode the file location to prevent issues with special characters and spaces
			Process deleteProcess = Runtime.getRuntime().exec(execCommand);
			
			// check if the pro
			if (deleteProcess.isAlive())
			{
				LOGGER.info(DeleteOnUnlock.class.getSimpleName()+" process started...");
			}
			else
			{
				LOGGER.error(DeleteOnUnlock.class.getSimpleName()+" process failed to start.");
			}
			
			// wait a moment so we can catch if there are any immediate issues with the process
			Thread.sleep(250);
			
			if (deleteProcess.isAlive())
			{
				LOGGER.info(DeleteOnUnlock.class.getSimpleName()+" running, old jar file at ["+JarUtils.jarFile.getAbsolutePath()+"] should be deleted after Minecraft's JVM shutdown has completed.");
			}
			else
			{
				int processExitCode = deleteProcess.exitValue();
				if (processExitCode != DeleteOnUnlock.SUCCESS_EXIT_CODE)
				{
					String failReason = (processExitCode == DeleteOnUnlock.FAIL_EXIT_CODE) ? "Timed out and was unable to delete the file." : "Ran into an unexpected error."; 
					LOGGER.error(DeleteOnUnlock.class.getSimpleName() + " " + failReason);
					LOGGER.error(DeleteOnUnlock.class.getSimpleName() + " Logs are listed below:");
					
					// record the process' logs 
					String normalOutput = convertInputStreamToString(deleteProcess.getInputStream());
					LOGGER.info("process output: \n\n" + normalOutput);
					
					// record the process' error logs
					String errorOutput = convertInputStreamToString(deleteProcess.getInputStream());
					LOGGER.error("process error output: \n\n" + errorOutput);
				}
				else
				{
					LOGGER.info(DeleteOnUnlock.class.getSimpleName() + " completed before JVM shutdown.");
				}
			}
		}
		catch (Exception e)
		{
			LOGGER.warn("Failed to delete old jar using bootstrap method, doing backup 'Files.deleteOnExit()' method", e);
			JarUtils.jarFile.deleteOnExit();
			LOGGER.warn("If the old Distant Horizons file didn't delete, delete it manually at [" + JarUtils.jarFile + "]");
		}
	}
	
	private static String convertInputStreamToString(InputStream inputStream)
	{
		try
		{
			byte[] bytes = new byte[inputStream.available()];
			DataInputStream dataInputStream = new DataInputStream(inputStream);
			dataInputStream.readFully(bytes);
			return new String(bytes);
		}
		catch (IOException e)
		{
			return e.getMessage();
		}
	}
	
	
}
