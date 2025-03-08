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

package com.seibel.distanthorizons.core.render.renderer;

import com.seibel.distanthorizons.api.interfaces.override.rendering.IDhApiFramebuffer;
import com.seibel.distanthorizons.api.interfaces.override.rendering.IDhApiShaderProgram;
import com.seibel.distanthorizons.api.methods.events.abstractEvents.*;
import com.seibel.distanthorizons.api.methods.events.sharedParameterObjects.DhApiRenderParam;
import com.seibel.distanthorizons.core.config.Config;
import com.seibel.distanthorizons.core.dataObjects.render.bufferBuilding.ColumnRenderBuffer;
import com.seibel.distanthorizons.core.dependencyInjection.ModAccessorInjector;
import com.seibel.distanthorizons.core.dependencyInjection.SingletonInjector;
import com.seibel.distanthorizons.core.logging.ConfigBasedLogger;
import com.seibel.distanthorizons.core.logging.ConfigBasedSpamLogger;
import com.seibel.distanthorizons.core.pos.blockPos.DhBlockPos;
import com.seibel.distanthorizons.core.render.DhApiRenderProxy;
import com.seibel.distanthorizons.core.render.RenderBufferHandler;
import com.seibel.distanthorizons.core.render.glObject.GLProxy;
import com.seibel.distanthorizons.core.render.glObject.buffer.GLVertexBuffer;
import com.seibel.distanthorizons.core.render.glObject.buffer.QuadElementBuffer;
import com.seibel.distanthorizons.core.render.glObject.texture.*;
import com.seibel.distanthorizons.core.render.renderer.generic.GenericObjectRenderer;
import com.seibel.distanthorizons.core.render.renderer.shaders.*;
import com.seibel.distanthorizons.core.util.math.Mat4f;
import com.seibel.distanthorizons.core.wrapperInterfaces.minecraft.IMinecraftClientWrapper;
import com.seibel.distanthorizons.core.wrapperInterfaces.minecraft.IMinecraftGLWrapper;
import com.seibel.distanthorizons.core.wrapperInterfaces.minecraft.IMinecraftRenderWrapper;
import com.seibel.distanthorizons.core.wrapperInterfaces.minecraft.IProfilerWrapper;
import com.seibel.distanthorizons.core.wrapperInterfaces.misc.ILightMapWrapper;
import com.seibel.distanthorizons.api.enums.rendering.EDhApiFogColorMode;
import com.seibel.distanthorizons.core.wrapperInterfaces.modAccessor.AbstractOptifineAccessor;
import com.seibel.distanthorizons.core.wrapperInterfaces.modAccessor.IIrisAccessor;
import com.seibel.distanthorizons.core.wrapperInterfaces.world.IClientLevelWrapper;
import com.seibel.distanthorizons.coreapi.DependencyInjection.ApiEventInjector;
import com.seibel.distanthorizons.coreapi.DependencyInjection.OverrideInjector;
import com.seibel.distanthorizons.core.util.math.Vec3d;
import com.seibel.distanthorizons.core.util.math.Vec3f;
import org.apache.logging.log4j.LogManager;
import org.lwjgl.opengl.GL32;

import java.awt.*;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

/**
 * This is where all the magic happens. <br>
 * This is where LODs are draw to the world.
 */
public class LodRenderer
{
	public static final ConfigBasedLogger EVENT_LOGGER = new ConfigBasedLogger(LogManager.getLogger(LodRenderer.class),
			() -> Config.Common.Logging.logRendererBufferEvent.get());
	public static final ConfigBasedSpamLogger SPAM_LOGGER = new ConfigBasedSpamLogger(LogManager.getLogger(LodRenderer.class),
			() -> Config.Common.Logging.logRendererBufferEvent.get(), 1);
	
	private static final IIrisAccessor IRIS_ACCESSOR = ModAccessorInjector.INSTANCE.get(IIrisAccessor.class);
	
	private static final IMinecraftClientWrapper MC = SingletonInjector.INSTANCE.get(IMinecraftClientWrapper.class);
	private static final IMinecraftRenderWrapper MC_RENDER = SingletonInjector.INSTANCE.get(IMinecraftRenderWrapper.class);
	private static final IMinecraftGLWrapper GLMC = SingletonInjector.INSTANCE.get(IMinecraftGLWrapper.class);
	
