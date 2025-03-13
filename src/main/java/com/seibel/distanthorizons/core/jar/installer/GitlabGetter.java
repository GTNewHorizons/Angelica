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
import com.seibel.distanthorizons.core.logging.DhLoggerBuilder;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.Nullable;

import java.net.URL;
import java.util.*;

/**
 * Gets info for nightly builds
 *
 * @author coolGi
 */
public class GitlabGetter
{
	private static final Logger LOGGER = DhLoggerBuilder.getLogger();
	
	/** DH's instance of the Gitlab getter */
	public static GitlabGetter INSTANCE = new GitlabGetter();
	
	public static final String GitlabApi = "https://gitlab.com/api/v4/projects/";
	/** Gitlab project ID (can by gotten by typing `document.getElementById('project_id').value` on your main project's console) */
	public final String projectID;
	/** Combines the {@link GitlabGetter#GitlabApi} and {@link GitlabGetter#projectID} into one var (Followed by a "/" at the end) */
	public final String GitProjID;
	public ArrayList<Config> projectPipelines = new ArrayList<>();
	
	/** Commit sha; Commit info */
	private static final Map<String, Config> commitInfo = new HashMap<>();
	/** Pipeline ID; Pipeline info */
	private static final Map<Integer, ArrayList<Config>> pipelineInfo = new HashMap<>();
	
	/** Uses our projectID to init this */
	public GitlabGetter()
	{
		this("18204078");
	}
	
	public GitlabGetter(String projectID)
	{
		this.projectID = projectID;
		this.GitProjID = GitlabApi + projectID + "/";
		
		try
		{
			this.projectPipelines = WebDownloader.parseWebJsonList(this.GitProjID + "pipelines");
		}
		catch (Exception e) { LOGGER.error("Unable to get project pipelines, error: ["+e.getMessage()+"].", e); }
	}
	
	public Config getCommitInfo(String commit)
	{
		if (!commitInfo.containsKey(commit))
		{
			try
			{
				commitInfo.put(commit, WebDownloader.parseWebJson(this.GitProjID + "repository/commits/" + commit));
			}
			catch (Exception e)
			{
				LOGGER.error("Unable to get commit info for project ["+this.GitProjID+"], commit: ["+commit+"], error: ["+e.getMessage()+"].", e);
				// Return empty
				return Config.inMemory();
			}
		}
		
		return commitInfo.get(commit);
	}
	
	public ArrayList<Config> getPipelineInfo(int pipeline)
	{
		if (!pipelineInfo.containsKey(pipeline))
		{
			try
			{
				pipelineInfo.put(pipeline, WebDownloader.parseWebJsonList(this.GitProjID + "pipelines/" + pipeline + "/jobs"));
			}
			catch (Exception e)
			{
				LOGGER.error("Unable to get ["+pipeline+"]'s pipeline info, error: ["+e.getMessage()+"].", e);
				
				// Return empty
				return new ArrayList<>();
			}
		}
		
		return pipelineInfo.get(pipeline);
	}
	
	/**
	 * Gets all the Minecraft download links to a pipeline ID
	 * 
	 * @return Minecraft version; Download URL
	 */
	public Map<String, URL> getDownloads(int pipelineID)
	{
		Map<String, URL> downloads = new HashMap<>();
		ArrayList<Config> currentPipelineInfo = this.getPipelineInfo(pipelineID);
		
		try
		{
			for (Config cfg : currentPipelineInfo)
			{
				if (!cfg.get("stage").equals("build"))
				{
					continue;
				}
				downloads.put(
						((String) cfg.get("name")).split("\\[|\\]")[1], // Regex to extract the Minecraft version from the text
						new URL(this.GitProjID + "jobs/" + cfg.get("id") + "/artifacts")
				);
			}
		}
		catch (Exception e) { LOGGER.error("Unable to get downloads for pipeline ["+pipelineID+"], error: ["+e.getMessage()+"].", e); }
		
		return downloads;
	}
	
	// Just a small test for this (Should output the nightly for each version that it supports)
	public static void main(String[] args) {
		GitlabGetter gitlabGetter = new GitlabGetter();
		
		System.out.println(gitlabGetter.getDownloads(gitlabGetter.projectPipelines.get(0).get("id")));
	}
	
	
	
	/** 
	 * A simple url getter for the latest jar of a version
	 * @apiNote Not dependent on the instance of this object, will just download the one for the base mod
	 */
	@Nullable
	public static URL getLatestForVersion(String mcVer)
	{
		try
		{
			return new URL("https://gitlab.com/distant-horizons-team/distant-horizons/-/jobs/artifacts/main/download?job=build:%20%5B" + mcVer + "%5D");
		}
		catch (Exception e)
		{
			LOGGER.error("Unable to get latest URL for MC version ["+mcVer+"], error: ["+e.getMessage()+"].", e);
			return null;
		} // This should always be safe (unless you stuff up **badly** somewhere)
	}
}
