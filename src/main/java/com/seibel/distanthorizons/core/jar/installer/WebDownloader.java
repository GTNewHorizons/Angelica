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
import com.seibel.distanthorizons.core.logging.DhLoggerBuilder;
import org.apache.logging.log4j.Logger;

import javax.net.ssl.HttpsURLConnection;
import java.io.*;
import java.net.URL;
import java.net.URLConnection;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.util.ArrayList;

/**
 * Does something similar to wget/curl. <br>
 * It allows you to download a file from a link, and other useful web utils
 *
 * @author coolGi
 */
public class WebDownloader
{
	private static final Logger LOGGER = DhLoggerBuilder.getLogger();
	
	
	public static boolean netIsAvailable()
	{
		try
		{
			final URL url = new URL("https://example.com"); // example.com will always be online as long as a DNS server exists, so attempt to ping it to check for internet connectivity
			final URLConnection conn = url.openConnection();
			conn.connect();
			conn.getInputStream().close();
			return false;
		}
		catch (Exception e)
		{
			return true;
		}
	}
	
	public static void downloadAsFile(URL url, File file) throws Exception
	{
//        URL url = new URL(urlS);
		
		HttpsURLConnection connection = (HttpsURLConnection) url
				.openConnection();
		long filesize = connection.getContentLengthLong();
		if (filesize == -1)
		{
			throw new Exception("Content length must not be -1 (unknown)!");
		}
		long totalDataRead = 0;
		try (java.io.BufferedInputStream in = new java.io.BufferedInputStream(
				connection.getInputStream()))
		{
			java.io.FileOutputStream fos = new java.io.FileOutputStream(file);
			try (java.io.BufferedOutputStream bout = new BufferedOutputStream(
					fos, 1024))
			{
				byte[] data = new byte[1024];
				int i, percent = -1;
				while ((i = in.read(data, 0, 1024)) >= 0)
				{
					totalDataRead = totalDataRead + i;
					bout.write(data, 0, i);
					
					// TODO: Link this to an atomic integer rather than printing it to log
                    int newPercent = (int) ((totalDataRead * 100) / filesize);
					if (percent != newPercent)
					{
						percent = newPercent;
						LOGGER.info(percent +"% downloaded");
					}
				}
			}
		}
	}
	
	public static String downloadAsString(URL url) throws Exception
	{
		StringBuilder stringBuilder = new StringBuilder();
//        URL url = new URL(urlS);
		
		URLConnection urlConnection = url.openConnection();
		urlConnection.setConnectTimeout(1000);
		urlConnection.setReadTimeout(1000);
		BufferedReader bReader = new BufferedReader(new InputStreamReader(urlConnection.getInputStream()));
		
		String line;
		while ((line = bReader.readLine()) != null)
		{
			stringBuilder.append(line);
		}
		
		return (stringBuilder.toString());
	}
	
	public static String formatMarkdownToHtml(String md, int width)
	{
		String str = String.format("<html><div style=\"width:%dpx;\">%s</div></html>", width, md);
		return new MarkdownFormatter.HTMLFormat().convertTo(str);
	}
	
	
	
	public static Config parseWebJson(String url) throws Exception
	{
		return parseWebJson(new URL(url));
	}
	public static Config parseWebJson(URL url) throws Exception
	{
		return JsonFormat.minimalInstance().createParser().parse(WebDownloader.downloadAsString(url));
	}
	
	public static ArrayList<Config> parseWebJsonList(String url) throws Exception
	{
		return parseWebJsonList(new URL(url));
	}
	public static ArrayList<Config> parseWebJsonList(URL url) throws Exception
	{
		// Is there a better way of doing this?
		return JsonFormat.minimalInstance().createParser().parse("{\"E\":" + WebDownloader.downloadAsString(url) + "}").get("E");
	}
	
	
	
	// Taken from https://mkyong.com/java/how-to-generate-a-file-checksum-value-in-java/ but added some comments
	/**
	 * @param filepath Path to the file
	 * @param md The checksum. Can be gotten by "MessageDigest.getInstance("SHA-256")" and can replace string with something like SHA, MD2, MD5, SHA-256, SHA-384...
	 * @return Returns the checksum using the previous md
	 */
	private static String checksum(String filepath, MessageDigest md) throws IOException
	{
		// file hashing with DigestInputStream
		try (DigestInputStream dis = new DigestInputStream(new FileInputStream(filepath), md))
		{
			while (dis.read() != -1) ; //empty loop to clear the data
			md = dis.getMessageDigest();
		}
		
		// bytes to hex
		StringBuilder result = new StringBuilder();
		for (byte b : md.digest())
		{
			result.append(String.format("%02x", b));
		}
		return result.toString();
		
	}
	
}