	public static final boolean ENABLE_DRAW_LAG_SPIKE_LOGGING = false;
	public static final long DRAW_LAG_SPIKE_THRESHOLD_NS = TimeUnit.NANOSECONDS.convert(20, TimeUnit.MILLISECONDS);
	
	
	
	// TODO make these private, the LOD Builder can get these variables from the config itself
	public static boolean transparencyEnabled = true;
	public static boolean fakeOceanFloor = true;
	
	/** used to prevent cleaning up render resources while they are being used */
	private static final ReentrantLock renderLock = new ReentrantLock();
	
	// these ID's either what any render is currently using (since only one renderer can be active at a time), or just used previously
	private static int activeFramebufferId = -1;
	private static int activeColorTextureId = -1;
	private static int activeDepthTextureId = -1;
	private int cachedWidth;
	private int cachedHeight;
	
	private final ReentrantLock setupLock = new ReentrantLock();
	
	public final RenderBufferHandler bufferHandler;
	public final GenericObjectRenderer genericObjectRenderer;
	
	
	// The shader program
	private IDhApiShaderProgram lodRenderProgram = null;
	public QuadElementBuffer quadIBO = null;
	public boolean isSetupComplete = false;
	
	// frameBuffer and texture ID's for this renderer
	private IDhApiFramebuffer framebuffer;
	/** will be null if MC's framebuffer is being used since MC already has a color texture */
	private DhColorTexture nullableColorTexture;
	private DHDepthTexture depthTexture;
	/** 
	 * If true the {@link LodRenderer#framebuffer} is the same as MC's.
	 * This should only be true in the case of Optifine so LODs won't be overwritten when shaders are enabled.
	 */
	private boolean usingMcFrameBuffer = false;
	
	
	
	//=============//
	// constructor //
	//=============//
	
	public LodRenderer(RenderBufferHandler bufferHandler, GenericObjectRenderer genericObjectRenderer)
	{
		this.bufferHandler = bufferHandler;
		this.genericObjectRenderer = genericObjectRenderer;
	}
	
	private boolean rendererClosed = false;
	public void close()
	{
		if (this.rendererClosed)
		{
			EVENT_LOGGER.warn("close() called twice!");
			return;
		}
		
		
		this.rendererClosed = true;
		
		// wait for the renderer to finish before closing (to prevent closing resources that are currently in use)
		renderLock.lock();
		try
		{
			EVENT_LOGGER.info("Shutting down " + LodRenderer.class.getSimpleName() + "...");
			
			this.cleanup();
			this.bufferHandler.close();
			
			EVENT_LOGGER.info("Finished shutting down " + LodRenderer.class.getSimpleName());
		}
		finally
		{
			renderLock.unlock();
		}
	}
	
	
	
	//===========//
	// rendering //
	//===========//
	
	/**
	 * This will draw both opaque and transparent LODs if 
	 * {@link DhApiRenderProxy#getDeferTransparentRendering()} is false,
	 * otherwise it will only render opaque LODs.
	 */
	public void drawLods(
			IClientLevelWrapper clientLevelWrapper,
			DhApiRenderParam renderEventParam, IProfilerWrapper profiler)
	{ 
		this.renderLodPass(
			clientLevelWrapper,
			renderEventParam,
			profiler,
			false); 
	}
	
	/**
	 * This method is designed for Iris to be able 
	 * to draw water in a deferred rendering context. 
	 * It needs to be updated with any major changes, 
	 * but shouldn't be activated as per deferWaterRendering.
	 */
	public void drawDeferredLods(
			IClientLevelWrapper clientLevelWrapper,
			DhApiRenderParam renderEventParam, IProfilerWrapper profiler)
	{
		this.renderLodPass(
				clientLevelWrapper,
				renderEventParam,
				profiler,
				true);
	}
	
