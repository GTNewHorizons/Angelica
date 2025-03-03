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
import com.seibel.distanthorizons.core.render.glObject.shader.ShaderProgram;
import com.seibel.distanthorizons.core.render.renderer.FogRenderer;
import com.seibel.distanthorizons.core.render.renderer.LodRenderer;
import com.seibel.distanthorizons.core.render.renderer.ScreenQuad;
import com.seibel.distanthorizons.core.wrapperInterfaces.minecraft.IMinecraftGLWrapper;
import org.lwjgl.opengl.GL32;

/**
 * Draws the Fog texture onto DH's FrameBuffer. <br><br>
 * 
 * See Also: <br>
 * {@link FogRenderer} - Parent to this shader. <br>
 * {@link FogShader} - draws the Fog texture. <br>
 */
public class FogApplyShader extends AbstractShaderRenderer
{
	public static FogApplyShader INSTANCE = new FogApplyShader();
	
	private static final IMinecraftGLWrapper GLMC = SingletonInjector.INSTANCE.get(IMinecraftGLWrapper.class);
	
	
	public int fogTexture;
	
	// uniforms
	public int colorTextureUniform;
	public int depthTextureUniform;
	
	
	
	//=============//
	// constructor //
	//=============//
	
	@Override
	public void onInit()
	{
		this.shader = new ShaderProgram(
				"shaders/normal.vert",
				"shaders/fog/apply.frag",
				"fragColor",
				new String[]{ "vPosition" });
		
		// uniform setup
		this.colorTextureUniform = this.shader.getUniformLocation("uColorTexture");
		this.depthTextureUniform = this.shader.getUniformLocation("uDepthTexture");
		
	}
	
	
	
	//=============//
	// render prep //
	//=============//
	
	@Override
	protected void onApplyUniforms(float partialTicks)
	{
		GLMC.glActiveTexture(GL32.GL_TEXTURE0);
		GLMC.glBindTexture(this.fogTexture);
		GL32.glUniform1i(this.colorTextureUniform, 0);
		
		GLMC.glActiveTexture(GL32.GL_TEXTURE1);
		GLMC.glBindTexture(LodRenderer.getActiveDepthTextureId());
		GL32.glUniform1i(this.depthTextureUniform, 1);
	}
	
	
	
	//========//
	// render //
	//========//
	
	@Override
	protected void onRender()
	{
		GLMC.enableBlend();
		GL32.glBlendEquation(GL32.GL_FUNC_ADD);
		GLMC.glBlendFuncSeparate(GL32.GL_SRC_ALPHA, GL32.GL_ONE_MINUS_SRC_ALPHA, GL32.GL_ONE, GL32.GL_ONE_MINUS_SRC_ALPHA);
		
		// Depth testing must be disabled otherwise this application shader won't apply anything.
		// setting this isn't necessary in vanilla, but some mods may change this, requiring it to be set manually, 
		// it should be automatically restored after rendering is complete.
		GLMC.disableDepthTest();
		
		
		// apply the rendered Fog to DH's framebuffer
		GLMC.glBindFramebuffer(GL32.GL_READ_FRAMEBUFFER, FogShader.INSTANCE.frameBuffer);
		GLMC.glBindFramebuffer(GL32.GL_DRAW_FRAMEBUFFER, LodRenderer.getActiveFramebufferId());
		
		ScreenQuad.INSTANCE.render();
		
		GLMC.glBindFramebuffer(GL32.GL_READ_FRAMEBUFFER, 0);
	}
	
}
