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

package com.seibel.distanthorizons.core.config.gui;

import com.seibel.distanthorizons.core.jar.EPlatform;
import org.jetbrains.annotations.NotNull;
import org.lwjgl.system.jawt.JAWT;
import org.lwjgl.system.macosx.*;

import java.awt.*;
import java.lang.reflect.*;
import java.util.regex.*;

import static org.lwjgl.glfw.GLFWNativeCocoa.*;
import static org.lwjgl.glfw.GLFWNativeWin32.*;
import static org.lwjgl.glfw.GLFWNativeX11.*;
import static org.lwjgl.system.JNI.*;
import static org.lwjgl.system.jawt.JAWTFunctions.*;
import static org.lwjgl.system.macosx.ObjCRuntime.*;

// Some of the code is from https://github.com/LWJGL/lwjgl3/blob/master/modules/samples/src/test/java/org/lwjgl/demo/system/jawt/EmbeddedFrameUtil.java
// which is licensed under https://www.lwjgl.org/license

/**
 * Some utils for embedding awt and swing items into lwjgl windows
 *
 * @author Ran
 * @author coolGi
 */
public final class EmbeddedFrameUtil
{
	
	private static final int JAVA_VERSION;
	
	private static final JAWT awt;
	
	static
	{
		Pattern p = Pattern.compile("^(?:1[.])?([1-9][0-9]*)[.-]");
		Matcher m = p.matcher(System.getProperty("java.version"));
		
		if (!m.find())
		{
			throw new IllegalStateException("Failed to parse java.version");
		}
		
		JAVA_VERSION = Integer.parseInt(m.group(1));
		
		awt = JAWT.calloc();
		awt.version(JAVA_VERSION < 9 ? JAWT_VERSION_1_4 : JAWT_VERSION_9);
		if (!JAWT_GetAWT(awt))
		{
			throw new RuntimeException("GetAWT failed");
		}
	}
	
	private static String getEmbeddedFrameImpl()
	{
		switch (EPlatform.get())
		{
			case LINUX:
				return "sun.awt.X11.XEmbeddedFrame";
			case WINDOWS:
				return "sun.awt.windows.WEmbeddedFrame";
			case MACOS:
				return "sun.lwawt.macosx.CViewEmbeddedFrame";
			default:
				throw new IllegalStateException();
		}
	}
	
	private static long getEmbeddedFrameHandle(long window)
	{
		switch (EPlatform.get())
		{
			case LINUX:
				return glfwGetX11Window(window);
			case WINDOWS:
				return glfwGetWin32Window(window);
			case MACOS:
				long objc_msgSend = ObjCRuntime.getLibrary().getFunctionAddress("objc_msgSend");
				return invokePPP(glfwGetCocoaWindow(window), sel_getUid("contentView"), objc_msgSend);
			default:
				throw new IllegalStateException();
		}
	}
	
	public static Frame embeddedFrameCreate(long window)
	{
		if (JAVA_VERSION < 9)
		{
			try
			{
				@SuppressWarnings("unchecked")
				Class<? extends Frame> EmdeddedFrame = (Class<? extends Frame>) Class.forName(getEmbeddedFrameImpl());
				Constructor<? extends Frame> c = EmdeddedFrame.getConstructor(long.class);
				
				return c.newInstance(getEmbeddedFrameHandle(window));
			}
			catch (Exception e)
			{
				throw new RuntimeException(e);
			}
		}
		else
		{
			return nJAWT_CreateEmbeddedFrame(getEmbeddedFrameHandle(window), awt.CreateEmbeddedFrame());
		}
	}
	
	static void embeddedFrameSynthesizeWindowActivation(Frame embeddedFrame, boolean doActivate)
	{
		if (JAVA_VERSION < 9)
		{
			try
			{
				embeddedFrame
						.getClass()
						.getMethod("synthesizeWindowActivation", boolean.class)
						.invoke(embeddedFrame, doActivate);
			}
			catch (Exception e)
			{
				throw new RuntimeException(e);
			}
		}
		else
		{
			JAWT_SynthesizeWindowActivation(embeddedFrame, doActivate, awt.SynthesizeWindowActivation());
		}
	}
	
	public static void embeddedFrameSetBounds(Frame embeddedFrame, int x, int y, int width, int height)
	{
		if (JAVA_VERSION < 9)
		{
			try
			{
				Method setLocationPrivate = embeddedFrame
						.getClass()
						.getSuperclass()
						.getDeclaredMethod("setBoundsPrivate", int.class, int.class, int.class, int.class);
				setLocationPrivate.setAccessible(true);
				setLocationPrivate.invoke(embeddedFrame, x, y, width, height);
			}
			catch (Exception e)
			{
				throw new RuntimeException(e);
			}
		}
		else
		{
			JAWT_SetBounds(embeddedFrame, x, y, width, height, awt.SetBounds());
		}
	}
	
	
	public static void hideFrame(@NotNull Frame embeddedFrame)
	{
		embeddedFrame.setVisible(false);
		embeddedFrameSynthesizeWindowActivation(embeddedFrame, false);
	}
	
	public static void showFrame(@NotNull Frame embeddedFrame)
	{
		embeddedFrameSynthesizeWindowActivation(embeddedFrame, true);
		embeddedFrame.setVisible(true);
	}
	public static void placeAtCenter(Frame embeddedFrame, int windowWidth, int windowHeight, int frameWidth, int frameHeight, float scale)
	{
		float scaleFactor = (100.0F - scale) / 100.0F;
		float newWidth = frameWidth * scaleFactor;
		float newHeight = frameHeight * scaleFactor;
		float newX = (windowWidth - newWidth) / 2F;
		float newY = (windowHeight - newHeight) / 2F;
		embeddedFrameSetBounds(embeddedFrame, Math.round(newX), Math.round(newY), Math.round(newWidth), Math.round(newHeight));
	}
	
}