	private void renderLodPass(
			IClientLevelWrapper clientLevelWrapper,
			DhApiRenderParam renderEventParam,
			IProfilerWrapper profiler,
			boolean runningDeferredPass)
	{
		//====================//
		// validate rendering //
		//====================//
		
		boolean deferTransparentRendering = DhApiRenderProxy.INSTANCE.getDeferTransparentRendering();
		if (runningDeferredPass && !deferTransparentRendering)
		{
			return;
		}
		boolean renderingFirstPass = !runningDeferredPass;
		
		if (this.rendererClosed)
		{
			EVENT_LOGGER.error("LOD rendering attempted after the renderer has been shut down!");
			return;
		}
		
		if (AbstractOptifineAccessor.optifinePresent() && MC_RENDER.getTargetFrameBuffer() == -1)
		{
			// wait for MC to finish setting up their renderer
			return;
		}
		
		if (!renderLock.tryLock())
		{
			// never lock the render thread, if the lock isn't available don't wait for it
			return;
		}
		
		
		
		//=================//
		// rendering setup //
		//=================//
		
		try
		{
			ILightMapWrapper lightmap = MC_RENDER.getLightmapWrapper(clientLevelWrapper);
			if (lightmap == null)
			{
				// this shouldn't normally happen, but just in case
				return;
			}
			
			
			ApiEventInjector.INSTANCE.fireAllEvents(DhApiBeforeRenderSetupEvent.class, renderEventParam);
			this.setupGLStateAndRenderObjects(profiler, renderEventParam, renderingFirstPass);
			
			lightmap.bind();
			this.quadIBO.bind();
			
			if (renderingFirstPass)
			{
				this.bufferHandler.buildRenderListAndUpdateSections(clientLevelWrapper, renderEventParam, MC_RENDER.getLookAtVector());
				
				transparencyEnabled = Config.Client.Advanced.Graphics.Quality.transparency.get().transparencyEnabled;
				fakeOceanFloor = Config.Client.Advanced.Graphics.Quality.transparency.get().fakeTransparencyEnabled;
			}
			
			
			
			
			//===========//
			// rendering //
			//===========//
			
			if (!runningDeferredPass)
			{
				//=================================//
				// opaque (non-deferred) rendering //
				//=================================//
				
				
				// Disable blending for opaque rendering
				GLMC.disableBlend();
				
				
				// terrain
				profiler.popPush("LOD Opaque");
				ApiEventInjector.INSTANCE.fireAllEvents(DhApiBeforeRenderPassEvent.class, renderEventParam);
				
				
				this.bufferHandler.renderOpaque(this, renderEventParam);
				
				
				
				// custom objects with SSAO
				if (Config.Client.Advanced.Graphics.GenericRendering.enableGenericRendering.get())
				{
					profiler.popPush("Custom Objects");
					this.genericObjectRenderer.render(renderEventParam, profiler, true);
				}
				
				
				// SSAO
				if (Config.Client.Advanced.Graphics.Ssao.enableSsao.get())
				{
					profiler.popPush("LOD SSAO");
					SSAORenderer.INSTANCE.render(new Mat4f(renderEventParam.dhProjectionMatrix), renderEventParam.partialTicks);
				}
				
				
				// custom objects without SSAO
				if (Config.Client.Advanced.Graphics.GenericRendering.enableGenericRendering.get())
				{
					profiler.popPush("Custom Objects");
					this.genericObjectRenderer.render(renderEventParam, profiler, false);
				}
				
				
				if (!deferTransparentRendering && Config.Client.Advanced.Graphics.Quality.transparency.get().transparencyEnabled)
				{
					this.renderTransparentBuffersAndFireApiEvent(profiler, renderEventParam);
				}
				
				
				if (Config.Client.Advanced.Graphics.Fog.enableDhFog.get())
				{
					profiler.popPush("LOD Fog");
					
					Mat4f combinedMatrix = new Mat4f(renderEventParam.dhProjectionMatrix);
					combinedMatrix.multiply(renderEventParam.dhModelViewMatrix);
					
					FogRenderer.INSTANCE.render(combinedMatrix, renderEventParam.partialTicks);
				}
				
				
				
				
				//=================//
				// debug rendering //
				//=================//
				
				if (Config.Client.Advanced.Debugging.DebugWireframe.enableRendering.get())
				{
					profiler.popPush("Debug wireframes");
					
					Mat4f combinedMatrix = new Mat4f(renderEventParam.dhProjectionMatrix);
					combinedMatrix.multiply(renderEventParam.dhModelViewMatrix);
					
					// Note: this can be very slow if a lot of boxes are being rendered 
					DebugRenderer.INSTANCE.render(combinedMatrix);
				}
				
				profiler.popPush("LOD cleanup");
				
				
				
				if (this.usingMcFrameBuffer)
				{
					// If MC's framebuffer is being used the depth needs to be cleared to prevent rendering on top of MC.
					// This should only happen when Optifine shaders are being used.
					GL32.glClear(GL32.GL_DEPTH_BUFFER_BIT);
				}
				
				
				
				//=============================//
				// Apply to the MC FrameBuffer //
				//=============================//
				
				boolean cancelApplyShader = ApiEventInjector.INSTANCE.fireAllEvents(DhApiBeforeApplyShaderRenderEvent.class, renderEventParam);
				if (!cancelApplyShader)
				{
					profiler.popPush("LOD Apply");
					
					// Copy the LOD framebuffer to Minecraft's framebuffer
					DhApplyShader.INSTANCE.render(renderEventParam.partialTicks);
				}
			}
			else
			{
				//====================//
				// deferred rendering //
				//====================//
				
				if (Config.Client.Advanced.Graphics.Quality.transparency.get().transparencyEnabled)
				{
					profiler.popPush("LOD Transparent");
					
					this.renderTransparentBuffersAndFireApiEvent(profiler, renderEventParam);
					
					
					if (Config.Client.Advanced.Graphics.Fog.enableDhFog.get())
					{
						profiler.popPush("LOD Fog");
						
						Mat4f combinedMatrix = new Mat4f(renderEventParam.dhProjectionMatrix);
						combinedMatrix.multiply(renderEventParam.dhModelViewMatrix);
						
						FogRenderer.INSTANCE.render(combinedMatrix, renderEventParam.partialTicks);
					}
				}
			}
			
			
			
			//================//
			// render cleanup //
			//================//
			
			profiler.popPush("LOD cleanup");
			ApiEventInjector.INSTANCE.fireAllEvents(DhApiBeforeRenderCleanupEvent.class, renderEventParam);
			
			lightmap.unbind();
			this.quadIBO.unbind();
			
			IDhApiShaderProgram shaderProgram = this.lodRenderProgram;
			IDhApiShaderProgram shaderProgramOverride = OverrideInjector.INSTANCE.get(IDhApiShaderProgram.class);
			if (shaderProgramOverride != null && shaderProgram.overrideThisFrame())
			{
				shaderProgram = shaderProgramOverride;
			}
			shaderProgram.unbind();
			
			
			// end of internal LOD profiling
			profiler.pop();
			SPAM_LOGGER.incLogTries();
			
		}
		finally
		{
			renderLock.unlock();
		}
	}
	
