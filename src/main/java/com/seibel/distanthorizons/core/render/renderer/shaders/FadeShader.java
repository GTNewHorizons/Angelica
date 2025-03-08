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
import com.seibel.distanthorizons.core.util.LodUtil;
import com.seibel.distanthorizons.core.util.RenderUtil;
import com.seibel.distanthorizons.core.util.math.Mat4f;
import com.seibel.distanthorizons.core.wrapperInterfaces.minecraft.IMinecraftClientWrapper;
import com.seibel.distanthorizons.core.wrapperInterfaces.minecraft.IMinecraftGLWrapper;
import com.seibel.distanthorizons.core.wrapperInterfaces.minecraft.IMinecraftRenderWrapper;
import org.lwjgl.opengl.GL32;

public class FadeShader extends AbstractShaderRenderer
{
	public static FadeShader INSTANCE = new FadeShader();
	
	private static final IMinecraftClientWrapper MC_CLIENT = SingletonInjector.INSTANCE.get(IMinecraftClientWrapper.class);
	private static final IMinecraftRenderWrapper MC_RENDER = SingletonInjector.INSTANCE.get(IMinecraftRenderWrapper.class);
	private static final IMinecraftGLWrapper GLMC = SingletonInjector.INSTANCE.get(IMinecraftGLWrapper.class);
	
	
	public int frameBuffer = -1;
	
	private Mat4f inverseMcMvmProjMatrix;
	private Mat4f inverseDhMvmProjMatrix;
	private float levelMaxHeight;
	
	
	// Uniforms
	public int uMcDepthTexture = -1;
	public int uDhDepthTexture = -1;
	public int uCombinedMcDhColorTexture = -1;
	public int uDhColorTexture = -1;
	
	/** Inverted Model View Projection matrix */
	public int uDhInvMvmProj = -1;
	public int uMcInvMvmProj = -1;
	
	public int uStartFadeBlockDistance = -1;
	public int uEndFadeBlockDistance = -1;
	
	public int uMaxLevelHeight = -1;
	
	
	
	
	//=============//
	// constructor //
	//=============//
	
	public FadeShader() {  }

	@Override
	public void onInit()
	{
		this.shader = new ShaderProgram(
				"shaders/normal.vert", "shaders/fade/fade.frag",
				"fragColor", new String[]{"vPosition"}
		);
		
		// all uniforms should be tryGet...
		// because disabling fade can cause the GLSL to optimize out most (if not all) uniforms
		
		// near fade
		this.uDhInvMvmProj = this.shader.tryGetUniformLocation("uDhInvMvmProj");
		this.uMcInvMvmProj = this.shader.tryGetUniformLocation("uMcInvMvmProj");
		
		this.uMcDepthTexture = this.shader.tryGetUniformLocation("uMcDepthMap");
		this.uDhDepthTexture = this.shader.tryGetUniformLocation("uDhDepthTexture");
		this.uCombinedMcDhColorTexture = this.shader.tryGetUniformLocation("uCombinedMcDhColorTexture");
		this.uDhColorTexture = this.shader.tryGetUniformLocation("uDhColorTexture");
		
		this.uStartFadeBlockDistance = this.shader.tryGetUniformLocation("uStartFadeBlockDistance");
		this.uEndFadeBlockDistance = this.shader.tryGetUniformLocation("uEndFadeBlockDistance");
		
		this.uMaxLevelHeight = this.shader.tryGetUniformLocation("uMaxLevelHeight");
		
	}
	
	
	
	//=============//
	// render prep //
	//=============//
	
	@Override
	protected void onApplyUniforms(float partialTicks)
	{
		if (this.inverseMcMvmProjMatrix != null) this.shader.setUniform(this.uMcInvMvmProj, this.inverseMcMvmProjMatrix);
		if (this.inverseDhMvmProjMatrix != null) this.shader.setUniform(this.uDhInvMvmProj, this.inverseDhMvmProjMatrix);
		
		
		float dhNearClipDistance = RenderUtil.getNearClipPlaneInBlocksForFading(partialTicks);
		// this added value prevents the near clip plane and discard circle from touching, which looks bad
		dhNearClipDistance += 16f;
		
		// measured in blocks
		// these multipliers in James' tests should provide a fairly smooth transition
		// without having underdraw issues
		float fadeStartDistance = dhNearClipDistance * 1.5f;
		float fadeEndDistance = dhNearClipDistance * 1.9f;
		
		if (this.uStartFadeBlockDistance != -1) this.shader.setUniform(this.uStartFadeBlockDistance, fadeStartDistance);
		if (this.uEndFadeBlockDistance != -1) this.shader.setUniform(this.uEndFadeBlockDistance, fadeEndDistance);
		
		if (this.uMaxLevelHeight != -1) this.shader.setUniform(this.uMaxLevelHeight, this.levelMaxHeight);
	}
	
	public void setProjectionMatrix(Mat4f mcModelViewMatrix, Mat4f mcProjectionMatrix, float partialTicks)
	{
		Mat4f inverseMcModelViewProjectionMatrix = new Mat4f(mcProjectionMatrix);
		inverseMcModelViewProjectionMatrix.multiply(mcModelViewMatrix);
		inverseMcModelViewProjectionMatrix.invert();
		this.inverseMcMvmProjMatrix = inverseMcModelViewProjectionMatrix;
		
		
		Mat4f dhProjectionMatrix = RenderUtil.createLodProjectionMatrix(mcProjectionMatrix, partialTicks);
		Mat4f dhModelViewMatrix = RenderUtil.createLodModelViewMatrix(mcModelViewMatrix);
		
		Mat4f inverseDhModelViewProjectionMatrix = new Mat4f(dhProjectionMatrix);
		inverseDhModelViewProjectionMatrix.multiply(dhModelViewMatrix);
		inverseDhModelViewProjectionMatrix.invert();
		this.inverseDhMvmProjMatrix = inverseDhModelViewProjectionMatrix;
	}
	public void setLevelMaxHeight(int levelMaxHeight) { this.levelMaxHeight = levelMaxHeight; }
	
	
	
	//========//
	// render //
	//========//
	
	@Override
	protected void onRender()
	{
		GLMC.glBindFramebuffer(GL32.GL_FRAMEBUFFER, this.frameBuffer);
		GLMC.disableScissorTest();
		GLMC.disableDepthTest();
		GLMC.disableBlend();
		
		GLMC.glActiveTexture(GL32.GL_TEXTURE0);
		GLMC.glBindTexture(MC_RENDER.getDepthTextureId());
		GL32.glUniform1i(this.uMcDepthTexture, 0);
		
		GLMC.glActiveTexture(GL32.GL_TEXTURE1);
		GLMC.glBindTexture(LodRenderer.getActiveDepthTextureId());
		GL32.glUniform1i(this.uDhDepthTexture, 1);
		
		GLMC.glActiveTexture(GL32.GL_TEXTURE2);
		GLMC.glBindTexture(MC_RENDER.getColorTextureId());
		GL32.glUniform1i(this.uCombinedMcDhColorTexture, 2);
		
		GLMC.glActiveTexture(GL32.GL_TEXTURE3);
		GLMC.glBindTexture(LodRenderer.getActiveColorTextureId());
		GL32.glUniform1i(this.uDhColorTexture, 3);
		
		
		ScreenQuad.INSTANCE.render();
	}
	
}
