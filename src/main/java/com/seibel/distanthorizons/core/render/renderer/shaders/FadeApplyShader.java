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
import com.seibel.distanthorizons.core.render.renderer.FadeRenderer;
import com.seibel.distanthorizons.core.render.renderer.LodRenderer;
import com.seibel.distanthorizons.core.render.renderer.ScreenQuad;
import com.seibel.distanthorizons.core.wrapperInterfaces.minecraft.IMinecraftGLWrapper;
import com.seibel.distanthorizons.core.wrapperInterfaces.minecraft.IMinecraftRenderWrapper;
import org.lwjgl.opengl.GL32;

/**
 * Draws the Fade texture onto Minecraft's FrameBuffer. <br><br>
 * 
 * See Also: <br>
 * {@link FadeRenderer} - Parent to this shader. <br>
 * {@link FadeShader} - draws the Fade texture. <br>
 */
public class FadeApplyShader extends AbstractShaderRenderer
{
	public static FadeApplyShader INSTANCE = new FadeApplyShader();
	
	private static final IMinecraftRenderWrapper MC_RENDER = SingletonInjector.INSTANCE.get(IMinecraftRenderWrapper.class);
	private static final IMinecraftGLWrapper GLMC = SingletonInjector.INSTANCE.get(IMinecraftGLWrapper.class);
	
	
	
	public int fadeTexture;
	
	// uniforms
	public int uFadeColorTextureUniform = -1;
	public int uDhDepthTextureUniform = -1;
	public int uMcDepthTextureUniform = -1;
	
	
	
	//=============//
	// constructor //
	//=============//
	
	@Override
	public void onInit()
	{
		this.shader = new ShaderProgram(
				"shaders/normal.vert",
				"shaders/fade/apply.frag",
				"fragColor",
				new String[]{ "vPosition" });
		
		// uniform setup
		this.uFadeColorTextureUniform = this.shader.getUniformLocation("uFadeColorTextureUniform");
		this.uMcDepthTextureUniform = this.shader.getUniformLocation("uMcDepthTextureUniform");
		
	}
	
	
	
	//=============//
	// render prep //
	//=============//
	
	@Override
	protected void onApplyUniforms(float partialTicks)
	{
		GLMC.glActiveTexture(GL32.GL_TEXTURE0);
		GLMC.glBindTexture(this.fadeTexture);
		GL32.glUniform1i(this.uFadeColorTextureUniform, 0);
		
		GLMC.glActiveTexture(GL32.GL_TEXTURE1);
		GLMC.glBindTexture(LodRenderer.getActiveDepthTextureId());
		GL32.glUniform1i(this.uDhDepthTextureUniform, 1);
		
		GLMC.glActiveTexture(GL32.GL_TEXTURE2);
		GLMC.glBindTexture(MC_RENDER.getDepthTextureId());
		GL32.glUniform1i(this.uMcDepthTextureUniform, 2);
		
	}
	
	
	
	//========//
	// render //
	//========//
	
	@Override
	protected void onRender()
	{
		GLMC.disableBlend();
		
		// Depth testing must be disabled otherwise this application shader won't apply anything.
		// setting this isn't necessary in vanilla, but some mods may change this, requiring it to be set manually, 
		// it should be automatically restored after rendering is complete.
		GLMC.disableDepthTest();
		
		
		// apply the rendered Fade to Minecraft's framebuffer
		GLMC.glBindFramebuffer(GL32.GL_READ_FRAMEBUFFER, FadeShader.INSTANCE.frameBuffer);
		GLMC.glBindFramebuffer(GL32.GL_DRAW_FRAMEBUFFER, MC_RENDER.getTargetFrameBuffer());
		
		ScreenQuad.INSTANCE.render();
		
		GLMC.enableDepthTest();
	}
	
}