	private void renderTransparentBuffersAndFireApiEvent(IProfilerWrapper profiler, DhApiRenderParam renderEventParam) 
	{
		profiler.popPush("LOD Transparent");
		
		GLMC.enableBlend();
		GLMC.enableDepthTest();
		GL32.glBlendEquation(GL32.GL_FUNC_ADD);
		GLMC.glBlendFuncSeparate(GL32.GL_SRC_ALPHA, GL32.GL_ONE_MINUS_SRC_ALPHA, GL32.GL_ONE, GL32.GL_ONE_MINUS_SRC_ALPHA);
		
		ApiEventInjector.INSTANCE.fireAllEvents(DhApiBeforeRenderPassEvent.class, renderEventParam);
		
		this.bufferHandler.renderTransparent(this, renderEventParam);
	}
	
	/** called by each {@link ColumnRenderBuffer} before rendering */
	public void setModelViewMatrixOffset(DhBlockPos pos, DhApiRenderParam renderEventParam) throws IllegalStateException
	{
		Vec3d cam = MC_RENDER.getCameraExactPosition();
		Vec3f modelPos = new Vec3f((float) (pos.getX() - cam.x), (float) (pos.getY() - cam.y), (float) (pos.getZ() - cam.z));
		
		
		IDhApiShaderProgram shaderProgram = this.lodRenderProgram;
		IDhApiShaderProgram shaderProgramOverride = OverrideInjector.INSTANCE.get(IDhApiShaderProgram.class);
		if (shaderProgramOverride != null && shaderProgram.overrideThisFrame())
		{
			shaderProgram = shaderProgramOverride;
		}
		
		shaderProgram.bind();
		shaderProgram.setModelOffsetPos(modelPos);
		
		ApiEventInjector.INSTANCE.fireAllEvents(DhApiBeforeBufferRenderEvent.class, new DhApiBeforeBufferRenderEvent.EventParam(renderEventParam, modelPos));
	}
	
