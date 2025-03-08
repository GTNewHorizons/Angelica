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

package com.seibel.distanthorizons.core.render.glObject;

import com.seibel.distanthorizons.api.enums.config.EDhApiGLErrorHandlingMode;
import com.seibel.distanthorizons.api.enums.config.EDhApiGpuUploadMethod;
import com.seibel.distanthorizons.core.config.Config;
import com.seibel.distanthorizons.core.dependencyInjection.SingletonInjector;
import com.seibel.distanthorizons.core.logging.ConfigBasedLogger;
import com.seibel.distanthorizons.core.logging.DhLoggerBuilder;
import com.seibel.distanthorizons.core.util.objects.GLMessage;
import com.seibel.distanthorizons.core.util.objects.GLMessageOutputStream;
import com.seibel.distanthorizons.core.wrapperInterfaces.minecraft.IMinecraftClientWrapper;
import com.seibel.distanthorizons.coreapi.ModInfo;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.GL32;
import org.lwjgl.opengl.GLCapabilities;
import org.lwjgl.opengl.GLUtil;

import java.io.PrintStream;
import java.lang.invoke.MethodHandles;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * A singleton that holds references to different openGL contexts
 * and GPU capabilities.
 *
 * <p>
 * Helpful OpenGL resources:
 * <p>
 * https://www.seas.upenn.edu/~pcozzi/OpenGLInsights/OpenGLInsights-AsynchronousBufferTransfers.pdf <br>
 * https://learnopengl.com/Advanced-OpenGL/Advanced-Data <br>
 * https://www.slideshare.net/CassEveritt/approaching-zero-driver-overhead <br><br>
 *
 * https://gamedev.stackexchange.com/questions/91995/edit-vbo-data-or-create-a-new-one <br>
 * https://stackoverflow.com/questions/63509735/massive-performance-loss-with-glmapbuffer <br><br>
 */
public class GLProxy
{
	private static final IMinecraftClientWrapper MC = SingletonInjector.INSTANCE.get(IMinecraftClientWrapper.class);
	
	private static final Logger LOGGER = DhLoggerBuilder.getLogger(MethodHandles.lookup().lookupClass().getSimpleName());
	public static final ConfigBasedLogger GL_LOGGER = new ConfigBasedLogger(LogManager.getLogger(GLProxy.class),
			() -> Config.Common.Logging.logRendererGLEvent.get());
	
	private static GLProxy instance = null;
	
	
	private final ConcurrentLinkedQueue<Runnable> renderThreadRunnableQueue = new ConcurrentLinkedQueue<>();
	
	/** Minecraft's GL capabilities */
	public final GLCapabilities glCapabilities;
	
	public boolean namedObjectSupported = false; // ~OpenGL 4.5 (UNUSED CURRENTLY)
	public boolean bufferStorageSupported = false; // ~OpenGL 4.4
	public boolean vertexAttributeBufferBindingSupported = false; // ~OpenGL 4.3
	public boolean instancedArraysSupported = false;
	public boolean vertexAttribDivisorSupported = false; // OpenGL 3.3 or newer
	
	private final EDhApiGpuUploadMethod preferredUploadMethod;
	
	public final GLMessage.Builder vanillaDebugMessageBuilder = GLMessage.Builder.DEFAULT_MESSAGE_BUILDER;
	
	
	
	//=============//
	// constructor //
	//=============//
	
