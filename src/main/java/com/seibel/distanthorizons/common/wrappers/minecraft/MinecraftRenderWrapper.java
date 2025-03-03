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

package com.seibel.distanthorizons.common.wrappers.minecraft;

import java.awt.Color;
import java.lang.invoke.MethodHandles;
import java.util.concurrent.ConcurrentHashMap;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.platform.NativeImage;
import com.seibel.distanthorizons.common.wrappers.WrapperFactory;
import com.seibel.distanthorizons.common.wrappers.misc.LightMapWrapper;
import com.seibel.distanthorizons.core.dependencyInjection.ModAccessorInjector;

import com.seibel.distanthorizons.core.logging.DhLoggerBuilder;
import com.seibel.distanthorizons.core.util.ColorUtil;
import com.seibel.distanthorizons.core.wrapperInterfaces.misc.ILightMapWrapper;

#if MC_VER >= MC_1_17_1
import net.minecraft.client.renderer.FogRenderer;
import com.mojang.blaze3d.systems.RenderSystem;
#endif

#if MC_VER < MC_1_19_4
#else
#endif
#if MC_VER >= MC_1_20_2
#endif

import com.seibel.distanthorizons.core.wrapperInterfaces.modAccessor.AbstractOptifineAccessor;
import com.seibel.distanthorizons.core.wrapperInterfaces.world.IClientLevelWrapper;
import com.seibel.distanthorizons.core.wrapperInterfaces.world.IDimensionTypeWrapper;
import com.seibel.distanthorizons.core.wrapperInterfaces.world.ILevelWrapper;
import com.seibel.distanthorizons.core.util.math.Vec3d;
import com.seibel.distanthorizons.core.util.math.Vec3f;
import com.seibel.distanthorizons.core.wrapperInterfaces.IWrapperFactory;
import com.seibel.distanthorizons.core.wrapperInterfaces.minecraft.IMinecraftRenderWrapper;
import com.seibel.distanthorizons.core.wrapperInterfaces.modAccessor.IOptifineAccessor;

import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.world.effect.MobEffects;
#if MC_VER < MC_1_17_1
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.material.FluidState;
import org.lwjgl.opengl.GL15;
#else
import net.minecraft.world.level.material.FogType;
#endif
import net.minecraft.world.phys.Vec3;
import org.apache.logging.log4j.Logger;
import org.joml.Vector4f;


/**
 * A singleton that contains everything
 * related to rendering in Minecraft.
 *
 * @author James Seibel
 * @version 12-12-2021
 */
//@Environment(EnvType.CLIENT)
public class MinecraftRenderWrapper implements IMinecraftRenderWrapper
{
	public static final MinecraftRenderWrapper INSTANCE = new MinecraftRenderWrapper();

	private static final Logger LOGGER = DhLoggerBuilder.getLogger(MethodHandles.lookup().lookupClass().getSimpleName());
	private static final Minecraft MC = Minecraft.getInstance();
	private static final IWrapperFactory FACTORY = WrapperFactory.INSTANCE;

	private static final IOptifineAccessor OPTIFINE_ACCESSOR = ModAccessorInjector.INSTANCE.get(IOptifineAccessor.class);

	/**
	 * In the case of immersive portals multiple levels may be active at once, causing conflicting lightmaps. <br>
	 * Requiring the use of multiple {@link LightMapWrapper}.
	 */
	public ConcurrentHashMap<IDimensionTypeWrapper, LightMapWrapper> lightmapByDimensionType = new ConcurrentHashMap<>();

	/**
	 * Holds the render buffer that should be used when displaying levels to the screen.
	 * This is used for Optifine shader support so we can render directly to Optifine's level frame buffer.
	 */
	public int finalLevelFrameBufferId = -1;



	@Override
	public Vec3f getLookAtVector()
	{
		Camera camera = MC.gameRenderer.getMainCamera();
		return new Vec3f(camera.getLookVector().x(), camera.getLookVector().y(), camera.getLookVector().z());
	}