	public void drawVbo(GLVertexBuffer vbo, ColumnRenderBuffer parentBufferContainer)
	{
		// this should only be enabled for debugging
		if (Config.Client.Advanced.Debugging.OpenGl.validateBufferIdsBeforeRendering.get())
		{
			// this is a fairly slow call and enabling it will reduce FPS significantly
			if (!GL32.glIsBuffer(vbo.getId()))
			{
				if (SPAM_LOGGER.canMaybeLog())
				{
					SPAM_LOGGER.warn("Attempted to draw invalid buffer: [" + vbo.getId() + "], expected size: ["+vbo.getSize()+"], upload complete: [" + parentBufferContainer.buffersUploaded + "], upload in progress: [" + parentBufferContainer.uploadInProgress() + "], buffer blockPos: ["+parentBufferContainer.blockPos+"].");
				}
				return;
			}
		}
		
		
		IDhApiShaderProgram shaderProgram = this.lodRenderProgram;
		IDhApiShaderProgram shaderProgramOverride = OverrideInjector.INSTANCE.get(IDhApiShaderProgram.class);
		if (shaderProgramOverride != null && shaderProgram.overrideThisFrame())
		{
			shaderProgram = shaderProgramOverride;
		}
		
		
		vbo.bind();
		shaderProgram.bindVertexBuffer(vbo.getId());
		GL32.glDrawElements(GL32.GL_TRIANGLES, (vbo.getVertexCount() / 4) * 6, // TODO what does the 4 and 6 here represent?
				this.quadIBO.getType(), 0);
		vbo.unbind();
	}
	
	
	
	
	//=================//
	// Setup Functions //
	//=================//
	
	private void setupGLStateAndRenderObjects(
			IProfilerWrapper profiler,
			DhApiRenderParam renderEventParam,
			boolean firstPass)
	{
		//===================//
		// draw params setup //
		//===================//
		
		profiler.push("LOD draw setup");
		
		if (!this.isSetupComplete)
		{
			this.setupRenderObjects();
			
			// shouldn't normally happen, but just in case
			if (!this.isSetupComplete)
			{
				return;
			}
		}
		
		if (MC_RENDER.getTargetFrameBufferViewportWidth() != this.cachedWidth || MC_RENDER.getTargetFrameBufferViewportHeight() != this.cachedHeight)
		{
			// just resizing the textures doesn't work when Optifine is present,
			// so recreate the textures with the new size instead
			this.createColorAndDepthTextures();
		}
		
		
		IDhApiFramebuffer activeFrameBuffer = this.framebuffer;
		IDhApiFramebuffer framebufferOverride = OverrideInjector.INSTANCE.get(IDhApiFramebuffer.class);
		if (framebufferOverride != null && framebufferOverride.overrideThisFrame())
		{
			activeFrameBuffer = framebufferOverride;
		}
		
		this.setActiveFramebufferId(activeFrameBuffer.getId());
		this.setActiveDepthTextureId(this.depthTexture.getTextureId());
		if (this.nullableColorTexture != null)
		{
			this.setActiveColorTextureId(this.nullableColorTexture.getTextureId());
		}
		else
		{
			// get MC's color texture
			int mcColorTextureId = GL32.glGetFramebufferAttachmentParameteri(GL32.GL_FRAMEBUFFER, GL32.GL_COLOR_ATTACHMENT0, GL32.GL_FRAMEBUFFER_ATTACHMENT_OBJECT_NAME);
			this.setActiveColorTextureId(mcColorTextureId);
		}
		// Bind LOD frame buffer
		activeFrameBuffer.bind();
		
		
		boolean clearTextures = !ApiEventInjector.INSTANCE.fireAllEvents(DhApiBeforeTextureClearEvent.class, renderEventParam);
		if (clearTextures)
		{
			if (this.usingMcFrameBuffer && framebufferOverride == null)
			{
				// Due to using MC/Optifine's framebuffer we need to re-bind the depth texture,
				// otherwise we'll be writing to MC/Optifine's depth texture which causes rendering issues
				activeFrameBuffer.addDepthAttachment(this.depthTexture.getTextureId(), EDhDepthBufferFormat.DEPTH32F.isCombinedStencil());
				
				
				// don't clear the color texture, that removes the sky 
				GL32.glClear(GL32.GL_DEPTH_BUFFER_BIT);
			}
			else if (firstPass)
			{
				GL32.glClear(GL32.GL_COLOR_BUFFER_BIT | GL32.GL_DEPTH_BUFFER_BIT);
			}
		}
		
		// by default draw everything as triangles
		GL32.glPolygonMode(GL32.GL_FRONT_AND_BACK, GL32.GL_FILL);
		GLMC.enableFaceCulling();
		
		// Enable depth test and depth mask
		GLMC.enableDepthTest();
		GLMC.glDepthFunc(GL32.GL_LESS);
		GLMC.enableDepthMask();
		
		/*---------Bind required objects--------*/
		// Setup LodRenderProgram and the LightmapTexture if it has not yet been done
		// also binds LightmapTexture, VAO, and ShaderProgram
		if (!this.isSetupComplete)
		{
			this.setupRenderObjects();
		}
		else
		{
			this.lodRenderProgram.bind();
		}
		
		
		IDhApiShaderProgram shaderProgramOverride = OverrideInjector.INSTANCE.get(IDhApiShaderProgram.class);
		if (shaderProgramOverride != null)
		{
			shaderProgramOverride.fillUniformData(renderEventParam);
		}
		
		this.lodRenderProgram.fillUniformData(renderEventParam);
	}
	
