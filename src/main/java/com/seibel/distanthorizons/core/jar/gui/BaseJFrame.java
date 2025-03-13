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

package com.seibel.distanthorizons.core.jar.gui;

import com.seibel.distanthorizons.core.jar.JarUtils;
import com.seibel.distanthorizons.core.dependencyInjection.SingletonInjector;
import com.seibel.distanthorizons.core.wrapperInterfaces.config.ILangWrapper;

import javax.imageio.ImageIO;
import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.List;

/**
 * @author coolGi
 */
// This will be removed later on to make a better ui
// To get colors use https://alvinalexander.com/java/java-uimanager-color-keys-list/
// TODO: Make the code less spaghetti later
public class BaseJFrame extends JFrame
{
	public BaseJFrame()
	{
		init();
	}
	public BaseJFrame(boolean show, boolean resizable)
	{
		init();
		setVisible(show);
		setResizable(resizable);
	}
	
	public void init()
	{
		setTitle(SingletonInjector.INSTANCE.get(ILangWrapper.class).getLang("lod.title"));
		try
		{
			setIconImage(ImageIO.read(JarUtils.accessFile("assets/distanthorizons/icon.png")));
		}
		catch (Exception e)
		{
			e.printStackTrace();
		}
		setSize(720, 480);
		setLocationRelativeTo(null); // Puts the window at the middle of the screen
		setLayout(new BorderLayout());
		setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
		initLookAndFeel();
	}
	
	/**
	 * Buttons for language and theme changing
	 *
	 * @param themeOnBottom Puts the theme buttons below the language
	 * @param rootPosOnLeft Where the start for the x is (on the left of the buttons or on the right)
	 */
	public void addExtraButtons(int x, int y, boolean themeOnBottom, boolean rootPosOnLeft)
	{
		// ========== LANGUAGE ==========
		int langBoxHeight = 25;
		int langBoxWidth = 100;
		
		// Creates a list with all the options in it
		List<String> langsToChoose = new ArrayList<>();
		try (
				final InputStreamReader isr = new InputStreamReader(JarUtils.accessFile("assets/distanthorizons/lang"), StandardCharsets.UTF_8);
				final BufferedReader br = new BufferedReader(isr)
		)
		{
			List<Object> col = Collections.unmodifiableList(new ArrayList<>(Arrays.asList(br.lines().toArray())));
			for (Object obj : col)
			{
				langsToChoose.add(((String) obj).replaceAll("\\.json", ""));
			}
		}
		catch (Exception e)
		{
			e.printStackTrace();
		}
		
		// Creates the box
		JComboBox<String> languageBox = new JComboBox(new DefaultComboBoxModel(langsToChoose.toArray()));
		languageBox.setSelectedIndex(langsToChoose.indexOf(Locale.getDefault().toString().toLowerCase()));
		languageBox.addActionListener(e -> {
			Locale.setDefault(Locale.forLanguageTag(languageBox.getSelectedItem().toString())); // Change lang on update
		});
		// Set where it goes
		languageBox.setBounds(rootPosOnLeft ? x : x - langBoxWidth, themeOnBottom ? y : y + langBoxHeight, langBoxWidth, langBoxHeight);
		// And finally add it
		add(languageBox);
		
		
		// ========== THEMING ========== //
		/**
		// TODO: Change the theme to a toggle switch rather than having 2 buttons
		int themeButtonSize = 25;
		JButton lightMode = null;
		JButton darkMode = null;
		// Try to set the icons for them
		try
		{
			lightMode = new JButton(new ImageIcon(
					new FlatSVGIcon(JarUtils.accessFile("assets/distanthorizons/textures/jar/themeLight.svg")).getImage() // Get the image
							.getScaledInstance(themeButtonSize, themeButtonSize, Image.SCALE_DEFAULT) // Scale it to the correct size
			));
			darkMode = new JButton(new ImageIcon(
					new FlatSVGIcon(JarUtils.accessFile("assets/distanthorizons/textures/jar/themeDark.svg")).getImage() // Get the image
							.getScaledInstance(themeButtonSize, themeButtonSize, Image.SCALE_DEFAULT) // Scale it to the correct size
			));
		}
		catch (Exception e)
		{
			e.printStackTrace();
		}
		// Where do the buttons go
		lightMode.setBounds(rootPosOnLeft ? x : x - (themeButtonSize * 2), themeOnBottom ? y + langBoxHeight : y, themeButtonSize, themeButtonSize);
		darkMode.setBounds(rootPosOnLeft ? x + themeButtonSize : x - themeButtonSize, themeOnBottom ? y + langBoxHeight : y, themeButtonSize, themeButtonSize);
		// Tell buttons what to do
		lightMode.addActionListener(e -> {
			FlatLightLaf.setup();
			FlatLightLaf.updateUI();
		});
		darkMode.addActionListener(e -> {
			FlatDarkLaf.setup();
			FlatDarkLaf.updateUI();
		});
		// Finally add the buttons
		add(lightMode);
		add(darkMode);
		 */
	}
	
