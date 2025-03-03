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

import com.seibel.distanthorizons.core.dependencyInjection.SingletonInjector;
import com.seibel.distanthorizons.core.render.glObject.GLState;
import com.seibel.distanthorizons.core.render.renderer.shaders.FogApplyShader;
import com.seibel.distanthorizons.core.render.renderer.shaders.FogShader;
import com.seibel.distanthorizons.core.util.math.Mat4f;
import com.seibel.distanthorizons.core.wrapperInterfaces.minecraft.IMinecraftGLWrapper;
import com.seibel.distanthorizons.core.wrapperInterfaces.minecraft.IMinecraftRenderWrapper;
import org.lwjgl.opengl.GL32;

import java.nio.ByteBuffer;

/**
 * Handles adding SSAO via {@link FogShader} and {@link FogApplyShader}. <br><br>
 * 
 * {@link FogShader} - draws the Fog to a texture. <br>
 * {@link FogApplyShader} - draws the Fog texture to DH's FrameBuffer. <br>
 */
public class FogRenderer
{
	public static FogRenderer INSTANCE = new FogRenderer();
	
	private static final IMinecraftRenderWrapper MC_RENDER = SingletonInjector.INSTANCE.get(IMinecraftRenderWrapper.class);
	private static final IMinecraftGLWrapper GLMC = SingletonInjector.INSTANCE.get(IMinecraftGLWrapper.class);
	
	
	private boolean init = false;
	
	private int width = -1;
	private int height = -1;
	private int fogFramebuffer = -1;
	
	private int fogTexture = -1;
	
	
	
	//=============//
	// constructor //
	//=============//
	
	private FogRenderer() { }
	
	public void init()
	{
		if (this.init) return;
		this.init = true;
		
		FogShader.INSTANCE.init();
		FogApplyShader.INSTANCE.init();
	}
	
	private void createFramebuffer(int width, int height)
	{
		if (this.fogFramebuffer != -1)
		{
			GL32.glDeleteFramebuffers(this.fogFramebuffer);
			this.fogFramebuffer = -1;
		}
		
		if (this.fogTexture != -1)
		{
			GLMC.glDeleteTextures(this.fogTexture);
			this.fogTexture = -1;
		}
		
		this.fogFramebuffer = GL32.glGenFramebuffers();
		GLMC.glBindFramebuffer(GL32.GL_FRAMEBUFFER, this.fogFramebuffer);
		
		this.fogTexture = GLMC.glGenTextures();
		GLMC.glBindTexture(this.fogTexture);
		GL32.glTexImage2D(GL32.GL_TEXTURE_2D, 0, GL32.GL_RGBA16, width, height, 0, GL32.GL_RGBA, GL32.GL_UNSIGNED_SHORT_4_4_4_4, (ByteBuffer) null);
		GL32.glTexParameteri(GL32.GL_TEXTURE_2D, GL32.GL_TEXTURE_MIN_FILTER, GL32.GL_LINEAR);
		GL32.glTexParameteri(GL32.GL_TEXTURE_2D, GL32.GL_TEXTURE_MAG_FILTER, GL32.GL_LINEAR);
		GL32.glFramebufferTexture2D(GL32.GL_FRAMEBUFFER, GL32.GL_COLOR_ATTACHMENT0, GL32.GL_TEXTURE_2D, this.fogTexture, 0);
	}
	
	
	
	//========//
	// render //
	//========//
	
	public void render(Mat4f modelViewProjectionMatrix, float partialTicks)
	{
		// needed in MC 1.16.5 probably due to MC not manually setting each GL state they need before the next rendering step
		GLState state = new GLState();
		
		this.init();
		
		// resize the framebuffer if necessary
		int width = MC_RENDER.getTargetFrameBufferViewportWidth();
		int height = MC_RENDER.getTargetFrameBufferViewportHeight();
		if (this.width != width || this.height != height)
		{
			this.width = width;
			this.height = height;
			this.createFramebuffer(width, height);
		}
		
		FogShader.INSTANCE.frameBuffer = this.fogFramebuffer;
		FogShader.INSTANCE.setProjectionMatrix(modelViewProjectionMatrix);
		FogShader.INSTANCE.render(partialTicks);
		
		FogApplyShader.INSTANCE.fogTexture = this.fogTexture;
		FogApplyShader.INSTANCE.render(partialTicks);
		
		state.restore();
	}
	
	public void free()
	{
		FogShader.INSTANCE.free();
		FogApplyShader.INSTANCE.free();
	}
	
}