	/** Setup all render objects - MUST be called on the render thread */
	private void setupRenderObjects()
	{
		if (this.isSetupComplete)
		{
			EVENT_LOGGER.warn("Renderer setup called but it has already completed setup!");
			return;
		}
		if (GLProxy.getInstance() == null)
		{
			// shouldn't normally happen, but just in case
			EVENT_LOGGER.warn("Renderer setup called but GLProxy has not yet been setup!");
			return;
		}
		
		try
		{
			this.setupLock.lock();
			
			
			EVENT_LOGGER.info("Setting up renderer");
			this.isSetupComplete = true;
			this.lodRenderProgram = new DhTerrainShaderProgram();
			
			this.quadIBO = new QuadElementBuffer();
			this.quadIBO.reserve(ColumnRenderBuffer.MAX_QUADS_PER_BUFFER);
			
			
			// create or get the frame buffer
			if (AbstractOptifineAccessor.optifinePresent())
			{
				// use MC/Optifine's default FrameBuffer so shaders won't remove the LODs
				int currentFrameBufferId = MC_RENDER.getTargetFrameBuffer();
				this.framebuffer = new DhFramebuffer(currentFrameBufferId);
				this.usingMcFrameBuffer = true;
			}
			else 
			{
				// normal use case
				this.framebuffer = new DhFramebuffer();
				this.usingMcFrameBuffer = false;
			}
			
			// create and bind the necessary textures
			this.createColorAndDepthTextures();
			
			if(this.framebuffer.getStatus() != GL32.GL_FRAMEBUFFER_COMPLETE)
			{
				// This generally means something wasn't bound, IE missing either the color or depth texture
				SPAM_LOGGER.warn("FrameBuffer ["+this.framebuffer.getId()+"] isn't complete.");
			}
			
			
			EVENT_LOGGER.info("Renderer setup complete");
		}
		finally
		{
			this.setupLock.unlock();
		}
	}
	/** also binds the new textures to the {@link LodRenderer#framebuffer} */
	private void createColorAndDepthTextures()
	{
		int oldWidth = this.cachedWidth;
		int oldHeight = this.cachedHeight;
		this.cachedWidth = MC_RENDER.getTargetFrameBufferViewportWidth();
		this.cachedHeight = MC_RENDER.getTargetFrameBufferViewportHeight();
		
		
		ApiEventInjector.INSTANCE.fireAllEvents(DhApiColorDepthTextureCreatedEvent.class, 
				new DhApiColorDepthTextureCreatedEvent.EventParam(
						oldWidth, oldHeight,
						this.cachedWidth, this.cachedHeight
				));
				
		
		// also update the override if present
		IDhApiFramebuffer framebufferOverride = OverrideInjector.INSTANCE.get(IDhApiFramebuffer.class);
		
		this.depthTexture = new DHDepthTexture(this.cachedWidth, this.cachedHeight, EDhDepthBufferFormat.DEPTH32F);
		this.framebuffer.addDepthAttachment(this.depthTexture.getTextureId(), EDhDepthBufferFormat.DEPTH32F.isCombinedStencil());
		if (framebufferOverride != null)
		{
			framebufferOverride.addDepthAttachment(this.depthTexture.getTextureId(), EDhDepthBufferFormat.DEPTH32F.isCombinedStencil());
		}
		
		// if we are using MC's frame buffer, a color texture is already present and shouldn't need to be bound
		if (!this.usingMcFrameBuffer)
		{
			this.nullableColorTexture = DhColorTexture.builder().setDimensions(this.cachedWidth, this.cachedHeight)
					.setInternalFormat(EDhInternalTextureFormat.RGBA8)
					.setPixelType(EDhPixelType.UNSIGNED_BYTE)
					.setPixelFormat(EDhPixelFormat.RGBA)
					.build();
			
			this.framebuffer.addColorAttachment(0, this.nullableColorTexture.getTextureId());
			if (framebufferOverride != null)
			{
				framebufferOverride.addColorAttachment(0, this.nullableColorTexture.getTextureId());
			}
		}
		else
		{
			this.nullableColorTexture = null;
		}
	}
	
	
	
