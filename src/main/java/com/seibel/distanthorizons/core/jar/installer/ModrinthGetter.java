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

package com.seibel.distanthorizons.core.jar.installer;

import com.electronwill.nightconfig.core.Config;
import com.electronwill.nightconfig.json.JsonFormat;
import com.seibel.distanthorizons.core.jar.updater.SelfUpdater;
import com.seibel.distanthorizons.core.logging.DhLoggerBuilder;
import org.apache.logging.log4j.Logger;

import java.net.URL;
import java.util.*;

/**
 * Gets the releases available on Modrinth and allows you to perform actions with them
 *
 * @author coolGi
 */
public class ModrinthGetter
{
	private static final Logger LOGGER = DhLoggerBuilder.getLogger();
	
	
	public static final String ModrinthAPI = "https://api.modrinth.com/v2/project/";
	public static final String projectID = "distanthorizons";
	/** Functions should only be accessed once this is true */
	public static boolean initted = false;
	public static ArrayList<Config> projectRelease;
	public static Map<String, Config> idToJson = new HashMap<>();
	
	public static List<String> releaseID = new ArrayList<>(); // This list contains the release ID's
	public static List<String> mcVersions = new ArrayList<>(); // List of available Minecraft versions in the mod
	/** Release ID; Readable name */
	public static Map<String, String> releaseNames = new HashMap<>(); // This list contains the readable names of the ID's to the
	/** Minecraft version; Compatible project ID's for that */
	public static Map<String, List<String>> mcVerToReleaseID = new HashMap<>();
	/** ID; Download URL */
	public static Map<String, URL> downloadUrl = new HashMap<>(); // Get the download url
	/** ID; Changelog */
	public static Map<String, String> changeLogs = new HashMap<>();
	
	
	public static boolean init()
	{
		try
		{
			initted = false;
			projectRelease = WebDownloader.parseWebJsonList(ModrinthAPI + projectID + "/version");
			
			
			for (Config currentRelease : projectRelease)
			{
				String workingID = currentRelease.get("id").toString();
				
				releaseID.add(workingID);
				idToJson.put(workingID, currentRelease);
				releaseNames.put(workingID, currentRelease.get("name").toString().replaceAll(" - 1\\..*", ""));
				changeLogs.put(workingID, currentRelease.get("changelog").toString());
				try
				{
					downloadUrl.put(workingID,
							new URL(
									((Config)
											((ArrayList) currentRelease.get("files"))
													.get(0))
											.get("url")
											.toString()
							));
				}
				catch (Exception e)
				{
					LOGGER.error("Unable get modrinth version list, error: ["+e.getMessage()+"]", e);
				}
				
				// Get all the mc versions this mod is available for
				for (String mcVer : (List<String>) currentRelease.get("game_versions"))
				{
					if (!mcVersions.contains(mcVer))
					{
						mcVersions.add(mcVer);
						mcVerToReleaseID.put(mcVer, new ArrayList<>());
					}
					mcVerToReleaseID.get(mcVer).add(workingID);
				}
			}
			// Sort them to look better
			Collections.sort(mcVersions);
			Collections.reverse(mcVersions);
			
			initted = true;
			return true;
		}
		catch (Exception e)
		{
			LOGGER.error("Unable to set up Modrinth access, error: ["+e.getMessage()+"]", e);
			return false;
		}
	}
	
	public static String getLatestIDForVersion(String mcVer)
	{
		try
		{
			return mcVerToReleaseID.get(mcVer).get(0);
		}
		catch (Exception e)
		{
			return null;
		}
	}
	public static String getLatestNameForVersion(String mcVer)
	{
		return releaseNames.get(mcVerToReleaseID.get(mcVer).get(0));
	}
	public static URL getLatestDownloadForVersion(String mcVer)
	{
		return downloadUrl.get(mcVerToReleaseID.get(mcVer).get(0));
	}
	public static String getLatestShaForVersion(String mcVer)
	{
		return (((ArrayList<Config>) idToJson.get(
				mcVerToReleaseID.get(mcVer).get(0)
		).get("files")).get(0).get("hashes.sha1")
				.toString());
	}
	
}
