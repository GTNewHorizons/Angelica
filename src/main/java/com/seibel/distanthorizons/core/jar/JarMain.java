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
package com.seibel.distanthorizons.core.jar;


import com.seibel.distanthorizons.core.pos.DhSectionPos;
import com.seibel.distanthorizons.core.sql.dto.FullDataSourceV2DTO;
import com.seibel.distanthorizons.core.sql.repo.FullDataSourceV2Repo;
import com.seibel.distanthorizons.coreapi.ModInfo;
import com.seibel.distanthorizons.core.jar.gui.BaseJFrame;
import com.seibel.distanthorizons.core.jar.gui.cusomJObject.JBox;
import com.seibel.distanthorizons.core.jar.installer.ModrinthGetter;
import com.seibel.distanthorizons.core.jar.installer.WebDownloader;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.core.LoggerContext;
import org.lwjgl.util.tinyfd.TinyFileDialogs;

import javax.swing.*;
import java.awt.*;
import java.io.*;
import java.net.URL;
import java.sql.SQLException;
import java.util.*;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

/**
 * The main class when you run the standalone jar
 *
 * @author coolGi
 */
// Once built it would be in core/build/libs/DistantHorizons-<Version>-dev-all.jar
public class JarMain
{
	public static final Logger logger = LogManager.getLogger(JarMain.class.getSimpleName());
	public static List<String> argList;
	public static final boolean isDarkTheme = DarkModeDetector.isDarkMode();
	public static boolean isOffline = WebDownloader.netIsAvailable();
	
	// TODO: Rewrite the standalone jar
	// Previous version here https://gitlab.com/jeseibel/distant-horizons-core/-/blob/333dc4d0e079777b712c0fff246837104ae9a2b6/core/src/main/java/com/seibel/lod/core/jar/JarMain.java
	
	
	public static void main(String[] args)
	{
		argList = Arrays.asList(args);
		
		if (!argList.contains("--no-custom-logger"))
		{
			LoggerContext context = (LoggerContext) LogManager.getContext(false);
			try
			{
				context.setConfigLocation(JarUtils.accessFileURI("/log4jConfig.xml"));
			}
			catch (Exception e)
			{
				logger.error("Failed to set log4j config. Try running with the \"--no-custom-logger\" argument");
				e.printStackTrace();
			}
		}
		
		logger.debug("Running " + ModInfo.READABLE_NAME + " standalone jar");
		logger.warn("The standalone jar is still a massive WIP, expect bugs");
		logger.debug("Java version " + System.getProperty("java.version"));
		//logger.debug(argList);
		
		
		if (args.length == 0 || Arrays.asList(args).contains("--gui"))
		{
			// Sets up the local
			if (JarUtils.accessFile("assets/lod/lang/" + Locale.getDefault().toString().toLowerCase() + ".json") == null)
			{
				logger.warn("The language setting [" + Locale.getDefault().toString().toLowerCase() + "] isn't allowed yet. Defaulting to [" + Locale.US.toString().toLowerCase() + "].");
				Locale.setDefault(Locale.US);
			}
			JarDependencySetup.createInitialBindings();
			
			startGUI();
		}
		else if (argList.get(0).equals("--export"))
		{
			Byte exportDetailLevel = null;
			Long exportPos = null;
			
			boolean showHelp = argList.contains("help");
			if (!showHelp)
			{
				// assume something is wrong unless we find a valid arg set
				showHelp = true;
				
				
				
				if (argList.size() == 1)
				{
					// only --export
					showHelp = false;
				}
				else if (argList.size() == 2)
				{
					// --export 0
					
					String detailLevelString = argList.get(1);
					try
					{
						exportDetailLevel = Byte.parseByte(detailLevelString);
						showHelp = false;
					}
					catch (NumberFormatException e)
					{
						logger.error("Unable to parse detail level ["+detailLevelString+"], error: ["+e.getMessage()+"].");
					}
				}
				else if (argList.size() == 4)
				{
					// --export 0 1 -2
					
					String detailLevelString = argList.get(1);
					String posXString = argList.get(2);
					String posZString = argList.get(3);
					try
					{
						byte detailLevel = Byte.parseByte(detailLevelString);
						int posX = Integer.parseInt(posXString);
						int posZ = Integer.parseInt(posZString);
						
						exportPos = DhSectionPos.encode(detailLevel, posX, posZ);
						showHelp = false;
					}
					catch (NumberFormatException e)
					{
						logger.error("Unable to parse position ["+detailLevelString+"], ["+posXString+"], ["+posZString+"], error: ["+e.getMessage()+"].");
					}
				}
			}
			
			if (showHelp)
			{
				logger.info("--export parses the 'DistantHorizons.sqlite' file next to this jar and exports the given data into a CSV file. \n" +
						"Usage: \n" +
						"--export [LOD position Detail Level] [LOD position X] [LOD position Z] \n" +
						"\tExport the given position's data if present. \n" +
						"\tThe detail level should be absolute, IE 0 = block sized, 1 = 2x2 blocks, etc. \n" +
						"--export [LOD position Detail Level]\n" +
						"\tExport all data for a given detail level.\n" +
						"\tThe detail level should be absolute, IE 0 = block sized, 1 = 2x2 blocks, etc. \n" +
						"--export\n" +
						"\tExport the entire database.\n");
				return;
			}
			
			
			// find the database file
			File dbFile = new File("./DistantHorizons.sqlite");
			if (!dbFile.exists())
			{
				logger.error("Unable to find a database to parse at: ["+dbFile.getAbsolutePath()+"].");
				return;
			}
			
			
			
			// set the export file
			File exportFile = new File("DistantHorizons-export.csv"); // TODO allow setting an export folder
			if (exportFile.isDirectory())
			{
				logger.error("Export file can't be a folder. Given path: ["+exportFile+"].");
				return;
			}
			
			
			// create the export file
			try
			{
				boolean ignored = exportFile.mkdirs(); // we don't care if we're making new directories of if they already exist
				
				if (exportFile.exists())
				{
					logger.error("Export file already exists: ["+exportFile.getAbsolutePath()+"].");
					return;
				}
				else if (exportFile.createNewFile())
				{
					logger.error("Failed to create file: ["+exportFile.getAbsolutePath()+"].");
					return;
				}
			}
			catch (Exception e)
			{
				logger.error("Unable to create export file: ["+exportFile.getAbsolutePath()+"].");
				return;
			}
			
			logger.info("LOD data will be exported to ["+exportFile.getAbsolutePath()+"].");
			
			
			FullDataSourceV2Repo repo;
			try
			{
				repo = new FullDataSourceV2Repo(FullDataSourceV2Repo.DEFAULT_DATABASE_TYPE, dbFile);
			}
			catch (SQLException e)
			{
				logger.error("Failed to initialize connection with database: ["+exportFile.getAbsolutePath()+"], error: ["+e.getMessage()+"].", e);
				return;
			}
			
			if (exportPos != null)
			{
				exportLodDataAtPosition(repo, exportFile, exportPos);
			}
			else if (exportDetailLevel != null)
			{
				exportAllAtDetailLevel(repo, exportFile, exportDetailLevel);
			}
			else
			{
				exportEntireDatabase(repo, exportFile);
			}
		}
	}
	