	private GLProxy() throws IllegalStateException
	{
		// this must be created on minecraft's render context to work correctly
		if (GLFW.glfwGetCurrentContext() == 0L)
		{
			throw new IllegalStateException(GLProxy.class.getSimpleName() + " was created outside the render thread!");
		}
		
		GL_LOGGER.info("Creating " + GLProxy.class.getSimpleName() + "... If this is the last message you see there must have been an OpenGL error.");
		GL_LOGGER.info("Lod Render OpenGL version [" + GL32.glGetString(GL32.GL_VERSION) + "].");
		
		
		
		
		//============================//
		// get Minecraft's GL context //
		//============================//
		
		// get Minecraft's capabilities
		this.glCapabilities = GL.getCapabilities();
		
		// crash the game if the GPU doesn't support OpenGL 3.2
		if (!this.glCapabilities.OpenGL32)
		{
			String supportedVersionInfo = this.getFailedVersionInfo(this.glCapabilities);
			
			// See full requirement at above.
			String errorMessage = ModInfo.READABLE_NAME + " was initializing " + GLProxy.class.getSimpleName()
					+ " and discovered this GPU doesn't meet the OpenGL requirements. Sorry I couldn't tell you sooner :(\n" +
					"Additional info:\n" + supportedVersionInfo;
			MC.crashMinecraft(errorMessage, new UnsupportedOperationException("Distant Horizon OpenGL requirements not met"));
		}
	 	GL_LOGGER.info("minecraftGlCapabilities:\n" + this.versionInfoToString(this.glCapabilities));
		
		if (Config.Client.Advanced.Debugging.OpenGl.overrideVanillaGLLogger.get())
		{
			GLUtil.setupDebugMessageCallback(new PrintStream(new GLMessageOutputStream(GLProxy::logMessage, this.vanillaDebugMessageBuilder), true));
		}
		
		
		
		//======================//
		// get GPU capabilities //
		//======================//
		
		// UNUSED currently
		// Check if we can use the named version of all calls, which is available in GL4.5 or after
		this.namedObjectSupported = this.glCapabilities.glNamedBufferData != 0L; //Nullptr
		
		// Check if we can use the Buffer Storage, which is available in GL4.4 or after
		this.bufferStorageSupported = this.glCapabilities.glBufferStorage != 0L; // Nullptr
		if (!this.bufferStorageSupported)
		{
			GL_LOGGER.info("This GPU doesn't support Buffer Storage (OpenGL 4.4), falling back to using other methods.");
		}
		
		// Check if we can use the make-over version of Vertex Attribute, which is available in GL4.3 or after
		this.vertexAttributeBufferBindingSupported = this.glCapabilities.glBindVertexBuffer != 0L; // Nullptr
		
		// used by instanced rendering
		this.vertexAttribDivisorSupported = this.glCapabilities.OpenGL33;
		// denotes if ARBInstancedArrays.glVertexAttribDivisorARB() is available or not
		// can be used as a backup if MC didn't create a GL 3.3+ context
		this.instancedArraysSupported = this.glCapabilities.GL_ARB_instanced_arrays;
		
		// get the best automatic upload method
		String vendor = GL32.glGetString(GL32.GL_VENDOR).toUpperCase(); // example return: "NVIDIA CORPORATION"
		if (vendor.contains("NVIDIA") || vendor.contains("GEFORCE"))
		{
			// NVIDIA card
			this.preferredUploadMethod = this.bufferStorageSupported ? EDhApiGpuUploadMethod.BUFFER_STORAGE : EDhApiGpuUploadMethod.SUB_DATA;
		}
		else
		{
			// AMD or Intel card
			this.preferredUploadMethod = this.bufferStorageSupported ? EDhApiGpuUploadMethod.BUFFER_STORAGE : EDhApiGpuUploadMethod.DATA;
		}
		GL_LOGGER.info("GPU Vendor [" + vendor + "], Preferred upload method is [" + this.preferredUploadMethod + "].");
		
		
		
		//==========//
		// clean up //
		//==========//
		
		// GLProxy creation success
		GL_LOGGER.info(GLProxy.class.getSimpleName() + " creation successful. OpenGL smiles upon you this day.");
	}
	
	
	
	//=========//
	// getters //
	//=========//
	
	public static boolean hasInstance() { return instance != null; }
	public static GLProxy getInstance()
	{
		if (instance == null)
		{
			instance = new GLProxy();
		}
		
		return instance;
	}
	
	public EDhApiGpuUploadMethod getGpuUploadMethod() { return this.preferredUploadMethod; }
	
	public boolean runningOnRenderThread()
	{
		long currentContext = GLFW.glfwGetCurrentContext();
		return currentContext != 0L; // if the context isn't null, it's the MC context
	}
	
	
	
	//=========================//
	// Worker Thread Runnables //
	//=========================//
	
