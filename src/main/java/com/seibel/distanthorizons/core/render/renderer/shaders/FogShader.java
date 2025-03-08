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

import com.seibel.distanthorizons.api.enums.rendering.EDhApiFogColorMode;
import com.seibel.distanthorizons.api.enums.rendering.EDhApiHeightFogDirection;
import com.seibel.distanthorizons.api.enums.rendering.EDhApiHeightFogMixMode;
import com.seibel.distanthorizons.core.config.Config;
import com.seibel.distanthorizons.core.dependencyInjection.SingletonInjector;
import com.seibel.distanthorizons.core.render.glObject.shader.ShaderProgram;
import com.seibel.distanthorizons.core.render.renderer.LodRenderer;
import com.seibel.distanthorizons.core.render.renderer.ScreenQuad;
import com.seibel.distanthorizons.core.util.LodUtil;
import com.seibel.distanthorizons.core.wrapperInterfaces.minecraft.IMinecraftClientWrapper;
import com.seibel.distanthorizons.core.util.math.Mat4f;
import com.seibel.distanthorizons.core.wrapperInterfaces.minecraft.IMinecraftGLWrapper;
import org.lwjgl.opengl.GL32;

import java.awt.*;

public class FogShader extends AbstractShaderRenderer
{
	public static final FogShader INSTANCE = new FogShader();
	
	private static final IMinecraftClientWrapper MC = SingletonInjector.INSTANCE.get(IMinecraftClientWrapper.class);
	private static final IMinecraftGLWrapper GLMC = SingletonInjector.INSTANCE.get(IMinecraftGLWrapper.class);
	
	
	public int frameBuffer;
	
	private Mat4f inverseMvmProjMatrix;
	
	
	//==========//
	// Uniforms //
	//==========//
	
	public int uDepthMap;
	/** Inverted Model View Projection matrix */
	public int uInvMvmProj;
	
	// fog uniforms
	public int uFogColor;
	public int uFogScale;
	public int uFogVerticalScale;
	public int uFullFogMode;
	
	// far fog
	public int uFarFogStart;
	public int uFarFogLength;
	public int uFarFogMin;
	public int uFarFogRange;
	public int uFarFogDensity;
	
	// height fog
	public int uHeightFogStart;
	public int uHeightFogLength;
	public int uHeightFogMin;
	public int uHeightFogRange;
	public int uHeightFogDensity;
	
	public int uHeightFogEnabled;
	public int uHeightFogFalloffType;
	public int uHeightBasedOnCamera;
	public int uHeightFogBaseHeight;
	public int uHeightFogAppliesUp;
	public int uHeightFogAppliesDown;
	public int uUseSphericalFog;
	public int uHeightFogMixingMode;
	public int uCameraBlockYPos;
	
	
	
	//=============//
	// constructor //
	//=============//
	
	public FogShader() { }
	
	@Override
	public void onInit()
	{
		this.shader = new ShaderProgram(
				"shaders/normal.vert", "shaders/fog/fog.frag",
				"fragColor", new String[]{"vPosition"}
		);
		
		// all uniforms should be tryGet...
		// because disabling fog can cause the GLSL to optimize out most (if not all) uniforms
		
		this.uDepthMap = this.shader.getUniformLocation("uDepthMap");
		this.uInvMvmProj = this.shader.getUniformLocation("uInvMvmProj");
		
		// Fog uniforms
		this.uFogScale = this.shader.getUniformLocation("uFogScale");
		this.uFogVerticalScale = this.shader.getUniformLocation("uFogVerticalScale");
		this.uFogColor = this.shader.getUniformLocation("uFogColor");
		this.uFullFogMode = this.shader.getUniformLocation("uFullFogMode");
		
		// fog config
		this.uFarFogStart = this.shader.getUniformLocation("uFarFogStart");
		this.uFarFogLength = this.shader.getUniformLocation("uFarFogLength");
		this.uFarFogMin = this.shader.getUniformLocation("uFarFogMin");
		this.uFarFogRange = this.shader.getUniformLocation("uFarFogRange");
		this.uFarFogDensity = this.shader.getUniformLocation("uFarFogDensity");
		
		// height fog
		this.uHeightFogStart = this.shader.getUniformLocation("uHeightFogStart");
		this.uHeightFogLength = this.shader.getUniformLocation("uHeightFogLength");
		this.uHeightFogMin = this.shader.getUniformLocation("uHeightFogMin");
		this.uHeightFogRange = this.shader.getUniformLocation("uHeightFogRange");
		this.uHeightFogDensity = this.shader.getUniformLocation("uHeightFogDensity");
		
		this.uHeightFogEnabled = this.shader.getUniformLocation("uHeightFogEnabled");
		this.uHeightFogFalloffType = this.shader.getUniformLocation("uHeightFogFalloffType");
		this.uHeightBasedOnCamera = this.shader.getUniformLocation("uHeightBasedOnCamera");
		this.uHeightFogBaseHeight = this.shader.getUniformLocation("uHeightFogBaseHeight");
		this.uHeightFogAppliesUp = this.shader.getUniformLocation("uHeightFogAppliesUp");
		this.uHeightFogAppliesDown = this.shader.getUniformLocation("uHeightFogAppliesDown");
		this.uUseSphericalFog = this.shader.getUniformLocation("uUseSphericalFog");
		this.uHeightFogMixingMode = this.shader.getUniformLocation("uHeightFogMixingMode");
		this.uCameraBlockYPos = this.shader.getUniformLocation("uCameraBlockYPos");
			
	}
	
	
	
