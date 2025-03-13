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

import com.seibel.distanthorizons.core.config.Config;
import com.seibel.distanthorizons.core.dependencyInjection.SingletonInjector;
import com.seibel.distanthorizons.core.render.glObject.shader.ShaderProgram;
import com.seibel.distanthorizons.core.render.renderer.LodRenderer;
import com.seibel.distanthorizons.core.render.renderer.SSAORenderer;
import com.seibel.distanthorizons.core.render.renderer.ScreenQuad;
import com.seibel.distanthorizons.core.util.RenderUtil;
import com.seibel.distanthorizons.core.wrapperInterfaces.minecraft.IMinecraftGLWrapper;
import org.lwjgl.opengl.GL32;

/**
 * Draws the SSAO texture onto DH's FrameBuffer. <br><br>
 * 
 * See Also: <br>
 * {@link SSAORenderer} - Parent to this shader. <br>
 * {@link SSAOShader} - draws the SSAO texture. <br>
 */
public class SSAOApplyShader extends AbstractShaderRenderer
{
	public static SSAOApplyShader INSTANCE = new SSAOApplyShader();
	
	private static final IMinecraftGLWrapper GLMC = SingletonInjector.INSTANCE.get(IMinecraftGLWrapper.class);
	
	
	public int ssaoTexture;
	
	// uniforms
	public int gSSAOMapUniform;
	public int gDepthMapUniform;
	public int gViewSizeUniform;
	public int gBlurRadiusUniform;
	public int gNearUniform;
	public int gFarUniform;
	
	
	
	//=============//
	// constructor //
	//=============//
	
	@Override
	public void onInit()
	{
		this.shader = new ShaderProgram(
				"shaders/normal.vert",
				"shaders/ssao/apply.frag",
				"fragColor",
				new String[]{"vPosition"});
		
		// uniform setup
		this.gSSAOMapUniform = this.shader.getUniformLocation("gSSAOMap");
		this.gDepthMapUniform = this.shader.getUniformLocation("gDepthMap");
		this.gViewSizeUniform = this.shader.tryGetUniformLocation("gViewSize");
		this.gBlurRadiusUniform = this.shader.tryGetUniformLocation("gBlurRadius");
		this.gNearUniform = this.shader.tryGetUniformLocation("gNear");
		this.gFarUniform = this.shader.tryGetUniformLocation("gFar");
	}
	
	
	
	//=============//
	// render prep //
	//=============//
	
	@Override
	protected void onApplyUniforms(float partialTicks)
	{
		GLMC.glActiveTexture(GL32.GL_TEXTURE0);
		GLMC.glBindTexture(LodRenderer.getActiveDepthTextureId());
		GL32.glUniform1i(this.gDepthMapUniform, 0);
		
		GLMC.glActiveTexture(GL32.GL_TEXTURE1);
		GLMC.glBindTexture(this.ssaoTexture);
		GL32.glUniform1i(this.gSSAOMapUniform, 1);
		
		GL32.glUniform1i(this.gBlurRadiusUniform, Config.Client.Advanced.Graphics.Ssao.blurRadius.get());
		
		if (this.gViewSizeUniform >= 0)
		{
			GL32.glUniform2f(this.gViewSizeUniform,
					MC_RENDER.getTargetFrameBufferViewportWidth(),
					MC_RENDER.getTargetFrameBufferViewportHeight());
		}
		
		if (this.gNearUniform >= 0)
		{
			GL32.glUniform1f(this.gNearUniform,
					RenderUtil.getNearClipPlaneDistanceInBlocks(partialTicks));
		}
		
		if (this.gFarUniform >= 0)
		{
			float farClipPlane = RenderUtil.getFarClipPlaneDistanceInBlocks();
			GL32.glUniform1f(this.gFarUniform, farClipPlane);
		}
	}
	
	
	
	//========//
	// render //
	//========//
	
	@Override
	protected void onRender()
	{
		GLMC.enableBlend();
		GL32.glBlendEquation(GL32.GL_FUNC_ADD);
		GLMC.glBlendFuncSeparate(GL32.GL_ZERO, GL32.GL_SRC_ALPHA, GL32.GL_ZERO, GL32.GL_ONE);

		// Depth testing must be disabled otherwise this application shader won't apply anything.
		// setting this isn't necessary in vanilla, but some mods may change this, requiring it to be set manually, 
		// it should be automatically restored after rendering is complete.
		GLMC.disableDepthTest();
		
		GLMC.glActiveTexture(GL32.GL_TEXTURE0);
		GLMC.glBindTexture(0);
		
		// apply the rendered SSAO to the LODs 
		GLMC.glBindFramebuffer(GL32.GL_READ_FRAMEBUFFER, SSAOShader.INSTANCE.frameBuffer);
		GLMC.glBindFramebuffer(GL32.GL_DRAW_FRAMEBUFFER, LodRenderer.getActiveFramebufferId());
		
		
		ScreenQuad.INSTANCE.render();
		
	}
}
