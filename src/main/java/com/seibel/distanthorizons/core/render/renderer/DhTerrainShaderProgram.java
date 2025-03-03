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

import com.seibel.distanthorizons.api.interfaces.override.rendering.IDhApiShaderProgram;
import com.seibel.distanthorizons.api.methods.events.sharedParameterObjects.DhApiRenderParam;
import com.seibel.distanthorizons.api.objects.math.DhApiVec3f;
import com.seibel.distanthorizons.core.config.Config;
import com.seibel.distanthorizons.core.render.glObject.GLProxy;
import com.seibel.distanthorizons.core.render.glObject.shader.Shader;
import com.seibel.distanthorizons.core.render.glObject.shader.ShaderProgram;
import com.seibel.distanthorizons.core.render.glObject.vertexAttribute.AbstractVertexAttribute;
import com.seibel.distanthorizons.core.render.glObject.vertexAttribute.VertexAttributePostGL43;
import com.seibel.distanthorizons.core.render.glObject.vertexAttribute.VertexAttributePreGL43;
import com.seibel.distanthorizons.core.render.glObject.vertexAttribute.VertexPointer;
import com.seibel.distanthorizons.core.util.LodUtil;
import com.seibel.distanthorizons.core.util.RenderUtil;
import com.seibel.distanthorizons.core.util.math.Mat4f;
import com.seibel.distanthorizons.core.util.math.Vec3f;

/**
 * Handles rendering the normal LOD terrain.
 */
public class DhTerrainShaderProgram extends ShaderProgram implements IDhApiShaderProgram
{
	public final AbstractVertexAttribute vao;
	
	// Uniforms
	public int uCombinedMatrix = -1;
	public int uModelOffset = -1;
	public int uWorldYOffset = -1;
	public int uDitherDhRendering = -1;
	
	public int uMircoOffset = -1;
	
	public int uEarthRadius = -1;
	
	public int uLightMap = -1;
	
	// Fog/Clip Uniforms
	public int uClipDistance = -1;
	
	// Noise Uniforms
	public int uNoiseEnabled = -1;
	public int uNoiseSteps = -1;
	public int uNoiseIntensity = -1;
	public int uNoiseDropoff = -1;
	
	// Debug Uniform
	public int uWhiteWorld = -1;
	
	
	
	//=============//
	// constructor //
	//=============//
	
	// This will bind  AbstractVertexAttribute
	public DhTerrainShaderProgram()
	{
		super(
				() -> Shader.loadFile(Config.Client.Advanced.Graphics.Experimental.earthCurveRatio.get() != 0 
								? "shaders/curve.vert"
								: "shaders/standard.vert",
						false, new StringBuilder()).toString(),
				() -> Shader.loadFile("shaders/flat_shaded.frag", false, new StringBuilder()).toString(),
				"fragColor", new String[]{"vPosition", "color"});
		
		this.uCombinedMatrix = this.getUniformLocation("uCombinedMatrix");
		this.uModelOffset = this.getUniformLocation("uModelOffset");
		this.uWorldYOffset = this.tryGetUniformLocation("uWorldYOffset");
		this.uDitherDhRendering = this.tryGetUniformLocation("uDitherDhRendering");
		this.uMircoOffset = this.getUniformLocation("uMircoOffset");
		this.uEarthRadius = this.tryGetUniformLocation("uEarthRadius");
		
		this.uLightMap = this.getUniformLocation("uLightMap");
		
		// Fog/Clip Uniforms
		this.uClipDistance = this.getUniformLocation("uClipDistance");
		
		// Noise Uniforms
		this.uNoiseEnabled = this.getUniformLocation("uNoiseEnabled");
		this.uNoiseSteps = this.getUniformLocation("uNoiseSteps");
		this.uNoiseIntensity = this.getUniformLocation("uNoiseIntensity");
		this.uNoiseDropoff = this.getUniformLocation("uNoiseDropoff");
		
		// Debug Uniform
		this.uWhiteWorld = this.getUniformLocation("uWhiteWorld");
		
		
		// TODO: Add better use of the LODFormat thing
		int vertexByteCount = LodUtil.LOD_VERTEX_FORMAT.getByteSize();
		if (GLProxy.getInstance().vertexAttributeBufferBindingSupported)
		{
			this.vao = new VertexAttributePostGL43(); // also binds AbstractVertexAttribute
		}
		else
		{
			this.vao = new VertexAttributePreGL43(); // also binds AbstractVertexAttribute
		}
		this.vao.bind();
		
		// TODO comment what each attribute represents
		this.vao.setVertexAttribute(0, 0, VertexPointer.addUnsignedShortsPointer(4, false, true)); // 2+2+2+2 // TODO probably color, blockpos
		this.vao.setVertexAttribute(0, 1, VertexPointer.addUnsignedBytesPointer(4, true, false)); // +4 // TODO ?
		this.vao.setVertexAttribute(0, 2, VertexPointer.addUnsignedBytesPointer(4, true, true)); // +4 // TODO probably normal index and Iris block ID
		
		try
		{
			this.vao.completeAndCheck(vertexByteCount);
		}
		catch (RuntimeException e)
		{
			System.out.println(LodUtil.LOD_VERTEX_FORMAT);
			throw e;
		}
		
		if (this.uEarthRadius != -1) this.setUniform(this.uEarthRadius,
				/*6371KM*/ 6371000.0f / Config.Client.Advanced.Graphics.Experimental.earthCurveRatio.get());
		
		
		// Noise Uniforms
		this.setUniform(this.uNoiseEnabled, Config.Client.Advanced.Graphics.NoiseTexture.enableNoiseTexture.get());
		this.setUniform(this.uNoiseSteps, Config.Client.Advanced.Graphics.NoiseTexture.noiseSteps.get());
		this.setUniform(this.uNoiseIntensity, Config.Client.Advanced.Graphics.NoiseTexture.noiseIntensity.get().floatValue());
		this.setUniform(this.uNoiseDropoff, Config.Client.Advanced.Graphics.NoiseTexture.noiseDropoff.get());
	}
	
	
	