	@Override
	/** Unless you really need to know if the player is blind, use {@link MinecraftRenderWrapper#isFogStateSpecial()}/{@link IMinecraftRenderWrapper#isFogStateSpecial()} instead */
	public boolean playerHasBlindingEffect()
	{
		return MC.player.getActiveEffectsMap().get(MobEffects.BLINDNESS) != null
				#if MC_VER >= MC_1_19_2
				|| MC.player.getActiveEffectsMap().get(MobEffects.DARKNESS) != null // Deep dark effect
				#endif
				;
	}

	@Override
	public Vec3d getCameraExactPosition()
	{
		Camera camera = MC.gameRenderer.getMainCamera();
		Vec3 projectedView = camera.getPosition();

		return new Vec3d(projectedView.x, projectedView.y, projectedView.z);
	}

	@Override
	public Color getFogColor(float partialTicks)
	{
		#if MC_VER < MC_1_17_1
		float[] colorValues = new float[4];
		GL15.glGetFloatv(GL15.GL_FOG_COLOR, colorValues);
		return new Color(
				Math.max(0f, Math.min(colorValues[0], 1f)), // r
				Math.max(0f, Math.min(colorValues[1], 1f)), // g
				Math.max(0f, Math.min(colorValues[2], 1f)), // b
				Math.max(0f, Math.min(colorValues[3], 1f))  // a
		);
		#elif MC_VER < MC_1_21_3
		FogRenderer.setupColor(MC.gameRenderer.getMainCamera(), partialTicks, MC.level, 1, MC.gameRenderer.getDarkenWorldAmount(partialTicks));
		float[] colorValues = RenderSystem.getShaderFogColor();
		return new Color(
				Math.max(0f, Math.min(colorValues[0], 1f)), // r
				Math.max(0f, Math.min(colorValues[1], 1f)), // g
				Math.max(0f, Math.min(colorValues[2], 1f)), // b
				Math.max(0f, Math.min(colorValues[3], 1f))  // a
		);
		#else
		Vector4f colorValues = FogRenderer.computeFogColor(MC.gameRenderer.getMainCamera(), partialTicks, MC.level, 1, MC.gameRenderer.getDarkenWorldAmount(partialTicks));
		return new Color(
				Math.max(0f, Math.min(colorValues.x, 1f)), // r
				Math.max(0f, Math.min(colorValues.y, 1f)), // g
				Math.max(0f, Math.min(colorValues.z, 1f)), // b
				Math.max(0f, Math.min(colorValues.w, 1f))  // a
		);
		#endif
	}
	// getSpecialFogColor() is the same as getFogColor()

	@Override
	public Color getSkyColor()
	{
		if (MC.level.dimensionType().hasSkyLight())
		{
			float frameTime;
			#if MC_VER < MC_1_21_1
			frameTime = MC.getFrameTime();
			#elif MC_VER < MC_1_21_3
			frameTime = MC.getTimer().getRealtimeDeltaTicks();
			#else
			frameTime = MC.deltaTracker.getGameTimeDeltaTicks();
			#endif

			#if MC_VER < MC_1_17_1
			Vec3 colorValues = MC.level.getSkyColor(MC.gameRenderer.getMainCamera().getBlockPosition(), frameTime);
			return new Color((float) colorValues.x, (float) colorValues.y, (float) colorValues.z);
			#elif MC_VER < MC_1_21_3
			Vec3 colorValues = MC.level.getSkyColor(MC.gameRenderer.getMainCamera().getPosition(), frameTime);
			return new Color((float) colorValues.x, (float) colorValues.y, (float) colorValues.z);
			#else
			int argbColorInt = MC.level.getSkyColor(MC.gameRenderer.getMainCamera().getPosition(), frameTime);;
			return ColorUtil.toColorObjARGB(argbColorInt); // TODO MC changed color formats
			#endif
		}
		else
		{
			return new Color(0, 0, 0);
		}
	}

