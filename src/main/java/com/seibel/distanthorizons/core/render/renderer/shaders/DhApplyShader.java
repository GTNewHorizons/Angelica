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

package com.seibel.distanthorizons.core.render.renderer.shaders;

import com.seibel.distanthorizons.core.dependencyInjection.SingletonInjector;
import com.seibel.distanthorizons.core.render.glObject.GLState;
import com.seibel.distanthorizons.core.render.glObject.shader.ShaderProgram;
import com.seibel.distanthorizons.core.render.renderer.LodRenderer;
import com.seibel.distanthorizons.core.render.renderer.ScreenQuad;
import com.seibel.distanthorizons.core.wrapperInterfaces.minecraft.IMinecraftGLWrapper;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.lwjgl.opengl.GL32;

import java.nio.ByteBuffer;

/**
 * Copies {@link LodRenderer}'s currently active color and depth texture to Minecraft's framebuffer. 
 */
public class DhApplyShader extends AbstractShaderRenderer
{
	public static DhApplyShader INSTANCE = new DhApplyShader();
	
	private static final Logger LOGGER = LogManager.getLogger();
	private static final IMinecraftGLWrapper GLMC = SingletonInjector.INSTANCE.get(IMinecraftGLWrapper.class);
	
	
	// uniforms
	public int gDhColorTextureUniform;
	public int gDepthMapUniform;
	
	
	
	private DhApplyShader() { }
	
	@Override
	public void onInit()
	{
		this.shader = new ShaderProgram(
				"shaders/normal.vert",
				"shaders/apply.frag",
				"fragColor",
				new String[]{"vPosition"});
		
		// uniform setup
		this.gDhColorTextureUniform = this.shader.getUniformLocation("gDhColorTexture");
		this.gDepthMapUniform = this.shader.getUniformLocation("gDhDepthTexture");
		
	}
	
	@Override
	protected void onApplyUniforms(float partialTicks) { }
	
	
	//========//
	// render //
	//========//
	
	@Override
	protected void onRender()
	{
		int targetFrameBuffer = MC_RENDER.getTargetFrameBuffer();
		if (targetFrameBuffer == -1)
		{
			return;
		}
		
		
		GLState state = new GLState();
		
		GLMC.disableDepthTest();
		
		GLMC.enableBlend();
		GL32.glBlendEquation(GL32.GL_FUNC_ADD);
		GLMC.glBlendFunc(GL32.GL_ONE, GL32.GL_ONE_MINUS_SRC_ALPHA);
		
		GLMC.glActiveTexture(GL32.GL_TEXTURE0);
		GLMC.glBindTexture(LodRenderer.getActiveColorTextureId());
		GL32.glUniform1i(this.gDhColorTextureUniform, 0);
		
		GLMC.glActiveTexture(GL32.GL_TEXTURE1);
		GLMC.glBindTexture(LodRenderer.getActiveDepthTextureId());
		GL32.glUniform1i(this.gDepthMapUniform, 1);
		
		// Copy to MC's framebuffer
		GLMC.glBindFramebuffer(GL32.GL_FRAMEBUFFER, targetFrameBuffer);
		
		ScreenQuad.INSTANCE.render();
		
		
		// restore everything, except at this point the MC framebuffer should now be used instead
		state.restore();
		GLMC.glBindFramebuffer(GL32.GL_FRAMEBUFFER, targetFrameBuffer);
		
	}
	
}
