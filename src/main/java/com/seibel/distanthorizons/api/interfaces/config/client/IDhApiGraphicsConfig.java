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

package com.seibel.distanthorizons.api.interfaces.config.client;

import com.seibel.distanthorizons.api.enums.config.*;
import com.seibel.distanthorizons.api.enums.rendering.EDhApiRendererMode;
import com.seibel.distanthorizons.api.enums.rendering.EDhApiTransparency;
import com.seibel.distanthorizons.api.interfaces.config.IDhApiConfigValue;
import com.seibel.distanthorizons.api.interfaces.config.IDhApiConfigGroup;

/**
 * Distant Horizons' graphics/rendering configuration.
 *
 * @author James Seibel
 * @version 2023-6-14
 * @since API 1.0.0
 */
public interface IDhApiGraphicsConfig extends IDhApiConfigGroup
{
	//===============//
	// inner configs //
	//===============//
	
	IDhApiFogConfig fog();
	IDhApiAmbientOcclusionConfig ambientOcclusion();
	IDhApiNoiseTextureConfig noiseTexture();
	IDhApiGenericRenderingConfig genericRendering();
	
	
	
	//========================//
	// basic graphic settings //
	//========================//
	
	/** The distance is the radius measured in chunks. */
	IDhApiConfigValue<Integer> chunkRenderDistance();
	
	/**
	 * Simplified version of {@link IDhApiGraphicsConfig#renderingMode()}
	 * that only enables/disables the fake chunk rendering. <br><br>
	 *
	 * Changing this config also changes {@link IDhApiGraphicsConfig#renderingMode()}'s value.
	 */
	IDhApiConfigValue<Boolean> renderingEnabled();
	
	/**
	 * Can be used to enable/disable fake chunk rendering or enable the debug renderer. <br><br>
	 *
	 * The debug renderer is used to confirm rendering is working at and will draw
	 * a single multicolor rhombus on the screen in skybox space (AKA behind MC's rendering). <br><br>
	 *
	 * Changing this config also changes {@link IDhApiGraphicsConfig#renderingEnabled()}'s value.
	 */
	IDhApiConfigValue<EDhApiRendererMode> renderingMode();
	
	
	
	//==================//
	// graphic settings //
	//==================//
	
	/** Defines how detailed fake chunks are in the horizontal direction */
	IDhApiConfigValue<EDhApiMaxHorizontalResolution> maxHorizontalResolution();
	
	/** Defines how detailed fake chunks are in the vertical direction */
	IDhApiConfigValue<EDhApiVerticalQuality> verticalQuality();
	
	/** Modifies the quadratic function fake chunks use for horizontal quality drop-off. */
	IDhApiConfigValue<EDhApiHorizontalQuality> horizontalQuality();
	
	IDhApiConfigValue<EDhApiTransparency> transparency();
	
	/** Defines what blocks won't be rendered as LODs. */
	IDhApiConfigValue<EDhApiBlocksToAvoid> blocksToAvoid();
	
	/**
	 * Defines if the color of avoided blocks will color the block below them. <Br>
	 * (IE: if flowers are avoided, should they color the grass below them?)
	 */
	IDhApiConfigValue<Boolean> tintWithAvoidedBlocks();
	
	/*
	 * The same as vanilla Minecraft's biome blending. <br><br>
	 *
	 * 0 = blending of 1x1 aka off	<br>
	 * 1 = blending of 3x3			<br>
	 * 2 = blending of 5x5			<br>
	 * ...							<br>
	 */
//	IDhApiConfigValue<Integer> getBiomeBlending();
	
	
	
	//===========================//
	// advanced graphic settings //
	//===========================//
	
	/**
	 * Sets the radius used by the near clip shader to reduce
	 * overdraw. <br>
	 * Measured in percentages of the render distance, IE: <br>
	 * 0.5 = 50% vanilla render distance <br>
	 * 0.1 = 10% vanilla render distance <br>
	 * <br>
	 * Setting this to 0 will reduce/prevent holes in the world due to clipping to close to the camera
	 * but may cause overdraw issues with transparent or non-full blocks.
	 * 
	 * @since API 2.0.0
	 */
	IDhApiConfigValue<Double> overdrawPreventionRadius();
	
	/**
	 * Modifies how bright fake chunks are. <br>
	 * This is done when generating the vertex data and is applied before any shaders.
	 */
	IDhApiConfigValue<Double> brightnessMultiplier();
	
	/**
	 * Modifies how saturated fake chunks are. <br>
	 * This is done when generating the vertex data and is applied before any shaders.
	 */
	IDhApiConfigValue<Double> saturationMultiplier();
	
	/** Defines if Distant Horizons should attempt to cull fake chunk cave geometry. */
	IDhApiConfigValue<Boolean> caveCullingEnabled();
	
	/** Defines what height cave culling should be used below if enabled. */
	IDhApiConfigValue<Integer> caveCullingHeight();
	
	/** This ratio is relative to Earth's real world curvature. */
	IDhApiConfigValue<Integer> earthCurvatureRatio();
	
	/** If enabled vanilla chunk rendering is disabled and only fake chunks are rendered. */
	IDhApiConfigValue<Boolean> lodOnlyMode();
	
	/**
	 * Setting this to a non-zero number will modify vanilla Minecraft's LOD Bias,
	 * increasing how quickly its textures fade away.
	 */
	IDhApiConfigValue<Double> lodBias();
	
	/**
	 * Determines how LODs should be shaded.
	 *
	 * @since API 2.0.0
	 */
	IDhApiConfigValue<EDhApiLodShading> lodShading();
	
	/**
	 * Sets whether LODs outside the view frustum culling will
	 * be culled. <br><br>
	 *
	 * Disabling this will prevent LODs not rendering on the corner
	 * of the users vision and may fix issues if LODs appear to
	 * start/stop rendering incorrectly based on the camera direction,
	 * but will also reduce FPS.
	 *
	 * @since API 2.0.0
	 * @see IDhApiGraphicsConfig#disableShadowFrustumCulling()
	 */
	IDhApiConfigValue<Boolean> disableFrustumCulling();
	
	/**
	 * Identical to the other frustum culling option, except that it is
	 * only used when a shader mod is present using the DH API
	 * and the shadow pass is being rendered. <br><br>
	 * 
	 * Disable this if shadows render incorrectly.
	 *
	 * @since API 2.0.0
	 * @see IDhApiGraphicsConfig#disableFrustumCulling() 
	 */
	IDhApiConfigValue<Boolean> disableShadowFrustumCulling();
	
}