	@Override
	public double getFov(float partialTicks)
	{
		return MC.gameRenderer.getFov(MC.gameRenderer.getMainCamera(), partialTicks, true);
	}

	/** Measured in chunks */
	@Override
	public int getRenderDistance()
	{
		#if MC_VER < MC_1_18_2
		//FIXME: How to resolve this?
		return MC.options.renderDistance;
		#else
		return MC.options.getEffectiveRenderDistance();
		#endif
	}

	@Override
	public int getScreenWidth()
	{
		// alternate ways of getting the window's resolution,
		// using one of these methods may fix the optifine render resolution bug
		// TODO: test these once we can run with Optifine again
//		int[] heightArray = new int[1];
//		int[] widthArray = new int[1];
//
//		long window = GLProxy.getInstance().minecraftGlContext;
//		GLFW.glfwGetWindowSize(window, widthArray, heightArray); // option 1
//		GLFW.glfwGetFramebufferSize(window, widthArray, heightArray); // option 2



		int width = MC.getWindow().getWidth();
		if (OPTIFINE_ACCESSOR != null)
		{
			// TODO remove comment after testing:
			// this should fix the issue where different optifine render resolutions screw up the LOD rendering
			width *= OPTIFINE_ACCESSOR.getRenderResolutionMultiplier();
		}
		return width;
	}
	@Override
	public int getScreenHeight()
	{
		int height = MC.getWindow().getHeight();
		if (OPTIFINE_ACCESSOR != null)
		{
			height *= OPTIFINE_ACCESSOR.getRenderResolutionMultiplier();
		}
		return height;
	}

	private RenderTarget getRenderTarget() { return MC.getMainRenderTarget(); }

	@Override
	public int getTargetFrameBuffer()
	{
		// used so we can access the framebuffer shaders end up rendering to
		if (AbstractOptifineAccessor.optifinePresent())
		{
			return this.finalLevelFrameBufferId;
		}

		return this.getRenderTarget().frameBufferId;
	}

	@Override
	public void clearTargetFrameBuffer() { this.finalLevelFrameBufferId = -1; }

	@Override
	public int getDepthTextureId() { return this.getRenderTarget().getDepthTextureId(); }
	@Override
	public int getColorTextureId() { return this.getRenderTarget().getColorTextureId(); }

	@Override
	public int getTargetFrameBufferViewportWidth()
	{
		return getRenderTarget().viewWidth;
	}

	@Override
	public int getTargetFrameBufferViewportHeight()
	{
		return getRenderTarget().viewHeight;
	}

	@Override
	public ILightMapWrapper getLightmapWrapper(ILevelWrapper level) { return this.lightmapByDimensionType.get(level.getDimensionType()); }

	@Override
	public boolean isFogStateSpecial()
	{
		#if MC_VER < MC_1_17_1
		Camera camera = Minecraft.getInstance().gameRenderer.getMainCamera();
		FluidState fluidState = camera.getFluidInCamera();
		Entity entity = camera.getEntity();
		boolean isBlind = this.playerHasBlindingEffect();
			isBlind |= fluidState.is(FluidTags.WATER);
			isBlind |= fluidState.is(FluidTags.LAVA);
		return isBlind;
		#else
		boolean isBlind = this.playerHasBlindingEffect();
		return MC.gameRenderer.getMainCamera().getFluidInCamera() != FogType.NONE || isBlind;
		#endif
	}

    public void setLightmapId(int tetxureId, IClientLevelWrapper level)
	{
		// Using ClientLevelWrapper as the key would be better, but we don't have a consistent way to create the same
		// object for the same MC level and/or the same hash,
		// so this will have to do for now
		IDimensionTypeWrapper dimensionType = level.getDimensionType();

		LightMapWrapper wrapper = this.lightmapByDimensionType.computeIfAbsent(dimensionType, (dimType) -> new LightMapWrapper());
		wrapper.setLightmapId(tetxureId);
	}

}