	private static void exportLodDataAtPosition(FullDataSourceV2Repo repo, File exportFile, long pos)
	{
		FullDataSourceV2DTO dto = repo.getByKey(pos);
		if (dto == null)
		{
			logger.error("Unable to find any data at the position ["+DhSectionPos.toString(pos)+"].");
			return;
		}
		// TODO need a way to create datasources (specifically data mappings) without a MC level object to deserialize with
		//dto.createPooledDataSource();
		
	}
	private static void exportAllAtDetailLevel(FullDataSourceV2Repo repo, File exportFile, byte detailLevel)
	{
		// TODO
	}
	private static void exportEntireDatabase(FullDataSourceV2Repo repo, File exportFile)
	{
		// TODO
	}
	
	
	
	
	public static void startGUI()
	{
		// Set up the theme
//        System.setProperty("apple.awt.application.appearance", "system");
//        if (isDarkTheme)
//            FlatDarkLaf.setup();
//        else
//            FlatLightLaf.setup();
		// This is done in BaseJFrame now


//        GitlabGetter.init();
		ModrinthGetter.init();
		System.out.println("WARNING: The standalone jar still work in progress");

//        JOptionPane.showMessageDialog(null, "The GUI for the standalone jar isn't made yet\nIf you want to use the mod then put it in your mods folder", "Distant Horizons", JOptionPane.WARNING_MESSAGE);

//        if (getOperatingSystem().equals(OperatingSystem.MACOS)) {
//            System.out.println("If you want the installer then please use Linux or Windows for the time being.\nMacOS support/testing will come later on");
//        }
		
		// Code will be changed later on to allow resizing and work better
		
		
		
		BaseJFrame frame = new BaseJFrame(false, true);
		frame.addExtraButtons(frame.getWidth(), 0, true, false);
		
		// Buttons which you want to be stacked vertically should be added with this (`frame.add(obj, this);`)
		GridBagConstraints verticalLayout = new GridBagConstraints();
		verticalLayout.gridy = GridBagConstraints.RELATIVE;
		verticalLayout.gridx = 0;
		verticalLayout.fill = GridBagConstraints.HORIZONTAL;
		verticalLayout.weightx = 1.0;
		verticalLayout.anchor = GridBagConstraints.NORTH;
		
		
		// Selected download
		AtomicReference<String> downloadID = new AtomicReference<String>("");
		
		
		// This is for the panel to show the update description
		JPanel modVersionDescriptionPanel = new JPanel(new GridBagLayout());
		JScrollPane modVersionDescriptionScroll = new JScrollPane(modVersionDescriptionPanel, JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED, JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
		// Sets all the layout stuff for it
		int modDescriptionWidth = 275;
		modVersionDescriptionScroll.setBounds(frame.getWidth() - modDescriptionWidth, 225, modDescriptionWidth, frame.getHeight() - 255);
		modVersionDescriptionScroll.setBorder(null); // Disables the border
		modVersionDescriptionScroll.setWheelScrollingEnabled(true);
		// The label
		JLabel modVersionDescriptionLabel = new JLabel();
		modVersionDescriptionPanel.add(modVersionDescriptionLabel, verticalLayout);
		// Finally add it
		frame.add(modVersionDescriptionScroll);
		
		
		
		// This is for the pannel to select MinecraftVersion
		JPanel modMinecraftVersionsPannel = new JPanel(new GridBagLayout());
		JScrollPane modMinecraftVersionsScroll = new JScrollPane(modMinecraftVersionsPannel, JScrollPane.VERTICAL_SCROLLBAR_ALWAYS, JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
		// Sets all the layout stuff for it
		modMinecraftVersionsScroll.setBounds(0, 225, 125, frame.getHeight() - 255);
		modMinecraftVersionsScroll.setBorder(null); // Disables the border
		modMinecraftVersionsScroll.setWheelScrollingEnabled(true);
		// List to store all the buttons
		ArrayList<JButton> modMinecraftReleaseButtons = new ArrayList<>();
		frame.add(modMinecraftVersionsScroll);
		
		
		// This is for selecting the mod version
		JPanel modVersionsPannel = new JPanel(new GridBagLayout());
		JScrollPane modVersionsScroll = new JScrollPane(modVersionsPannel, JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED, JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
		// Sets all the layout stuff for it
		modVersionsScroll.setBounds(125, 225, 100, frame.getHeight() - 255);
		modVersionsScroll.setBorder(null); // Disables the border
		modVersionsScroll.setWheelScrollingEnabled(true);
		// List to store all the buttons
		ArrayList<JButton> modReleaseButtons = new ArrayList<>();
		frame.add(modVersionsScroll);
		
		// Add all the buttons
		for (String mcVer : ModrinthGetter.mcVersions)
		{
			JButton btn = new JButton(mcVer);
			btn.setBackground(UIManager.getColor("Panel.background")); // Does the same thing as removing the background
			btn.setBorderPainted(false); // Removes the borders
//            btn.setHorizontalAlignment(SwingConstants.LEFT); // Sets the text to be on the left side rather than the center
			
			btn.addActionListener(e -> {
				// Clears the selected colors for the rest of the buttons
				for (JButton currentBtn : modMinecraftReleaseButtons)
				{
					currentBtn.setBackground(UIManager.getColor("Panel.background"));
				}
				btn.setBackground(UIManager.getColor("Button.background")); // Sets this to the selected color
				
				// Clears the minecraft version panel
				modVersionsPannel.removeAll();
				modReleaseButtons.clear();
				
				
				// Adds all the buttons for the minecraft panel
				for (String modID : ModrinthGetter.mcVerToReleaseID.get(mcVer))
				{
					// No need to comment most of these as it is the same this as before
					JButton btnDownload = new JButton(ModrinthGetter.releaseNames.get(modID));
					btnDownload.setBackground(UIManager.getColor("Panel.background"));
					btnDownload.setBorderPainted(false);
					btnDownload.setHorizontalAlignment(SwingConstants.LEFT);
					
					btnDownload.addActionListener(f -> {
						downloadID.set(modID);
						
						for (JButton currentBtn : modReleaseButtons)
						{
							currentBtn.setBackground(UIManager.getColor("Panel.background"));
						}
						btnDownload.setBackground(UIManager.getColor("Button.background"));
						
						
						modVersionDescriptionLabel.setText(
								WebDownloader.formatMarkdownToHtml(
										ModrinthGetter.changeLogs.get(modID), modDescriptionWidth - 75)
						);
						modVersionDescriptionPanel.repaint();
					});
					modVersionsPannel.add(btnDownload, verticalLayout);
					modReleaseButtons.add(btnDownload);
				}
				
				modVersionsScroll.getVerticalScrollBar().setValue(0); // Reset the scroll bar back to the top
				
				modVersionsPannel.repaint(); // Update the version pannel
				frame.validate(); // Update the frame
			});
			
			modMinecraftVersionsPannel.add(btn, verticalLayout);
			modMinecraftReleaseButtons.add(btn);
		}
		
		
		
		// Bar at the top
		frame.add(new JBox(UIManager.getColor("Separator.foreground"), 0, 220, frame.getWidth(), 5));
		// Minecraft version text
		JLabel textMcVersionHeader = new JLabel("Minecraft version");
		textMcVersionHeader.setBounds(0, 200, 125, 20);
		frame.add(textMcVersionHeader);
		// Version text
		JLabel textVersionHeader = new JLabel("Mod version");
		textVersionHeader.setBounds(125, 200, 150, 20);
		frame.add(textVersionHeader);
		
		
		
		
		// Stuff for setting the file install path
		JFileChooser minecraftDirPop = new JFileChooser();
		switch (EPlatform.get())
		{
			case WINDOWS:
				minecraftDirPop.setCurrentDirectory(new File(System.getenv("APPDATA") + "/.minecraft/mods"));
			case LINUX:
				minecraftDirPop.setCurrentDirectory(new File(System.getProperty("user.home") + "/.minecraft/mods"));
		}
		minecraftDirPop.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
		JButton minecraftDirBtn = new JButton("Click to select install path");
		minecraftDirBtn.addActionListener(e -> {
			if (minecraftDirPop.showOpenDialog(frame) == JFileChooser.APPROVE_OPTION)
				minecraftDirBtn.setText(minecraftDirPop.getSelectedFile().toString());
		});
		minecraftDirBtn.setBounds(230, frame.getHeight() - 100, 200, 20);
		frame.add(minecraftDirBtn);
		
		// Button for the install button
		JButton installMod = new JButton("Install " + ModInfo.READABLE_NAME);
		installMod.setBounds(230, frame.getHeight() - 70, 200, 20);
		installMod.addActionListener(e -> {
			if (minecraftDirPop.getSelectedFile() == null)
			{
				TinyFileDialogs.tinyfd_messageBox(ModInfo.READABLE_NAME, "Please select your install directory", "ok", "warning", false);
				return;
			}
			
			URL downloadPath = ModrinthGetter.downloadUrl.get(downloadID.get());
			
			try
			{
				WebDownloader.downloadAsFile(
						downloadPath,
						minecraftDirPop.getSelectedFile().toPath().resolve(
								ModInfo.NAME + "-" + ModrinthGetter.releaseNames.get(downloadID.get()) + ".jar"
						).toFile());
				
				TinyFileDialogs.tinyfd_messageBox(ModInfo.READABLE_NAME, "Installation done. \nYou can now close the installer", "ok", "info", false);
			}
			catch (Exception f)
			{
				TinyFileDialogs.tinyfd_messageBox(ModInfo.READABLE_NAME, "Download failed. Check your internet connection \nStacktrace: " + f.getMessage(), "error", "info", false);
			}
		});
		frame.add(installMod);
		
		
		// Fabric installer
//        try {
//            WebDownloader.downloadAsFile(new URL("https://maven.fabricmc.net/net/fabricmc/fabric-installer/0.11.0/fabric-installer-0.11.0.jar"), new File(System.getProperty("java.io.tmpdir") + "/fabricInstaller.jar"));
//            Runtime.getRuntime().exec("java -jar " + System.getProperty("java.io.tmpdir") + "/fabricInstaller.jar");
//        } catch (Exception e) {e.printStackTrace();}
		
		
		
		
		frame.addLogo(); // Has to be run at the end cus of a bug with java swing (it may not be a bug but idk how to fix it so I'll call it a bug)
		
		frame.validate(); // Update to add the widgets
		frame.setVisible(true); // Start the ui
	}
	
}