	private Color getFogColor(float partialTicks)
	{
		Color fogColor;
		
		if (Config.Client.Advanced.Graphics.Fog.colorMode.get() == EDhApiFogColorMode.USE_SKY_COLOR)
		{
			fogColor = MC_RENDER.getSkyColor();
		}
		else
		{
			fogColor = MC_RENDER.getFogColor(partialTicks);
		}
		
		return fogColor;
	}
	private Color getSpecialFogColor(float partialTicks) { return MC_RENDER.getSpecialFogColor(partialTicks); }
	
	
	
	//===============//
	// API functions //
	//===============//
	
	private void setActiveFramebufferId(int frameBufferId) { activeFramebufferId = frameBufferId; }
	/** Returns -1 if no frame buffer has been bound yet */
	public static int getActiveFramebufferId() { return activeFramebufferId; }
	
	private void setActiveColorTextureId(int colorTextureId) { activeColorTextureId = colorTextureId; }
	/** Returns -1 if no texture has been bound yet */
	public static int getActiveColorTextureId() { return activeColorTextureId; }
	
	private void setActiveDepthTextureId(int depthTextureId) { activeDepthTextureId = depthTextureId; }
	/** Returns -1 if no texture has been bound yet */
	public static int getActiveDepthTextureId() { return activeDepthTextureId; }
	
	
	
	//===================//
	// Cleanup Functions //
	//===================//
	
	/**
	 * cleanup and free all render objects.
	 * (Many objects are Native, outside of JVM, and need manual cleanup)
	 */
	private void cleanup()
	{
		if (GLProxy.getInstance() == null)
		{
			// shouldn't normally happen, but just in case
			EVENT_LOGGER.warn("Renderer Cleanup called but the GLProxy has never been initialized!");
			return;
		}
		
		try
		{
			this.setupLock.lock();
			
			EVENT_LOGGER.info("Queuing Renderer Cleanup for main render thread");
			GLProxy.getInstance().queueRunningOnRenderThread(() ->
			{
				EVENT_LOGGER.info("Renderer Cleanup Started");
				
				if (this.lodRenderProgram != null)
				{
					this.lodRenderProgram.free();
					this.lodRenderProgram = null;
				}
				
				if (this.quadIBO != null)
					this.quadIBO.destroyAsync();
				
				// Delete framebuffer, color texture, and depth texture
				if (this.framebuffer != null && !this.usingMcFrameBuffer)
					this.framebuffer.destroy();
				if (this.nullableColorTexture != null)
					this.nullableColorTexture.destroy();
				if (this.depthTexture != null)
					this.depthTexture.destroy();
				
				EVENT_LOGGER.info("Renderer Cleanup Complete");
			});
		}
		catch (Exception e)
		{
			this.setupLock.unlock();
		}
	}
	
	
	
	//================//
	// helper classes //
	//================//
	
	// TODO move
	public static class LagSpikeCatcher
	{
		long timer = System.nanoTime();
		
		public LagSpikeCatcher() { }
		
		public void end(String source)
		{
			if (!ENABLE_DRAW_LAG_SPIKE_LOGGING)
			{
				return;
			}
			
			this.timer = System.nanoTime() - this.timer;
			if (this.timer > DRAW_LAG_SPIKE_THRESHOLD_NS)
			{
				//4 ms
				EVENT_LOGGER.debug("NOTE: " + source + " took " + Duration.ofNanos(this.timer) + "!");
			}
			
		}
		
	}
	
	
}