	//=========//
	// methods //
	//=========//
	
	@Override
	public void bind()
	{
		super.bind();
		this.vao.bind();
	}
	@Override
	public void unbind()
	{
		super.unbind();
		this.vao.unbind();
	}
	
	@Override
	public void free()
	{
		this.vao.free();
		super.free();
	}
	
	@Override
	public void bindVertexBuffer(int vbo) { this.vao.bindBufferToAllBindingPoints(vbo); }
	
	@Override
	public void fillUniformData(DhApiRenderParam renderParameters)
	{
		Mat4f combinedMatrix = new Mat4f(renderParameters.dhProjectionMatrix);
		combinedMatrix.multiply(renderParameters.dhModelViewMatrix);
		
		super.bind();

		// uniforms
		this.setUniform(this.uCombinedMatrix, combinedMatrix);
		this.setUniform(this.uMircoOffset, 0.01f); // 0.01 block offset
		
		// setUniform(skyLightUniform, skyLight);
		this.setUniform(this.uLightMap, 0); // TODO this should probably be passed in
		
		if (this.uWorldYOffset != -1) this.setUniform(this.uWorldYOffset, (float) renderParameters.worldYOffset);
		
		if (this.uDitherDhRendering != -1) this.setUniform(this.uDitherDhRendering, Config.Client.Advanced.Graphics.Quality.ditherDhFade.get());
		
		// Debug
		this.setUniform(this.uWhiteWorld, Config.Client.Advanced.Debugging.enableWhiteWorld.get());
		
		// Clip Uniform
		float dhNearClipDistance = RenderUtil.getNearClipPlaneInBlocksForFading(renderParameters.partialTicks);
		if (!Config.Client.Advanced.Debugging.lodOnlyMode.get())
		{
			// this added value prevents the near clip plane and discard circle from touching, which looks bad
			dhNearClipDistance += 16f;
		}
		// if the player is very high up and the near clip plane has been modified, disable the distance clipping
		// we're high enough that nothing will render on top of the player and this can cause issues otherwise
		if (RenderUtil.getHeightBasedNearClipOverride() != -1)
		{
			dhNearClipDistance = 1.0f;
		}
		this.setUniform(this.uClipDistance, dhNearClipDistance);
	}
	
	@Override
	public void setModelOffsetPos(DhApiVec3f modelOffsetPos) { this.setUniform(this.uModelOffset, new Vec3f(modelOffsetPos)); }
	
	@Override
	public int getId() { return this.id; }
	
	/** The base DH render program should always render */
	@Override
	public boolean overrideThisFrame() { return true; }
	
}