	public BaseJFrame addLogo()
	{
		int logoHeight = 200;
		
		JPanel logo = new JPanel()
		{
			@Override
			protected void paintComponent(Graphics g)
			{
				super.paintComponent(g);
				try
				{
					BufferedImage image = ImageIO.read(JarUtils.accessFile("assets/distanthorizons/logo.png"));
					int logoWidth = (int) ((double) logoHeight * ((double) image.getWidth() / (double) image.getHeight())); // Calculate the aspect ratio and set the height correctly to not stretch it
					g.drawImage(image, (getWidth() / 2) - (logoWidth / 2), 0, logoWidth, logoHeight, this); // Resize image and draw it
				}
				catch (Exception e)
				{
					e.printStackTrace();
				}
			}
		};
		logo.setBounds(logo.getX(), logo.getY(), logo.getWidth(), logo.getHeight());
		
		add(logo);
		
		return this;
	}
	
	
	
	
	// This part of the code is taken from the official java docs at https://docs.oracle.com/javase/tutorial/uiswing/lookandfeel/plaf.html
	
	// Specify the look and feel to use by defining the LOOKANDFEEL constant
	// Valid values are: null (use the default), "Metal", "System", "Motif", and "GTK"
	final static String LOOKANDFEEL = "GTK";
	private static void initLookAndFeel()
	{
		String lookAndFeel = null;
		
		if (LOOKANDFEEL != null)
		{
			if (LOOKANDFEEL.equals("Metal"))
			{
				lookAndFeel = UIManager.getCrossPlatformLookAndFeelClassName();
				//  an alternative way to set the Metal L&F is to replace the
				// previous line with:
				// lookAndFeel = "javax.swing.plaf.metal.MetalLookAndFeel";
				
			}
			else if (LOOKANDFEEL.equals("System"))
			{
				lookAndFeel = UIManager.getSystemLookAndFeelClassName();
			}
			else if (LOOKANDFEEL.equals("Motif"))
			{
				lookAndFeel = "com.sun.java.swing.plaf.motif.MotifLookAndFeel";
			}
			else if (LOOKANDFEEL.equals("GTK"))
			{
				lookAndFeel = "com.sun.java.swing.plaf.gtk.GTKLookAndFeel";
			}
			else
			{
				System.err.println("Unexpected value of LOOKANDFEEL specified: "
						+ LOOKANDFEEL);
				lookAndFeel = UIManager.getCrossPlatformLookAndFeelClassName();
			}
			
			try
			{
				UIManager.setLookAndFeel(lookAndFeel);
			}
			catch (ClassNotFoundException e)
			{
				System.err.println("Couldn't find class for specified look and feel:"
						+ lookAndFeel);
				System.err.println("Did you include the L&F library in the class path?");
				System.err.println("Using the default look and feel.");
			}
			catch (UnsupportedLookAndFeelException e)
			{
				System.err.println("Can't use the specified look and feel ("
						+ lookAndFeel
						+ ") on this platform.");
				System.err.println("Using the default look and feel.");
			}
			catch (Exception e)
			{
				System.err.println("Couldn't get specified look and feel ("
						+ lookAndFeel
						+ "), for some reason.");
				System.err.println("Using the default look and feel.");
				e.printStackTrace();
			}
		}
	}
	
}

