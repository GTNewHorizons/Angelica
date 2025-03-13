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
import com.seibel.distanthorizons.core.render.renderer.shaders.SSAOApplyShader;
import com.seibel.distanthorizons.core.render.renderer.shaders.SSAOShader;
import com.seibel.distanthorizons.core.wrapperInterfaces.minecraft.IMinecraftGLWrapper;
import com.seibel.distanthorizons.core.wrapperInterfaces.minecraft.IMinecraftRenderWrapper;
import com.seibel.distanthorizons.core.util.math.Mat4f;
import org.lwjgl.opengl.GL32;

import java.nio.ByteBuffer;

/**
 * Handles adding SSAO via {@link SSAOShader} and {@link SSAOApplyShader}. <br><br>
 * 
 * {@link SSAOShader} - draws the SSAO to a texture. <br>
 * {@link SSAOApplyShader} - draws the SSAO texture to DH's FrameBuffer. <br>
 */
public class SSAORenderer
{
	public static SSAORenderer INSTANCE = new SSAORenderer();
	
	private static final IMinecraftRenderWrapper MC_RENDER = SingletonInjector.INSTANCE.get(IMinecraftRenderWrapper.class);
	private static final IMinecraftGLWrapper GLMC = SingletonInjector.INSTANCE.get(IMinecraftGLWrapper.class);
	
	
	private boolean init = false;
	
	private int width = -1;
	private int height = -1;
	private int ssaoFramebuffer = -1;
	
	private int ssaoTexture = -1;
	
	
	
	//=============//
	// constructor //
	//=============//
	
	private SSAORenderer() { }
	
	public void init()
	{
		if (this.init) return;
		this.init = true;
		
		SSAOShader.INSTANCE.init();
		SSAOApplyShader.INSTANCE.init();
	}
	
	private void createFramebuffer(int width, int height)
	{
		if (this.ssaoFramebuffer != -1)
		{
			GL32.glDeleteFramebuffers(this.ssaoFramebuffer);
			this.ssaoFramebuffer = -1;
		}
		
		if (this.ssaoTexture != -1)
		{
			GLMC.glDeleteTextures(this.ssaoTexture);
			this.ssaoTexture = -1;
		}
		
		this.ssaoFramebuffer = GL32.glGenFramebuffers();
		GLMC.glBindFramebuffer(GL32.GL_FRAMEBUFFER, this.ssaoFramebuffer);
		
		this.ssaoTexture = GLMC.glGenTextures();
		GLMC.glBindTexture(this.ssaoTexture);
		GL32.glTexImage2D(GL32.GL_TEXTURE_2D, 0, GL32.GL_R16F, width, height, 0, GL32.GL_RED, GL32.GL_HALF_FLOAT, (ByteBuffer) null);
		GL32.glTexParameteri(GL32.GL_TEXTURE_2D, GL32.GL_TEXTURE_MIN_FILTER, GL32.GL_LINEAR);
		GL32.glTexParameteri(GL32.GL_TEXTURE_2D, GL32.GL_TEXTURE_MAG_FILTER, GL32.GL_LINEAR);
		GL32.glFramebufferTexture2D(GL32.GL_FRAMEBUFFER, GL32.GL_COLOR_ATTACHMENT0, GL32.GL_TEXTURE_2D, this.ssaoTexture, 0);
	}
	
	
	
	//========//
	// render //
	//========//
	
	public void render(Mat4f projectionMatrix, float partialTicks)
	{
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
		
		SSAOShader.INSTANCE.frameBuffer = this.ssaoFramebuffer;
		SSAOShader.INSTANCE.setProjectionMatrix(projectionMatrix);
		SSAOShader.INSTANCE.render(partialTicks);
		
		SSAOApplyShader.INSTANCE.ssaoTexture = this.ssaoTexture;
		SSAOApplyShader.INSTANCE.render(partialTicks);
		
		state.restore();
	}
	
	public void free()
	{
		SSAOShader.INSTANCE.free();
		SSAOApplyShader.INSTANCE.free();
	}
	
}