	//=============//
	// render prep //
	//=============//
	
	@Override
	protected void onApplyUniforms(float partialTicks)
	{
		int lodDrawDistance = Config.Client.Advanced.Graphics.Quality.lodChunkRenderDistanceRadius.get() * LodUtil.CHUNK_WIDTH;
		
		
		
		if (this.inverseMvmProjMatrix != null)
		{
			this.shader.setUniform(this.uInvMvmProj, this.inverseMvmProjMatrix);
		}
		
		
		// Fog uniforms
		this.shader.setUniform(this.uFogColor, MC_RENDER.isFogStateSpecial() ? this.getSpecialFogColor(partialTicks) : this.getFogColor(partialTicks));
		this.shader.setUniform(this.uFogScale, 1.f / lodDrawDistance);
		this.shader.setUniform(this.uFogVerticalScale, 1.f / MC.getWrappedClientLevel().getMaxHeight());
		this.shader.setUniform(this.uFullFogMode, MC_RENDER.isFogStateSpecial() ? 1 : 0);
		
		
		// fog config
		float farFogStart = Config.Client.Advanced.Graphics.Fog.farFogStart.get().floatValue();
		float farFogEnd = Config.Client.Advanced.Graphics.Fog.farFogEnd.get().floatValue();
		float farFogMin = Config.Client.Advanced.Graphics.Fog.farFogMin.get().floatValue();
		float farFogMax = Config.Client.Advanced.Graphics.Fog.farFogMax.get().floatValue();
		float farFogDensity = Config.Client.Advanced.Graphics.Fog.farFogDensity.get().floatValue();
		
		this.shader.setUniform(this.uFarFogStart, farFogStart);
		this.shader.setUniform(this.uFarFogLength, farFogEnd - farFogStart);
		this.shader.setUniform(this.uFarFogMin, farFogMin);
		this.shader.setUniform(this.uFarFogRange, farFogMax - farFogMin);
		this.shader.setUniform(this.uFarFogDensity, farFogDensity);
		
		
		// height config
		EDhApiHeightFogMixMode heightFogMixingMode = Config.Client.Advanced.Graphics.Fog.HeightFog.heightFogMixMode.get();
		boolean heightFogEnabled = heightFogMixingMode != EDhApiHeightFogMixMode.SPHERICAL && heightFogMixingMode != EDhApiHeightFogMixMode.CYLINDRICAL;
		boolean useSphericalFog = heightFogMixingMode == EDhApiHeightFogMixMode.SPHERICAL;
		EDhApiHeightFogDirection heightFogCameraDirection = Config.Client.Advanced.Graphics.Fog.HeightFog.heightFogDirection.get();
		
		float heightFogStart = Config.Client.Advanced.Graphics.Fog.HeightFog.heightFogStart.get().floatValue();
		float heightFogEnd = Config.Client.Advanced.Graphics.Fog.HeightFog.heightFogEnd.get().floatValue();
		float heightFogMin = Config.Client.Advanced.Graphics.Fog.HeightFog.heightFogMin.get().floatValue();
		float heightFogMax = Config.Client.Advanced.Graphics.Fog.HeightFog.heightFogMax.get().floatValue();
		float heightFogDensity = Config.Client.Advanced.Graphics.Fog.HeightFog.heightFogDensity.get().floatValue();
		
		this.shader.setUniform(this.uHeightFogStart, heightFogStart);
		this.shader.setUniform(this.uHeightFogLength, heightFogEnd - heightFogStart);
		this.shader.setUniform(this.uHeightFogMin, heightFogMin);
		this.shader.setUniform(this.uHeightFogRange, heightFogMax - heightFogMin);
		this.shader.setUniform(this.uHeightFogDensity, heightFogDensity);
		
		
		this.shader.setUniform(this.uHeightFogEnabled, heightFogEnabled);
		this.shader.setUniform(this.uHeightFogFalloffType, Config.Client.Advanced.Graphics.Fog.HeightFog.heightFogFalloff.get().value);
		this.shader.setUniform(this.uHeightFogBaseHeight, Config.Client.Advanced.Graphics.Fog.HeightFog.heightFogBaseHeight.get().floatValue());
		this.shader.setUniform(this.uHeightBasedOnCamera, heightFogCameraDirection.basedOnCamera);
		this.shader.setUniform(this.uHeightFogAppliesUp, heightFogCameraDirection.fogAppliesUp);
		this.shader.setUniform(this.uHeightFogAppliesDown, heightFogCameraDirection.fogAppliesDown);
		this.shader.setUniform(this.uUseSphericalFog, useSphericalFog);
		this.shader.setUniform(this.uHeightFogMixingMode, heightFogMixingMode.value);
		this.shader.setUniform(this.uCameraBlockYPos, (float)MC_RENDER.getCameraExactPosition().y);
		
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
	
	public void setProjectionMatrix(Mat4f projectionMatrix)
	{
		this.inverseMvmProjMatrix = new Mat4f(projectionMatrix);
		this.inverseMvmProjMatrix.invert();
	}
	
	
	
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
		GLMC.glBindTexture(LodRenderer.getActiveDepthTextureId());
		GL32.glUniform1i(this.uDepthMap, 0);
		
		// this is necessary for MC 1.16 (IE Legacy OpenGL)
		// otherwise the framebuffer isn't cleared correctly and the fog smears across the screen
		GL32.glClear(GL32.GL_COLOR_BUFFER_BIT | GL32.GL_DEPTH_BUFFER_BIT);
		
		
		ScreenQuad.INSTANCE.render();
	}
	
}