	public void queueRunningOnRenderThread(Runnable renderCall)
	{
		StackTraceElement[] stackTrace = Thread.currentThread().getStackTrace();
		this.renderThreadRunnableQueue.add(() -> this.runOpenGlCall(renderCall, stackTrace));
	}
	private void runOpenGlCall(Runnable renderCall, StackTraceElement[] stackTrace)
	{
		try
		{
			// ...run the actual code...
			renderCall.run();
		}
		catch (Exception e)
		{
			RuntimeException error = new RuntimeException("Uncaught Exception during execution:", e);
			error.setStackTrace(stackTrace);
			GL_LOGGER.error(Thread.currentThread().getName() + " ran into a issue: ", error);
		}
	}
	
	/**
	 * Doesn't do any thread/GL Context validation.
	 * Running this outside of the render thread may cause crashes or other issues. 
	 */
	public void runRenderThreadTasks()
	{
		long startTime = System.nanoTime();
		
		Runnable runnable = this.renderThreadRunnableQueue.poll();
		while(runnable != null)
		{
			runnable.run();
			
			// only try running for 4ms (240 FPS) at a time to prevent random lag spikes
			long currentTime = System.nanoTime();
			long runDuration = currentTime - startTime;
			if (runDuration > 4_000_000)
			{
				break;
			}
			
			runnable = this.renderThreadRunnableQueue.poll();
		}
	}
	
	
	
	//=========//
	// logging //
	//=========//
	
	private static void logMessage(GLMessage msg)
	{
		EDhApiGLErrorHandlingMode errorHandlingMode = Config.Client.Advanced.Debugging.OpenGl.glErrorHandlingMode.get();
		if (errorHandlingMode == EDhApiGLErrorHandlingMode.IGNORE)
		{
			return;
		}
		
		
		
		if (msg.type == GLMessage.EType.ERROR || msg.type == GLMessage.EType.UNDEFINED_BEHAVIOR)
		{
			// critical error
			
			GL_LOGGER.error("GL ERROR " + msg.id + " from " + msg.source + ": " + msg.message);
			
			if (errorHandlingMode == EDhApiGLErrorHandlingMode.LOG_THROW)
			{
				throw new RuntimeException("GL ERROR: " + msg);
			}
			
		}
		else
		{
			// non-critical log
			
			GLMessage.ESeverity severity = msg.severity;
			RuntimeException ex = new RuntimeException("GL MESSAGE: " + msg);
			
			if (severity == null)
			{
				// just in case the message was malformed
				severity = GLMessage.ESeverity.LOW;
			}
			
			switch (severity)
			{
				case HIGH:
					GL_LOGGER.error("{}", ex);
					break;
				case MEDIUM:
					GL_LOGGER.warn("{}", ex);
					break;
				case LOW:
					GL_LOGGER.info("{}", ex);
					break;
				case NOTIFICATION:
					GL_LOGGER.debug("{}", ex);
					break;
			}
		}
	}
	
	
	
	//================//
	// helper methods //
	//================//
	
	private String getFailedVersionInfo(GLCapabilities c)
	{
		return "Your OpenGL support:\n" +
				"openGL version 3.2+: [" + c.OpenGL32 + "] <- REQUIRED\n" +
				"Vertex Attribute Buffer Binding: [" + (c.glVertexAttribBinding != 0) + "] <- optional improvement\n" +
				"Buffer Storage: [" + (c.glBufferStorage != 0) + "] <- optional improvement\n" +
				"If you noticed that your computer supports higher OpenGL versions"
				+ " but not the required version, try running the game in compatibility mode."
				+ " (How you turn that on, I have no clue~)";
	}
	
	private String versionInfoToString(GLCapabilities c)
	{
		return "Your OpenGL support:\n" +
				"openGL version 3.2+: [" + c.OpenGL32 + "] <- REQUIRED\n" +
				"Vertex Attribute Buffer Binding: [" + (c.glVertexAttribBinding != 0) + "] <- optional improvement\n" +
				"Buffer Storage: [" + (c.glBufferStorage != 0) + "] <- optional improvement\n";
	}
	
	
	
}
