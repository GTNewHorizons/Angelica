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

package com.seibel.distanthorizons.core.config;

import com.seibel.distanthorizons.api.DhApi;
import com.seibel.distanthorizons.api.enums.config.*;
import com.seibel.distanthorizons.api.enums.config.quickOptions.*;
import com.seibel.distanthorizons.api.enums.rendering.*;
import com.seibel.distanthorizons.api.enums.worldGeneration.EDhApiDistantGeneratorMode;
import com.seibel.distanthorizons.api.enums.worldGeneration.EDhApiDistantGeneratorProgressDisplayLocation;
import com.seibel.distanthorizons.core.config.eventHandlers.*;
import com.seibel.distanthorizons.core.config.eventHandlers.presets.*;
import com.seibel.distanthorizons.core.config.listeners.ConfigChangeListener;
import com.seibel.distanthorizons.core.config.types.*;
import com.seibel.distanthorizons.core.config.types.enums.*;
import com.seibel.distanthorizons.core.dependencyInjection.SingletonInjector;
import com.seibel.distanthorizons.core.logging.DhLoggerBuilder;
import com.seibel.distanthorizons.core.pos.DhSectionPos;
import com.seibel.distanthorizons.core.wrapperInterfaces.IWrapperFactory;
import com.seibel.distanthorizons.core.wrapperInterfaces.minecraft.IMinecraftSharedWrapper;
import com.seibel.distanthorizons.coreapi.ModInfo;
import org.apache.logging.log4j.Logger;
import org.lwjgl.util.tinyfd.TinyFileDialogs;

import java.awt.*;
import java.io.File;
import java.util.*;
import java.util.List;

/**
 * This handles any configuration the user has access to. <br><br>
 *
 * Note: <br>
 * Only add simpler listeners here (IE listeners that only depend on 1 config entry).
 * For listeners that depend on 2 or more config entries, add them before the config menu is opened.
 * Otherwise, you will have issues where only some of the config entries will exist when your listener is created.
 *
 * @author coolGi
 */
@SuppressWarnings("ConcatenationWithEmptyString")
public class Config
{
	private static final Logger LOGGER = DhLoggerBuilder.getLogger();
	
	public static ConfigCategory client = new ConfigCategory.Builder().set(Client.class).build();
	
	
	
	public static class Client
	{
		public static ConfigEntry<Boolean> quickEnableRendering = new ConfigEntry.Builder<Boolean>()
				.set(true)
				.comment(""
						+ "If true, Distant Horizons will render LODs beyond the vanilla render distance."
						+ "")
				.setAppearance(EConfigEntryAppearance.ONLY_IN_GUI)
				.build();
		
		public static ConfigUiLinkedEntry quickLodChunkRenderDistance = new ConfigUiLinkedEntry(Client.Advanced.Graphics.Quality.lodChunkRenderDistanceRadius);
		
		public static ConfigEntry<EDhApiQualityPreset> qualityPresetSetting = new ConfigEntry.Builder<EDhApiQualityPreset>()
				.set(EDhApiQualityPreset.MEDIUM) // the default value is set via the listener when accessed
				.comment(""
						+ "Changing this setting will modify a number of different settings that will change the \n"
						+ "visual fidelity of the rendered LODs.\n"
						+ "\n"
						+ "Higher settings will improve the graphical quality while increasing GPU and memory use.\n"
						+ "")
				.setAppearance(EConfigEntryAppearance.ONLY_IN_GUI)
				.addListener(RenderQualityPresetConfigEventHandler.INSTANCE)
				.build();
		
		public static ConfigEntry<EDhApiThreadPreset> threadPresetSetting = new ConfigEntry.Builder<EDhApiThreadPreset>()
				.setChatCommandName("common.threadPreset")
				.set(EDhApiThreadPreset.BALANCED) // the default value is set via the listener when accessed
				.comment(""
						+ "Changing this setting will modify a number of different settings that will change \n"
						+ "the load that Distant Horizons is allowed to put on your CPU. \n"
						+ "\n"
						+ "Higher options will improve LOD generation and loading speed, \n"
						+ "but will increase CPU load and may introduce stuttering.\n"
						+ "\n"
						+ "Note: This is a CPU relative setting. \n"
						+ "It should put an equal amount of strain on a 2 core CPU as a 64 core CPU.\n"
						+ "")
				.setAppearance(EConfigEntryAppearance.ONLY_IN_GUI)
				.addListener(ThreadPresetConfigEventHandler.INSTANCE)
				.build();
		
		public static ConfigUiLinkedEntry quickEnableWorldGenerator = new ConfigUiLinkedEntry(Common.WorldGenerator.enableDistantGeneration);
		
		public static ConfigEntry<Boolean> quickShowWorldGenProgress = new ConfigEntry.Builder<Boolean>()
				.set(true)
				.setAppearance(EConfigEntryAppearance.ONLY_IN_GUI)
				.build();
		
		public static ConfigUiLinkedEntry quickLodCloudRendering = new ConfigUiLinkedEntry(Advanced.Graphics.GenericRendering.enableCloudRendering);
		
		public static ConfigEntry<Boolean> showDhOptionsButtonInMinecraftUi = new ConfigEntry.Builder<Boolean>()
				.set(true)
				.setAppearance(EConfigEntryAppearance.ONLY_IN_FILE)
				.comment("" +
						"Should Distant Horizon's config button appear in Minecraft's options screen next to the fov slider?")
				.build();
		
		
		public static ConfigCategory advanced = new ConfigCategory.Builder().set(Advanced.class).build();
		
		
		
		public static class Advanced
		{
			// common config links need to have their destination
			// since they aren't part of "client" config class
			// TODO determine their destination programically instead of hard coding the value
			
			public static ConfigCategory graphics = new ConfigCategory.Builder().set(Graphics.class).build();
			public static ConfigCategory worldGenerator = new ConfigCategory.Builder().set(Common.WorldGenerator.class).setDestination("common.worldGenerator").build();
			public static ConfigCategory multiplayer = new ConfigCategory.Builder().set(Multiplayer.class).build();
			public static ConfigCategory server = new ConfigCategory.Builder().set(Server.class).setDestination("server").build();
			public static ConfigCategory lodBuilding = new ConfigCategory.Builder().set(Common.LodBuilding.class).setDestination("common.lodBuilding").build();
			public static ConfigCategory multiThreading = new ConfigCategory.Builder().set(Common.MultiThreading.class).setDestination("common.multiThreading").build();
			public static ConfigCategory autoUpdater = new ConfigCategory.Builder().set(AutoUpdater.class).build();
			
			public static ConfigCategory logging = new ConfigCategory.Builder().set(Common.Logging.class).setDestination("common.logging").build();
			public static ConfigCategory debugging = new ConfigCategory.Builder().set(Debugging.class).build();
			
			
			
			public static class Graphics
			{
				public static ConfigCategory quality = new ConfigCategory.Builder().set(Quality.class).build();
				
				public static ConfigUiLinkedEntry quickEnableSsao = new ConfigUiLinkedEntry(Ssao.enableSsao);
				public static ConfigCategory ssao = new ConfigCategory.Builder().set(Ssao.class).build();
				
				public static ConfigUiLinkedEntry quickEnableGenericRendering = new ConfigUiLinkedEntry(GenericRendering.enableGenericRendering);
				public static ConfigCategory genericRendering = new ConfigCategory.Builder().set(GenericRendering.class).build();
				
				public static ConfigUiLinkedEntry quickEnableDhFog = new ConfigUiLinkedEntry(Fog.enableDhFog);
				public static ConfigUiLinkedEntry quickEnableMcFog = new ConfigUiLinkedEntry(Fog.enableVanillaFog);
				public static ConfigCategory fog = new ConfigCategory.Builder().set(Fog.class).build();
				
				public static ConfigUiLinkedEntry quickEnableNoiseTexture = new ConfigUiLinkedEntry(NoiseTexture.enableNoiseTexture);
				public static ConfigCategory noiseTexture = new ConfigCategory.Builder().set(NoiseTexture.class).build();
				
				public static ConfigUiLinkedEntry quickEnableCaveCulling = new ConfigUiLinkedEntry(Culling.enableCaveCulling);
				public static ConfigCategory culling = new ConfigCategory.Builder().set(Culling.class).build();
				
				public static ConfigCategory experimental = new ConfigCategory.Builder().set(Experimental.class).build();
				
				
				
				public static class Quality
				{
					public static ConfigEntry<Integer> lodChunkRenderDistanceRadius = new ConfigEntry.Builder<Integer>()
							.setMinDefaultMax(32, 256, 4096)
							.comment("" +
									"The radius of the mod's render distance. (measured in chunks)\n" +
									"")
							.setPerformance(EConfigEntryPerformance.HIGH)
							.build();
					
					public static ConfigEntry<EDhApiHorizontalQuality> horizontalQuality = new ConfigEntry.Builder<EDhApiHorizontalQuality>()
							.set(EDhApiHorizontalQuality.MEDIUM)
							.comment(""
									+ "This indicates how quickly LODs decrease in quality the further away they are. \n"
									+ "Higher settings will render higher quality fake chunks farther away, \n"
									+ "but will increase memory and GPU usage.")
							.setPerformance(EConfigEntryPerformance.MEDIUM)
							.build();
					
					public static ConfigEntry<EDhApiMaxHorizontalResolution> maxHorizontalResolution = new ConfigEntry.Builder<EDhApiMaxHorizontalResolution>()
							.set(EDhApiMaxHorizontalResolution.BLOCK)
							.comment(""
									+ "What is the maximum detail LODs should be drawn at? \n"
									+ "Higher settings will increase memory and GPU usage. \n"
									+ "\n"
									+ EDhApiMaxHorizontalResolution.CHUNK + ": render 1 LOD for each Chunk. \n"
									+ EDhApiMaxHorizontalResolution.HALF_CHUNK + ": render 4 LODs for each Chunk. \n"
									+ EDhApiMaxHorizontalResolution.FOUR_BLOCKS + ": render 16 LODs for each Chunk. \n"
									+ EDhApiMaxHorizontalResolution.TWO_BLOCKS + ": render 64 LODs for each Chunk. \n"
									+ EDhApiMaxHorizontalResolution.BLOCK + ": render 256 LODs for each Chunk (width of one block). \n"
									+ "\n"
									+ "Lowest Quality: " + EDhApiMaxHorizontalResolution.CHUNK + "\n"
									+ "Highest Quality: " + EDhApiMaxHorizontalResolution.BLOCK)
							.setPerformance(EConfigEntryPerformance.MEDIUM)
							.build();
					
					public static ConfigEntry<EDhApiVerticalQuality> verticalQuality = new ConfigEntry.Builder<EDhApiVerticalQuality>()
							.set(EDhApiVerticalQuality.MEDIUM)
							.comment(""
									+ "This indicates how well LODs will represent \n"
									+ "overhangs, caves, floating islands, etc. \n"
									+ "Higher options will make the world more accurate, but"
									+ "will increase memory and GPU usage. \n"
									+ "\n"
									+ "Lowest Quality: " + EDhApiVerticalQuality.HEIGHT_MAP + "\n"
									+ "Highest Quality: " + EDhApiVerticalQuality.EXTREME)
							.setPerformance(EConfigEntryPerformance.VERY_HIGH)
							.addListener(ReloadLodsConfigEventHandler.INSTANCE)
							.build();
					
					public static ConfigEntry<EDhApiTransparency> transparency = new ConfigEntry.Builder<EDhApiTransparency>()
							.set(EDhApiTransparency.COMPLETE)
							.comment(""
									+ "How should LOD transparency be handled. \n"
									+ "\n"
									+ EDhApiTransparency.COMPLETE + ": LODs will render transparent. \n"
									+ EDhApiTransparency.FAKE + ": LODs will be opaque, but shaded to match the blocks underneath. \n"
									+ EDhApiTransparency.DISABLED + ": LODs will be opaque. \n"
									+ "")
							.setPerformance(EConfigEntryPerformance.MEDIUM)
							.addListener(ReloadLodsConfigEventHandler.INSTANCE)
							.build();
					
					public static ConfigEntry<EDhApiBlocksToAvoid> blocksToIgnore = new ConfigEntry.Builder<EDhApiBlocksToAvoid>()
							.set(EDhApiBlocksToAvoid.NON_COLLIDING)
							.comment(""
									+ "What blocks shouldn't be rendered as LODs? \n"
									+ "\n"
									+ EDhApiBlocksToAvoid.NONE + ": Represent all blocks in the LODs \n"
									+ EDhApiBlocksToAvoid.NON_COLLIDING + ": Only represent solid blocks in the LODs (tall grass, torches, etc. won't count for a LOD's height) \n"
									+ "")
							.setPerformance(EConfigEntryPerformance.NONE)
							.addListener(ReloadLodsConfigEventHandler.INSTANCE)
							.build();
					
					public static ConfigEntry<Boolean> tintWithAvoidedBlocks = new ConfigEntry.Builder<Boolean>()
							.set(true)
							.comment(""
									+ "Should the blocks underneath avoided blocks gain the color of the avoided block? \n"
									+ "\n"
									+ "True: a red flower will tint the grass below it red. \n"
									+ "False: skipped blocks will not change color of surface below them. "
									+ "")
							.setPerformance(EConfigEntryPerformance.NONE)
							.addListener(ReloadLodsConfigEventHandler.INSTANCE)
							.build();
					
					public static ConfigEntry<Double> lodBias = new ConfigEntry.Builder<Double>()
							.setMinDefaultMax(0d, 0d, null)
							.comment(""
									+ "What the value should vanilla Minecraft's texture LodBias be? \n"
									+ "If set to 0 the mod wont overwrite vanilla's default (which so happens to also be 0)")
							.build();
					
					public static ConfigEntry<EDhApiLodShading> lodShading = new ConfigEntry.Builder<EDhApiLodShading>()
							.set(EDhApiLodShading.AUTO)
							.comment(""
									+ "How should LODs be shaded? \n"
									+ "\n"
									+ EDhApiLodShading.AUTO + ": Uses the same side shading as vanilla Minecraft blocks. \n"
									+ EDhApiLodShading.ENABLED + ": Simulates Minecraft's block shading for LODs. \n"
									+ "              Can be used to force LOD shading when using some shaders. \n"
									+ EDhApiLodShading.DISABLED + ": All LOD sides will be rendered with the same brightness. \n"
									+ "")
							.setPerformance(EConfigEntryPerformance.NONE)
							.addListener(ReloadLodsConfigEventHandler.INSTANCE)
							.build();
					
					public static ConfigEntry<EDhApiGrassSideRendering> grassSideRendering = new ConfigEntry.Builder<EDhApiGrassSideRendering>()
							.set(EDhApiGrassSideRendering.FADE_TO_DIRT)
							.comment(""
									+ "How should the sides and bottom of grass block LODs render? \n"
									+ "\n"
									+ EDhApiGrassSideRendering.AS_GRASS + ": all sides of dirt LOD's render using the top (green) color. \n"
									+ EDhApiGrassSideRendering.FADE_TO_DIRT + ": sides fade from grass to dirt. \n"
									+ EDhApiGrassSideRendering.AS_DIRT + ": sides render entirely as dirt. \n"
									+ "")
							.setPerformance(EConfigEntryPerformance.NONE)
							.addListener(ReloadLodsConfigEventHandler.INSTANCE)
							.build();
					
					public static ConfigEntry<Boolean> ditherDhFade = new ConfigEntry.Builder<Boolean>()
							.set(true)
							.comment(""
									+ "If true LODs will fade away as you get closer to them. \n"
									+ "If false LODs will cut off abruptly at a set distance from the camera. \n"
									+ "This setting is affected by the vanilla overdraw prevention config. \n"
									+ "")
							.setPerformance(EConfigEntryPerformance.LOW)
							.build();
					
					public static ConfigEntry<EDhApiMcRenderingFadeMode> vanillaFadeMode = new ConfigEntry.Builder<EDhApiMcRenderingFadeMode>()
							.set(EDhApiMcRenderingFadeMode.DOUBLE_PASS)
							.comment(""
									+ "How should vanilla Minecraft fade into Distant Horizons LODs? \n"
									+ "\n"
									+ EDhApiMcRenderingFadeMode.NONE + ": Fastest, there will be a pronounced border between DH and MC rendering. \n"
									+ EDhApiMcRenderingFadeMode.SINGLE_PASS + ": Fades after MC's transparent pass, opaque blocks underwater won't be faded. \n"
									+ EDhApiMcRenderingFadeMode.DOUBLE_PASS + ": Slowest, fades after both MC's opaque and transparent passes, provides the smoothest transition. \n"
									+ "")
							.setPerformance(EConfigEntryPerformance.LOW)
							.build();
					
					public static ConfigEntry<Double> brightnessMultiplier = new ConfigEntry.Builder<Double>() // TODO: Make this a float (the ClassicConfigGUI doesnt support floats)
							.set(1.0)
							.comment(""
									+ "How bright LOD colors are. \n"
									+ "\n"
									+ "0 = black \n"
									+ "1 = normal \n"
									+ "2 = near white")
							.addListener(ReloadLodsConfigEventHandler.INSTANCE)
							.build();
					
					public static ConfigEntry<Double> saturationMultiplier = new ConfigEntry.Builder<Double>() // TODO: Make this a float (the ClassicConfigGUI doesnt support floats)
							.set(1.0)
							.comment(""
									+ "How saturated LOD colors are. \n"
									+ "\n"
									+ "0 = black and white \n"
									+ "1 = normal \n"
									+ "2 = very saturated")
							.addListener(ReloadLodsConfigEventHandler.INSTANCE)
							.build();
					
					// TODO fixme
					//public static ConfigEntry<Integer> lodBiomeBlending = new ConfigEntry.Builder<Integer>()
					//		.setMinDefaultMax(0,1,7)
					//		.comment(""
					//				+ "This is the same as vanilla Biome Blending settings for Lod area. \n"
					//				+ "    Note that anything other than '0' will greatly effect Lod building time \n"
					//				+ "     and increase triangle count. The cost on chunk generation speed is also \n"
					//				+ "     quite large if set too high.\n"
					//				+ "\n"
					//				+ "    '0' equals to Vanilla Biome Blending of '1x1' or 'OFF', \n"
					//				+ "    '1' equals to Vanilla Biome Blending of '3x3', \n"
					//				+ "    '2' equals to Vanilla Biome Blending of '5x5'...")
					//		.build();	
				}
				
				public static class Ssao
				{
					public static ConfigEntry<Boolean> enableSsao = new ConfigEntry.Builder<Boolean>()
							.set(true)
							.comment("Enable Screen Space Ambient Occlusion")
							.setPerformance(EConfigEntryPerformance.MEDIUM)
							.build();
					
					public static ConfigEntry<Integer> sampleCount = new ConfigEntry.Builder<Integer>()
							.set(6)
							.comment("" +
									"Determines how many points in space are sampled for the occlusion test. \n" +
									"Higher numbers will improve quality and reduce banding, but will increase GPU load." +
									"")
							.setPerformance(EConfigEntryPerformance.MEDIUM)
							.build();
					
					public static ConfigEntry<Double> radius = new ConfigEntry.Builder<Double>()
							.set(4.0)
							.comment("" +
									"Determines the radius Screen Space Ambient Occlusion is applied, measured in blocks." +
									"")
							.setPerformance(EConfigEntryPerformance.NONE)
							.build();
					
					public static ConfigEntry<Double> strength = new ConfigEntry.Builder<Double>()
							.set(0.2)
							.comment("" +
									"Determines how dark the Screen Space Ambient Occlusion effect will be." +
									"")
							.setPerformance(EConfigEntryPerformance.NONE)
							.build();
					
					public static ConfigEntry<Double> bias = new ConfigEntry.Builder<Double>()
							.set(0.02)
							.comment("" +
									"Increasing the value can reduce banding at the cost of reducing the strength of the effect." +
									"")
							.setPerformance(EConfigEntryPerformance.NONE)
							.build();
					
					public static ConfigEntry<Double> minLight = new ConfigEntry.Builder<Double>()
							.set(0.25)
							.comment("" +
									"Determines how dark the occlusion shadows can be. \n" +
									"0 = totally black at the corners \n" +
									"1 = no shadow" +
									"")
							.setPerformance(EConfigEntryPerformance.NONE)
							.build();
					
					public static ConfigEntry<Integer> blurRadius = new ConfigEntry.Builder<Integer>()
							.set(2)
							.comment("" +
									"The radius, measured in pixels, that blurring is calculated for the SSAO. \n" +
									"Higher numbers will reduce banding at the cost of GPU performance." +
									"")
							.setPerformance(EConfigEntryPerformance.HIGH)
							.build();
					
				}
				
				public static class GenericRendering
				{
					public static ConfigEntry<Boolean> enableGenericRendering = new ConfigEntry.Builder<Boolean>()
							.set(true)
							.comment(""
									+ "If true non terrain objects will be rendered in DH's terrain. \n"
									+ "This includes beacon beams and clouds. \n"
									+ "")
							.build();
					
					public static ConfigEntry<Boolean> enableBeaconRendering = new ConfigEntry.Builder<Boolean>()
							.set(true)
							.comment(""
									+ "If true LOD beacon beams will be rendered. \n"
									+ "")
							.build();
					
					public static ConfigEntry<Integer> beaconRenderHeight = new ConfigEntry.Builder<Integer>()
							.setMinDefaultMax(1, 6000, 6_000_000)
							.comment(""
									+ "Sets the maximum height at which beacons will render."
									+ "This will only affect new beacons coming into LOD render distance."
									+ "Beacons currently visible in LOD chunks will not be affected."
									+ "")
							.build();
					
					public static ConfigEntry<Boolean> enableCloudRendering = new ConfigEntry.Builder<Boolean>()
							.set(true)
							.comment(""
									+ "If true LOD clouds will be rendered. \n"
									+ "")
							.build();
					
					public static ConfigEntry<Boolean> enableInstancedRendering = new ConfigEntry.Builder<Boolean>()
							.set(true)
							.comment(""
									+ "Can be disabled to use much slower but more compatible direct rendering. \n"
									+ "Disabling this can be used to fix some crashes on Mac. \n"
									+ "")
							.build();
				}
				
				public static class Fog
				{
					private static final Double FOG_RANGE_MIN = 0.0;
					private static final Double FOG_RANGE_MAX = Math.sqrt(2.0);
					
					
					
					public static ConfigEntry<Boolean> enableDhFog = new ConfigEntry.Builder<Boolean>()
							.set(true)
							.comment(""
									+ "Determines if fog is drawn on DH LODs. \n"
									+ "")
							.setPerformance(EConfigEntryPerformance.MEDIUM)
							.build();
					
					public static ConfigEntry<EDhApiFogColorMode> colorMode = new ConfigEntry.Builder<EDhApiFogColorMode>()
							.set(EDhApiFogColorMode.USE_WORLD_FOG_COLOR)
							.comment(""
									+ "What color should fog use? \n"
									+ "\n"
									+ EDhApiFogColorMode.USE_WORLD_FOG_COLOR + ": Use the world's fog color. \n"
									+ EDhApiFogColorMode.USE_SKY_COLOR + ": Use the sky's color.")
							.setPerformance(EConfigEntryPerformance.NONE)
							.build();
					
					public static ConfigEntry<Boolean> enableVanillaFog = new ConfigEntry.Builder<Boolean>()
							.set(false)
							.comment(""
									+ "Should Minecraft's fog render? \n"
									+ "Note: Other mods may conflict with this setting. \n"
									+ "")
							.build();
					@Deprecated
					public static ConfigEntry<Boolean> disableVanillaFog = new ConfigEntry.Builder<Boolean>()
							.set(!enableVanillaFog.get())
							.setAppearance(EConfigEntryAppearance.ONLY_IN_API)
							.build();
					
					
					
					public static ConfigEntry<Double> farFogStart = new ConfigEntry.Builder<Double>()
							.setMinDefaultMax(FOG_RANGE_MIN, 0.4, FOG_RANGE_MAX)
							.comment(""
									+ "At what distance should the far fog start? \n"
									+ "\n"
									+ "0.0: Fog starts at the player's position. \n"
									+ "1.0: Fog starts at the closest edge of the vanilla render distance. \n"
									+ "1.414: Fog starts at the corner of the vanilla render distance.")
							.build();
					
					public static ConfigEntry<Double> farFogEnd = new ConfigEntry.Builder<Double>()
							.setMinDefaultMax(FOG_RANGE_MIN, 1.0, FOG_RANGE_MAX)
							.comment(""
									+ "Where should the far fog end? \n"
									+ "\n"
									+ "0.0: Fog ends at player's position.\n"
									+ "1.0: Fog ends at the closest edge of the vanilla render distance. \n"
									+ "1.414: Fog ends at the corner of the vanilla render distance.")
							.build();
					
					public static ConfigEntry<Double> farFogMin = new ConfigEntry.Builder<Double>()
							.setMinDefaultMax(-5.0, 0.0, FOG_RANGE_MAX)
							.comment(""
									+ "What is the minimum fog thickness? \n"
									+ "\n"
									+ "0.0: No fog. \n"
									+ "1.0: Fully opaque fog.")
							.build();
					
					public static ConfigEntry<Double> farFogMax = new ConfigEntry.Builder<Double>()
							.setMinDefaultMax(FOG_RANGE_MIN, 1.0, 5.0)
							.comment(""
									+ "What is the maximum fog thickness? \n"
									+ "\n"
									+ "0.0: No fog. \n"
									+ "1.0: Fully opaque fog.")
							.build();
					
					public static ConfigEntry<EDhApiFogFalloff> farFogFalloff = new ConfigEntry.Builder<EDhApiFogFalloff>()
							.set(EDhApiFogFalloff.EXPONENTIAL_SQUARED)
							.comment(""
									+ "How should the fog thickness should be calculated? \n"
									+ "\n"
									+ EDhApiFogFalloff.LINEAR + ": Linear based on distance (will ignore 'density')\n"
									+ EDhApiFogFalloff.EXPONENTIAL + ": 1/(e^(distance*density)) \n"
									+ EDhApiFogFalloff.EXPONENTIAL_SQUARED + ": 1/(e^((distance*density)^2)")
							.build();
					
					public static ConfigEntry<Double> farFogDensity = new ConfigEntry.Builder<Double>()
							.setMinDefaultMax(0.01, 2.5, 50.0)
							.comment(""
									+ "Used in conjunction with the Fog Falloff.")
							.build();
					
					public static ConfigCategory heightFog = new ConfigCategory.Builder().set(HeightFog.class).build();
					
					
					
					static
					{
						disableVanillaFog.addListener(
								new ConfigChangeListener<Boolean>(disableVanillaFog,
								(disableVanillaFog) -> enableVanillaFog.setApiValue(disableVanillaFog))
						);
					}
					
					public static class HeightFog
					{
						public static ConfigEntry<EDhApiHeightFogMixMode> heightFogMixMode = new ConfigEntry.Builder<EDhApiHeightFogMixMode>()
								.set(EDhApiHeightFogMixMode.SPHERICAL)
								.comment(""
										+ "How should height effect the fog thickness? \n"
										+ "Note: height fog is combined with the other fog settings. \n"
										+ "\n"
										+ EDhApiHeightFogMixMode.SPHERICAL + ": Fog is calculated based on camera distance. \n"
										+ EDhApiHeightFogMixMode.CYLINDRICAL + ": Ignore height, fog is calculated based on horizontal distance. \n"
										+ "\n"
										+ EDhApiHeightFogMixMode.MAX + ": max(heightFog, farFog) \n"
										+ EDhApiHeightFogMixMode.ADDITION + ": heightFog + farFog \n"
										+ EDhApiHeightFogMixMode.MULTIPLY + ": heightFog * farFog \n"
										+ EDhApiHeightFogMixMode.INVERSE_MULTIPLY + ": 1 - (1-heightFog) * (1-farFog) \n"
										+ EDhApiHeightFogMixMode.LIMITED_ADDITION + ": farFog + max(farFog, heightFog) \n"
										+ EDhApiHeightFogMixMode.MULTIPLY_ADDITION + ": farFog + farFog * heightFog \n"
										+ EDhApiHeightFogMixMode.INVERSE_MULTIPLY_ADDITION + ": farFog + 1 - (1-heightFog) * (1-farFog) \n"
										+ EDhApiHeightFogMixMode.AVERAGE + ": farFog*0.5 + heightFog*0.5 \n"
										+ "\n")
								.build();
						
						public static ConfigEntry<EDhApiHeightFogDirection> heightFogDirection = new ConfigEntry.Builder<EDhApiHeightFogDirection>()
								.set(EDhApiHeightFogDirection.BELOW_SET_HEIGHT)
								.comment(""
										+ "Where should the height fog start? \n"
										+ "\n"
										+ EDhApiHeightFogDirection.ABOVE_CAMERA + ": Height fog starts at the camera and goes towards the sky \n"
										+ EDhApiHeightFogDirection.BELOW_CAMERA + ": Height fog starts at the camera and goes towards the void \n"
										+ EDhApiHeightFogDirection.ABOVE_AND_BELOW_CAMERA + ": Height fog starts from the camera to goes towards both the sky and void \n"
										+ EDhApiHeightFogDirection.ABOVE_SET_HEIGHT + ": Height fog starts from a set height and goes towards the sky \n"
										+ EDhApiHeightFogDirection.BELOW_SET_HEIGHT + ": Height fog starts from a set height and goes towards the void \n"
										+ EDhApiHeightFogDirection.ABOVE_AND_BELOW_SET_HEIGHT + ": Height fog starts from a set height and goes towards both the sky and void")
								.build();
						
						public static ConfigEntry<Double> heightFogBaseHeight = new ConfigEntry.Builder<Double>()
								.setMinDefaultMax(-4096.0, 80.0, 4096.0)
								.comment("If the height fog is calculated around a set height, what is that height position?")
								.build();
						
						public static ConfigEntry<Double> heightFogStart = new ConfigEntry.Builder<Double>()
								.setMinDefaultMax(FOG_RANGE_MIN, 0.0, FOG_RANGE_MAX)
								.comment(""
										+ "Should the start of the height fog be offset? \n"
										+ "\n"
										+ "0.0: Fog start with no offset.\n"
										+ "1.0: Fog start with offset of the entire world's height. (Includes depth)")
								.build();
						
						public static ConfigEntry<Double> heightFogEnd = new ConfigEntry.Builder<Double>()
								.setMinDefaultMax(FOG_RANGE_MIN, 0.6, FOG_RANGE_MAX)
								.comment(""
										+ "Should the end of the height fog be offset? \n"
										+ "\n"
										+ "0.0: Fog end with no offset.\n"
										+ "1.0: Fog end with offset of the entire world's height. (Include depth)")
								.build();
						
						public static ConfigEntry<Double> heightFogMin = new ConfigEntry.Builder<Double>()
								.setMinDefaultMax(0.0, 0.0, FOG_RANGE_MAX)
								.comment(""
										+ "What is the minimum fog thickness? \n"
										+ "\n"
										+ "0.0: No fog. \n"
										+ "1.0: Fully opaque fog.")
								.build();
						
						public static ConfigEntry<Double> heightFogMax = new ConfigEntry.Builder<Double>()
								.setMinDefaultMax(FOG_RANGE_MIN, 1.0, 5.0)
								.comment(""
										+ "What is the maximum fog thickness? \n"
										+ "\n"
										+ "0.0: No fog. \n"
										+ "1.0: Fully opaque fog.")
								.build();
						
						public static ConfigEntry<EDhApiFogFalloff> heightFogFalloff = new ConfigEntry.Builder<EDhApiFogFalloff>()
								.set(EDhApiFogFalloff.EXPONENTIAL_SQUARED)
								.comment(""
										+ "How should the height fog thickness should be calculated? \n"
										+ "\n"
										+ EDhApiFogFalloff.LINEAR + ": Linear based on height (will ignore 'density')\n"
										+ EDhApiFogFalloff.EXPONENTIAL + ": 1/(e^(height*density)) \n"
										+ EDhApiFogFalloff.EXPONENTIAL_SQUARED + ": 1/(e^((height*density)^2)")
								.build();
						
						public static ConfigEntry<Double> heightFogDensity = new ConfigEntry.Builder<Double>()
								.setMinDefaultMax(0.01, 20.0, 50.0)
								.comment("What is the height fog's density?")
								.build();
						
					}
					
				}
				
				public static class NoiseTexture
				{
					public static ConfigEntry<Boolean> enableNoiseTexture = new ConfigEntry.Builder<Boolean>()
							.set(true)
							.comment(""
									+ "Should a noise texture be applied to LODs? \n"
									+ "\n"
									+ "This is done to simulate textures and make the LODs appear more detailed. \n"
									+ "")
							.build();
					
					public static ConfigEntry<Integer> noiseSteps = new ConfigEntry.Builder<Integer>()
							.setMinDefaultMax(1, 4, null)
							.comment(""
									+ "How many steps of noise should be applied to LODs?"
									+ "")
							.build();
					
					public static ConfigEntry<Double> noiseIntensity = new ConfigEntry.Builder<Double>()    // TODO: Make this a float (the ClassicConfigGUI doesn't support floats)
							.setMinDefaultMax(0d, 5d, 100d)                    // TODO: Once this becomes a float make it 0-1 instead of 0-100 (I did this cus doubles only allow 2 decimal places)
							.comment(""
									+ "How intense should the noise should be?")
							.build();
					
					public static ConfigEntry<Integer> noiseDropoff = new ConfigEntry.Builder<Integer>()    // TODO: Make this a float (the ClassicConfigGUI doesn't support floats)
							.setMinDefaultMax(0, 1024, null)
							.comment(""
									+ "Defines how far should the noise texture render before it fades away. (in blocks) \n"
									+ "Set to 0 to disable noise from fading away \n"
									+ "")
							.build();
					
				}
				
				public static class Culling
				{
					public static ConfigEntry<Double> overdrawPrevention = new ConfigEntry.Builder<Double>()
							.setMinDefaultMax(0.0, 0.0, 1.0)
							.comment(""
									+ "Determines how far from the camera Distant Horizons will start rendering. \n"
									+ "Measured as a percentage of the vanilla render distance.\n"
									+ "\n"
									+ "0 = auto, overdraw will change based on the vanilla render distance.\n"
									+ "\n"
									+ "Higher values will prevent LODs from rendering behind vanilla blocks at a higher distance,\n"
									+ "but may cause holes in the world. \n"
									+ "Holes are most likely to appear when flying through unloaded terrain. \n"
									+ "\n"
									+ "Increasing the vanilla render distance increases the effectiveness of this setting."
									+ "")
							.setPerformance(EConfigEntryPerformance.NONE)
							.build();
					
					public static ConfigEntry<Boolean> enableCaveCulling = new ConfigEntry.Builder<Boolean>()
							.set(true)
							.setPerformance(EConfigEntryPerformance.HIGH)
							.comment(""
									+ "If enabled caves won't be rendered. \n"
									+ "\n"
									+ " Note: for some world types this can cause \n"
									+ " overhangs or walls for floating objects. \n"
									+ " Tweaking the caveCullingHeight, can resolve some \n"
									+ " of those issues. \n"
									+ "")
							.addListener(ReloadLodsConfigEventHandler.INSTANCE)
							.build();
					
					public static ConfigEntry<Integer> caveCullingHeight = new ConfigEntry.Builder<Integer>()
							.setMinDefaultMax(-4096, 60, 4096)
							.comment(""
									+ "At what Y value should cave culling start? \n"
									+ "Lower this value if you get walls for areas with 0 light.")
							.addListener(ReloadLodsConfigEventHandler.INSTANCE)
							.build();
					
					public static ConfigEntry<Boolean> disableBeaconDistanceCulling = new ConfigEntry.Builder<Boolean>()
							.set(true)
							.comment(""
									+ "If false all beacons near the camera won't be drawn to prevent vanilla overdraw. \n"
									+ "If true all beacons will be rendered. \n"
									+ "\n"
									+ "Generally this should be left as true. It's main purpose is for debugging\n"
									+ "beacon updating/rendering.\n"
									+ "")
							.build();
					
					public static ConfigEntry<Boolean> disableFrustumCulling = new ConfigEntry.Builder<Boolean>()
							.set(false)
							.comment(""
									+ "If true LODs outside the player's camera \n"
									+ "aren't drawn, increasing GPU performance. \n"
									+ "\n"
									+ "If false all LODs are drawn, even those behind \n"
									+ "the player's camera, decreasing GPU performance. \n"
									+ "\n"
									+ "Disable this if you see LODs disappearing at the corners of your vision.")
							.build();
					
					public static ConfigEntry<Boolean> disableShadowPassFrustumCulling = new ConfigEntry.Builder<Boolean>()
							.set(false)
							.comment(""
									+ "Identical to the other frustum culling option\n"
									+ "only used when a shader mod is present using the DH API\n"
									+ "and the shadow pass is being rendered.\n"
									+ "\n"
									+ "Disable this if shadows render incorrectly.")
							.build();
					
					public static ConfigEntry<String> ignoredRenderBlockCsv = new ConfigEntry.Builder<String>()
							.set("minecraft:barrier,minecraft:structure_void,minecraft:light,minecraft:tripwire,minecraft:brown_mushroom")
							.comment(""
									+ "A comma separated list of block resource locations that won't be rendered by DH. \n"
									+ "Note: air is always included in this list. \n"
									+ "")
							.build();
					
					public static ConfigEntry<String> ignoredRenderCaveBlockCsv = new ConfigEntry.Builder<String>()
							.set("minecraft:glow_lichen,minecraft:rail,minecraft:water,minecraft:lava,minecraft:bubble_column")
							.comment(""
									+ "A comma separated list of block resource locations that shouldn't be rendered \n"
									+ "if they are in a 0 sky light underground area. \n"
									+ "Note: air is always included in this list. \n"
									+ "")
							.build();
					
					
					static
					{
						ignoredRenderBlockCsv.addListener(new ConfigChangeListener<String>(ignoredRenderBlockCsv,
								(blockCsv) ->
								{
									IWrapperFactory wrapperFactory = SingletonInjector.INSTANCE.get(IWrapperFactory.class);
									if (wrapperFactory != null)
									{
										wrapperFactory.resetRendererIgnoredBlocksSet();
										DhApi.Delayed.renderProxy.clearRenderDataCache();
									}
								}));
						
						ignoredRenderCaveBlockCsv.addListener(new ConfigChangeListener<String>(ignoredRenderCaveBlockCsv,
								(blockCsv) ->
								{
									IWrapperFactory wrapperFactory = SingletonInjector.INSTANCE.get(IWrapperFactory.class);
									if (wrapperFactory != null)
									{
										wrapperFactory.resetRendererIgnoredCaveBlocks();
										DhApi.Delayed.renderProxy.clearRenderDataCache();
									}
								}));
					}
				}
				
				public static class Experimental
				{
					public static ConfigEntry<Integer> earthCurveRatio = new ConfigEntry.Builder<Integer>()
							.setMinDefaultMax(0, 0, 5000)
							.comment(""
									+ "This is the earth size ratio when applying the curvature shader effect. \n"
									+ "Note: Enabling this feature may cause rendering bugs. \n"
									+ "\n"
									+ "0 = flat/disabled \n"
									+ "1 = 1 to 1 (6,371,000 blocks) \n"
									+ "100 = 1 to 100 (63,710 blocks) \n"
									+ "10000 = 1 to 10000 (637.1 blocks) \n"
									+ "\n"
									+ "Note: Due to current limitations, the min value is 50 \n"
									+ "and the max value is 5000. Any values outside this range \n"
									+ "will be set to 0 (disabled).")
							.addListener(WorldCurvatureConfigEventHandler.INSTANCE)
							.build();
				}
				
			}
			
			public static class AutoUpdater
			{
				public static ConfigEntry<Boolean> enableAutoUpdater = new ConfigEntry.Builder<Boolean>()
						.set(!isRunningInDevEnvironment())
						.comment(""
								+ "Automatically check for updates on game launch? \n"
								+ "")
						.build();
				
				public static ConfigEntry<Boolean> enableSilentUpdates = new ConfigEntry.Builder<Boolean>()
						.set(false)
						.comment(""
								+ "Should Distant Horizons silently, automatically download and install new versions? \n"
								+ "This setting is force disabled on dedicated servers for stability reasons. \n"
								+ "")
						.build();
				
				public static ConfigEntry<EDhApiUpdateBranch> updateBranch = new ConfigEntry.Builder<EDhApiUpdateBranch>()
						.set(EDhApiUpdateBranch.AUTO)
						.comment(""
								+ "If DH should use the nightly (provided by Gitlab), or stable (provided by Modrinth) build. \n"
								+ "If ["+EDhApiUpdateBranch.AUTO+"] is selected DH will update to new stable releases if the current jar is a stable jar \n"
								+ "and will update to new nightly builds if the current jar is a nightly jar (IE the version number ends in '-dev')."
								+ "")
						.build();
			}
			
			public static class Multiplayer
			{
				public static ConfigEntry<EDhApiServerFolderNameMode> serverFolderNameMode = new ConfigEntry.Builder<EDhApiServerFolderNameMode>()
						.set(EDhApiServerFolderNameMode.NAME_ONLY)
						.comment(""
								+ "How should multiplayer save folders should be named? \n"
								+ "\n"
								+ EDhApiServerFolderNameMode.NAME_ONLY + ": Example: \"Minecraft Server\" \n"
								+ EDhApiServerFolderNameMode.IP_ONLY + ": Example: \"192.168.1.40\" \n"
								+ EDhApiServerFolderNameMode.NAME_IP + ": Example: \"Minecraft Server IP 192.168.1.40\" \n"
								+ EDhApiServerFolderNameMode.NAME_IP_PORT + ": Example: \"Minecraft Server IP 192.168.1.40:25565\""
								+ EDhApiServerFolderNameMode.NAME_IP_PORT_MC_VERSION + ": Example: \"Minecraft Server IP 192.168.1.40:25565 GameVersion 1.16.5\"")
						.build();
				
			}
			
			public static class Debugging
			{
				public static ConfigEntry<EDhApiRendererMode> rendererMode = new ConfigEntry.Builder<EDhApiRendererMode>()
						.set(EDhApiRendererMode.DEFAULT)
						.comment(""
								+ "What renderer is active? \n"
								+ "\n"
								+ EDhApiRendererMode.DEFAULT + ": Default lod renderer \n"
								+ EDhApiRendererMode.DEBUG + ": Debug testing renderer \n"
								+ EDhApiRendererMode.DISABLED + ": Disable rendering")
						.build();
				
				public static ConfigEntry<EDhApiDebugRendering> debugRendering = new ConfigEntry.Builder<EDhApiDebugRendering>()
						.set(EDhApiDebugRendering.OFF)
						.comment(""
								+ "Should specialized colors/rendering modes be used? \n"
								+ "\n"
								+ EDhApiDebugRendering.OFF + ": LODs will be drawn with their normal colors. \n"
								+ EDhApiDebugRendering.SHOW_DETAIL + ": LODs' color will be based on their detail level. \n"
								+ EDhApiDebugRendering.SHOW_BLOCK_MATERIAL + ": LODs' color will be based on their material. \n"
								+ EDhApiDebugRendering.SHOW_OVERLAPPING_QUADS + ": LODs will be drawn with total white, but overlapping quads will be drawn with red. \n"
								+ "")
						.build();
				
				public static ConfigEntry<Boolean> lodOnlyMode = new ConfigEntry.Builder<Boolean>()
						.set(false)
						.comment(""
								+ "If enabled this will disable (most) vanilla Minecraft rendering. \n"
								+ "\n"
								+ "NOTE: Do not report any issues when this mode is on! \n"
								+ "   This setting is only for fun and debugging. \n"
								+ "   Mod compatibility is not guaranteed.")
						.build();
				
				public static ConfigEntry<Boolean> renderWireframe = new ConfigEntry.Builder<Boolean>()
						.set(false)
						.comment(""
								+ "If enabled the LODs will render as wireframe."
								+ "")
						.build();
				
				// TODO add LOD-only mode to this
				public static ConfigEntry<Boolean> enableDebugKeybindings = new ConfigEntry.Builder<Boolean>()
						.set(false)
						.comment(""
								+ "If true the F8 key can be used to cycle through the different debug modes. \n"
								+ "and the F6 key can be used to enable and disable LOD rendering.")
						.build();
				
				public static ConfigEntry<Boolean> enableWhiteWorld = new ConfigEntry.Builder<Boolean>()
						.set(false)
						.comment(""
								+ "Stops vertex colors from being passed. \n"
								+ "Useful for debugging shaders")
						.build();
				
				public static ConfigEntry<Boolean> showOverlappingQuadErrors = new ConfigEntry.Builder<Boolean>()
						.set(false)
						.comment(""
								+ "If true overlapping quads will be rendered as bright red for easy identification. \n"
								+ "If false the quads will be rendered normally. \n"
								+ "")
						.build();
				
				public static ConfigEntry<Boolean> logBufferGarbageCollection = new ConfigEntry.Builder<Boolean>()
						.set(false)
						.comment(""
								+ "If true OpenGL Buffer garbage collection will be logged \n"
								+ "this also includes the number of live buffers. \n"
								+ "")
						.build();
				
				// Note: This will reset on game restart, and should have a warning on the tooltip
				public static ConfigEntry<Boolean> allowUnsafeValues = new ConfigEntry.Builder<Boolean>()
						.set(false)
						.setAppearance(EConfigEntryAppearance.ONLY_IN_GUI)
						.addListener(UnsafeValuesConfigListener.INSTANCE)
						.build();
				
				public static ConfigCategory debugWireframe = new ConfigCategory.Builder().set(DebugWireframe.class).build();
				public static ConfigCategory openGl = new ConfigCategory.Builder().set(OpenGl.class).build();
				public static ConfigCategory columnBuilderDebugging = new ConfigCategory.Builder().set(ColumnBuilderDebugging.class).build();
				public static ConfigCategory f3Screen = new ConfigCategory.Builder().set(F3Screen.class).build();
				public static ConfigCategory exampleConfigScreen = new ConfigCategory.Builder().set(ExampleConfigScreen.class).build();
				
				
				
				public static class DebugWireframe
				{
					public static ConfigEntry<Boolean> enableRendering = new ConfigEntry.Builder<Boolean>()
							.set(false)
							.comment(""
									+ "If enabled, various wireframes for debugging internal functions will be drawn. \n"
									+ "\n"
									+ "NOTE: There WILL be performance hit! \n"
									+ "   Additionally, only stuff that's loaded after you enable this \n"
									+ "   will render their debug wireframes. \n"
									+ "")
							.build();
					
					public static ConfigEntry<Boolean> showWorldGenQueue = new ConfigEntry.Builder<Boolean>()
							.set(false)
							.comment("Render queued world gen tasks?")
							.build();
					
					public static ConfigEntry<Boolean> showNetworkSyncOnLoadQueue = new ConfigEntry.Builder<Boolean>()
							.set(false)
							.comment("Render queued network sync on load tasks?")
							.build();
					
					public static ConfigEntry<Boolean> showRenderSectionStatus = new ConfigEntry.Builder<Boolean>()
							.set(false)
							.comment("Render LOD section status?")
							.build();
					public static ConfigEntry<Boolean> showRenderSectionToggling = new ConfigEntry.Builder<Boolean>()
							.set(false)
							.comment("" +
									"A white box will be drawn when an LOD starts rendering \n" +
									"and a purple box when an LOD stops rendering. \n" +
									"\n" +
									"This can be used to debug Quad Tree holes.\n" +
									"")
							.build();
					
					public static ConfigEntry<Boolean> showQuadTreeRenderStatus = new ConfigEntry.Builder<Boolean>()
							.set(false)
							.comment("Render Quad Tree Rendering status?")
							.build();
					
					public static ConfigEntry<Boolean> showFullDataUpdateStatus = new ConfigEntry.Builder<Boolean>()
							.set(false)
							.comment("Render full data update/lock status?")
							.build();
					
				}
				
				public static class OpenGl
				{
					public static ConfigEntry<Boolean> overrideVanillaGLLogger = new ConfigEntry.Builder<Boolean>()
							.set(ModInfo.IS_DEV_BUILD)
							.comment(""
									+ "Requires a reboot to change. \n"
									+ "")
							.build();
					
					public static ConfigEntry<EDhApiGLErrorHandlingMode> glErrorHandlingMode = new ConfigEntry.Builder<EDhApiGLErrorHandlingMode>()
							.set(ModInfo.IS_DEV_BUILD ? EDhApiGLErrorHandlingMode.LOG : EDhApiGLErrorHandlingMode.IGNORE)
							.comment(""
									+ "Defines how OpenGL errors are handled. \n"
									+ "May incorrectly catch OpenGL errors thrown by other mods. \n"
									+ "\n"
									+ EDhApiGLErrorHandlingMode.IGNORE + ": Do nothing. \n"
									+ EDhApiGLErrorHandlingMode.LOG + ": write an error to the log. \n"
									+ EDhApiGLErrorHandlingMode.LOG_THROW + ": write to the log and throw an exception. \n"
									+ "           Warning: this should only be enabled when debugging the LOD renderer \n"
									+ "           as it may break Minecraft's renderer when an exception is thrown. \n"
									+ "")
							.build();
					
					public static ConfigEntry<Boolean> validateBufferIdsBeforeRendering = new ConfigEntry.Builder<Boolean>()
							.set(false)
							.comment(""
									+ "Massively reduces FPS. \n"
									+ "Should only be used if mysterious EXCEPTION_ACCESS_VIOLATION crashes are happening in DH's rendering code for troubleshooting. \n"
									+ "")
							.build();
					
				}
				
				public static class ColumnBuilderDebugging
				{
					public static ConfigEntry<Boolean> columnBuilderDebugEnable = new ConfigEntry.Builder<Boolean>()
							.set(false)
							.setAppearance(EConfigEntryAppearance.ONLY_IN_GUI)
							.addListener(ReloadLodsConfigEventHandler.INSTANCE)
							.build();
					public static ConfigEntry<Integer> columnBuilderDebugDetailLevel = new ConfigEntry.Builder<Integer>()
							.set((int) DhSectionPos.SECTION_MINIMUM_DETAIL_LEVEL)
							.setAppearance(EConfigEntryAppearance.ONLY_IN_GUI)
							.addListener(ReloadLodsConfigEventHandler.INSTANCE)
							.build();
					public static ConfigEntry<Integer> columnBuilderDebugXPos = new ConfigEntry.Builder<Integer>()
							.set(0)
							.setAppearance(EConfigEntryAppearance.ONLY_IN_GUI)
							.addListener(ReloadLodsConfigEventHandler.INSTANCE)
							.build();
					public static ConfigEntry<Integer> columnBuilderDebugZPos = new ConfigEntry.Builder<Integer>()
							.set(0)
							.setAppearance(EConfigEntryAppearance.ONLY_IN_GUI)
							.addListener(ReloadLodsConfigEventHandler.INSTANCE)
							.build();
					
					public static ConfigEntry<Integer> columnBuilderDebugXRow = new ConfigEntry.Builder<Integer>()
							.set(-1)
							.setAppearance(EConfigEntryAppearance.ONLY_IN_GUI)
							.addListener(ReloadLodsConfigEventHandler.INSTANCE)
							.build();
					public static ConfigEntry<Integer> columnBuilderDebugZRow = new ConfigEntry.Builder<Integer>()
							.set(-1)
							.setAppearance(EConfigEntryAppearance.ONLY_IN_GUI)
							.addListener(ReloadLodsConfigEventHandler.INSTANCE)
							.build();
					public static ConfigEntry<Integer> columnBuilderDebugColumnIndex = new ConfigEntry.Builder<Integer>()
							.set(-1)
							.setAppearance(EConfigEntryAppearance.ONLY_IN_GUI)
							.addListener(ReloadLodsConfigEventHandler.INSTANCE)
							.build();
					
				}
				
				public static class F3Screen
				{
					public static ConfigEntry<Boolean> showPlayerPos = new ConfigEntry.Builder<Boolean>()
							.set(true)
							.comment("Shows info about each thread pool.")
							.build();
					public static ConfigEntry<Integer> playerPosSectionDetailLevel = new ConfigEntry.Builder<Integer>()
							.setMinDefaultMax(6, 6, 16)
							.comment("" +
									"Defines what internal detail level the player position will be shown as. \n" +
									"Internal detail level means: 6 = 1x1 block, 7 = 2x2 blocks, etc. \n" +
									"")
							.build();
					
					public static ConfigEntry<Boolean> showThreadPools = new ConfigEntry.Builder<Boolean>()
							.set(true)
							.comment("Shows info about each thread pool.")
							.build();
					
					public static ConfigEntry<Boolean> showCombinedObjectPools = new ConfigEntry.Builder<Boolean>()
							.set(false)
							.comment("Shows the combined memory use and array counts for all DH pooled objects.")
							.build();
					public static ConfigEntry<Boolean> showSeparatedObjectPools = new ConfigEntry.Builder<Boolean>()
							.set(false)
							.comment("Shows the memory use and array counts for each DH object pool.")
							.build();
					
					public static ConfigEntry<Boolean> showQueuedChunkUpdateCount = new ConfigEntry.Builder<Boolean>()
							.set(true)
							.comment("Shows how many chunks are queud for processing and the max count that can be queued.")
							.build();
					
					public static ConfigEntry<Boolean> showLevelStatus = new ConfigEntry.Builder<Boolean>()
							.set(true)
							.comment("Shows what levels are loaded and world gen/rendering info about those levels.")
							.build();
					
				}
				
				/** This class is used to debug the different features of the config GUI */
				// FIXME: WARNING: Some of the options in this class dont get show n in the default UI
				// This will throw a warning when opened in the default ui to tell you about it not showing
				public static class ExampleConfigScreen
				{
					// Defined in the lang, just a note about this screen
					public static ConfigUIComment debugConfigScreenNote = new ConfigUIComment();
					
					public static ConfigEntry<Boolean> boolTest = new ConfigEntry.Builder<Boolean>()
							.set(false)
							.build();
					
					public static ConfigEntry<Byte> byteTest = new ConfigEntry.Builder<Byte>()
							.set((byte) 8)
							.build();
					
					public static ConfigEntry<Integer> intTest = new ConfigEntry.Builder<Integer>()
							.set(69420)
							.build();
					
					public static ConfigEntry<Double> doubleTest = new ConfigEntry.Builder<Double>()
							.set(420.69d)
							.build();
					
					public static ConfigEntry<Short> shortTest = new ConfigEntry.Builder<Short>()
							.set((short) 69)
							.build();
					
					public static ConfigEntry<Long> longTest = new ConfigEntry.Builder<Long>()
							.set(42069L)
							.build();
					
					public static ConfigEntry<Float> floatTest = new ConfigEntry.Builder<Float>()
							.set(0.42069f)
							.build();
					
					public static ConfigEntry<String> stringTest = new ConfigEntry.Builder<String>()
							.set("Test input box")
							.build();
					
					public static ConfigEntry<List<String>> listTest = new ConfigEntry.Builder<List<String>>()
							.set(new ArrayList<String>(Arrays.asList("option 1", "option 2", "option 3")))
							.build();
					
					public static ConfigEntry<Map<String, String>> mapTest = new ConfigEntry.Builder<Map<String, String>>()
							.set(new HashMap<String, String>())
							.build();
					
					public static ConfigUIButton uiButtonTest = new ConfigUIButton(() -> 
					{
						// running on a separate thread is necessary to prevent locking
						new Thread(() -> 
						{
							if (!GraphicsEnvironment.isHeadless())
							{
								LOGGER.info("Attempting to show tinyfd message box...");
								boolean buttonPress = TinyFileDialogs.tinyfd_messageBox("Button pressed!", "UITester dialog", "ok", "info", false);
								LOGGER.info("dialog returned with ["+(buttonPress ? "TRUE" : "FALSE")+"]");
							}
							else
							{
								LOGGER.info("button pressed!");
							}
						}).start();
					});
					
					public static ConfigCategory categoryTest = new ConfigCategory.Builder().set(CategoryTest.class).build();
					
					public static ConfigEntry<Integer> linkableTest = new ConfigEntry.Builder<Integer>()
							.set(420)
							.build();
					
					
					public static class CategoryTest
					{
						// The name of this can be anything as it will be overwritten by the name of the linked object
						public static ConfigUiLinkedEntry linkableTest = new ConfigUiLinkedEntry(ExampleConfigScreen.linkableTest);
						
					}
				}
				
			}
		}
	}
	
	public static class Common
	{
		public static class WorldGenerator
		{
			public static ConfigEntry<Boolean> enableDistantGeneration = new ConfigEntry.Builder<Boolean>()
					.setChatCommandName("generation.enable")
					.set(true)
					.comment(""
							+ " Should Distant Horizons slowly generate LODs \n"
							+ " outside the vanilla render distance? \n"
							+ "Depending on the generator mode, this will import existing chunks \n"
							+ "and/or generating missing chunks."
							+ "")
					.build();
			
			public static ConfigEntry<EDhApiDistantGeneratorMode> distantGeneratorMode = new ConfigEntry.Builder<EDhApiDistantGeneratorMode>()
					.setChatCommandName("generation.mode")
					.set(EDhApiDistantGeneratorMode.FEATURES)
					.comment(""
							+ "How detailed should LODs be generated outside the vanilla render distance? \n"
							+ "\n"
							+ EDhApiDistantGeneratorMode.PRE_EXISTING_ONLY + " \n"
							+ "Only create LOD data for already generated chunks. \n"
							+ "\n"
							//not currently implemented
							//+ EDhApiDistantGeneratorMode.BIOME_ONLY + " \n"
							//+ "Only generate the biomes and use the biome's \n"
							//+ "grass color, water color, or snow color. \n"
							//+ "Doesn't generate height, everything is shown at sea level. \n"
							//+ "- Fastest \n"
							//+ "\n"
							//+ EDhApiDistantGeneratorMode.BIOME_ONLY_SIMULATE_HEIGHT + " \n"
							//+ "Same as " + EDhApiDistantGeneratorMode.BIOME_ONLY + ", except instead \n"
							//+ "of always using sea level as the LOD height \n"
							//+ "different biome types (mountain, ocean, forest, etc.) \n"
							//+ "use predetermined heights to simulate having height data. \n"
							//+ "- Fastest \n"
							+ "\n"
							+ EDhApiDistantGeneratorMode.SURFACE + " \n"
							+ "Generate the world surface, \n"
							+ "this does NOT include trees, \n"
							+ "or structures. \n"
							+ "\n"
							+ EDhApiDistantGeneratorMode.FEATURES + " \n"
							+ "Generate everything except structures. \n"
							+ "WARNING: This may cause world generator bugs or instability when paired with certain world generator mods. \n"
							+ "\n"
							+ EDhApiDistantGeneratorMode.INTERNAL_SERVER + " \n"
							+ "Ask the local server to generate/load each chunk. \n"
							+ "This is the most compatible and will generate structures correctly, \n"
							+ "but may cause server/simulation lag. \n"
							+ "Note: unlike other modes this option DOES save generated chunks to \n"
							+ "Minecraft's region files. \n"
							+ "")
					.build();
			
			public static ConfigEntry<EDhApiDistantGeneratorProgressDisplayLocation> showGenerationProgress = new ConfigEntry.Builder<EDhApiDistantGeneratorProgressDisplayLocation>()
					.set(EDhApiDistantGeneratorProgressDisplayLocation.OVERLAY)
					.comment(""
							+ "How should distant generator progress be displayed? \n"
							+ "\n"
							+ EDhApiDistantGeneratorProgressDisplayLocation.OVERLAY + ": may be the same as "+EDhApiDistantGeneratorProgressDisplayLocation.CHAT+" for some Minecraft versions \n"
							+ EDhApiDistantGeneratorProgressDisplayLocation.CHAT + " \n"
							+ EDhApiDistantGeneratorProgressDisplayLocation.LOG + " \n"
							+ EDhApiDistantGeneratorProgressDisplayLocation.DISABLED + " \n"
							+ "")
					.build();
			
			public static ConfigEntry<Integer> generationProgressDisplayIntervalInSeconds = new ConfigEntry.Builder<Integer>()
					.setChatCommandName("generation.logInterval")
					.setMinDefaultMax(1, 5, 60 * 60 * 4) // max = 4 hours
					.comment(""
							+ "How often should the distant generator progress be displayed? \n"
							+ "")
					.build();
			
			public static ConfigEntry<Integer> generationProgressDisableMessageDisplayTimeInSeconds = new ConfigEntry.Builder<Integer>()
					.setMinDefaultMax(0, 20, 60 * 60) // max = 1 hour
					.comment(""
							+ "For how many seconds should instructions for disabling the distant generator progress be displayed? \n"
							+ "Setting this to 0 hides the instructional message so the world gen progress is shown immediately when it starts. \n"
							+ "")
					.build();
			
		}
		
		public static class LodBuilding
		{
			public static ConfigEntry<Boolean> disableUnchangedChunkCheck = new ConfigEntry.Builder<Boolean>()
					.set(false)
					// enabling this can be quite detrimental to performance,
					// so hiding it in the config file should reduce people accidentally enabling it
					.setAppearance(isRunningInDevEnvironment() ? EConfigEntryAppearance.ALL : EConfigEntryAppearance.ONLY_IN_FILE)
					.comment(""
							+ "Enabling this will drastically increase chunk processing time\n"
							+ "and you may need to increase your CPU load to handle it.\n"
							+ "\n"
							+ "Normally DH will attempt to skip creating LODs for chunks it's already seen\n"
							+ "and that haven't changed.\n"
							+ "\n"
							+ "However sometimes that logic incorrectly prevents LODs from being updated.\n"
							+ "Disabling this check may fix issues where LODs aren't updated after\n"
							+ "blocks have been changed.\n"
							+ "")
					.build();
			
			public static ConfigEntry<EDhApiDataCompressionMode> dataCompression = new ConfigEntry.Builder<EDhApiDataCompressionMode>()
					.set(EDhApiDataCompressionMode.LZMA2)
					.comment(""
							+ "What algorithm should be used to compress new LOD data? \n"
							+ "This setting will only affect new or updated LOD data, \n"
							+ "any data already generated when this setting is changed will be\n"
							+ "unaffected until it needs to be re-written to the database.\n"
							+ "\n"
							+ EDhApiDataCompressionMode.UNCOMPRESSED + " \n"
							+ "Should only be used for testing, is worse in every way vs ["+EDhApiDataCompressionMode.LZ4+"].\n"
							+ "Expected Compression Ratio: 1.0\n"
							+ "Estimated average DTO read speed: 1.64 milliseconds\n"
							+ "Estimated average DTO write speed: 12.44 milliseconds\n"
							+ "\n"
							+ EDhApiDataCompressionMode.LZ4 + " \n"
							+ "A good option if you're CPU limited and have plenty of hard drive space.\n"
							+ "Expected Compression Ratio: 0.36\n"
							+ "Estimated average DTO read speed: 1.85 ms\n"
							+ "Estimated average DTO write speed: 9.46 ms\n"
							+ "\n"
							+ EDhApiDataCompressionMode.LZMA2 + " \n"
							+ "Slow but very good compression.\n"
							+ "Expected Compression Ratio: 0.14\n"
							+ "Estimated average DTO read speed: 11.89 ms\n"
							+ "Estimated average DTO write speed: 192.01 ms\n"
							+ "")
					.build();
			
			public static ConfigEntry<EDhApiWorldCompressionMode> worldCompression = new ConfigEntry.Builder<EDhApiWorldCompressionMode>()
					.set(EDhApiWorldCompressionMode.VISUALLY_EQUAL)
					.comment(""
							+ "How should block data be compressed when creating LOD data? \n"
							+ "This setting will only affect new or updated LOD data, \n"
							+ "any data already generated when this setting is changed will be\n"
							+ "unaffected until it is modified or re-loaded.\n"
							+ "\n"
							+ EDhApiWorldCompressionMode.MERGE_SAME_BLOCKS + " \n"
							+ "Every block/biome change is recorded in the database. \n"
							+ "This is what DH 2.0 and 2.0.1 all used by default and will store a lot of data. \n"
							+ "Expected Compression Ratio: 1.0\n"
							+ "\n"
							+ EDhApiWorldCompressionMode.VISUALLY_EQUAL + " \n"
							+ "Only visible block/biome changes are recorded in the database. \n"
							+ "Hidden blocks (IE ores) are ignored.  \n"
							+ "Expected Compression Ratio: 0.7\n"
							+ "")
					.build();
			
			public static ConfigEntry<Boolean> recalculateChunkHeightmaps = new ConfigEntry.Builder<Boolean>()
					.set(false)
					.comment(""
							+ "True: Recalculate chunk height maps before chunks can be used by DH.\n"
							+ "      This can fix problems with worlds created by World Painter or \n"
							+ "      other external tools where the heightmap format may be incorrect. \n"
							+ "False: Assume any height maps handled by Minecraft are correct. \n"
							+ "\n"
							+ "Fastest: False\n"
							+ "Most Compatible: True\n"
							+ "")
					.build();
			
			public static ConfigEntry<Boolean> pullLightingForPregeneratedChunks = new ConfigEntry.Builder<Boolean>()
					.set(false)
					.comment(""
							+ "If true LOD generation for pre-existing chunks will attempt to pull the lighting data \n"
							+ "saved in Minecraft's Region files. \n"
							+ "If false DH will pull in chunks without lighting and re-light them. \n"
							+ " \n"
							+ "Setting this to true will result in faster LOD generation \n"
							+ "for already generated worlds, but is broken by most lighting mods. \n"
							+ " \n"
							+ "Set this to false if LODs are black. \n"
							+ "")
					.build();
			
			public static ConfigEntry<Boolean> assumePreExistingChunksAreFinished = new ConfigEntry.Builder<Boolean>()
					.set(false)
					.comment(""
							+ "When DH pulls in pre-existing chunks it will attempt to \n"
							+ "run any missing world generation steps; for example: \n"
							+ "if a chunk has the status SURFACE, DH will skip BIOMES \n"
							+ "and SURFACE, but will run FEATURES. \n"
							+ " \n"
							+ "However if for some reason the chunks are malformed \n"
							+ "or there's some other issue that causes the status \n"
							+ "to be incorrect that can either cause world gen \n"
							+ "lock-ups and/or crashes. \n"
							+ "If either of those happen try setting this to True. \n"
							+ "")
					.build();
			
			public static ConfigCategory experimental = new ConfigCategory.Builder().set(Experimental.class).build();
			
			
			
			public static class Experimental
			{
				public static ConfigEntry<Boolean> upsampleLowerDetailLodsToFillHoles = new ConfigEntry.Builder<Boolean>()
						.set(false)
						.comment(""
								+ "When active DH will attempt to fill missing LOD data \n"
								+ "with any data that is present in the tree, preventing holes when moving \n"
								+ "when a N-sized generator (or server) is active. \n"
								+ "\n"
								+ "This is only used when N-sized world generation is available \n"
								+ "and/or when on a server where [generateOnlyInHighestDetail] is false. \n"
								+ "\n"
								+ "Experimental:\n"
								+ "Enabling this option will increase CPU and harddrive use\n"
								+ "and may cause rendering bugs.\n"
								+ "\n"
								+ "")
						.build();
			}
			
		}
		
		public static class MultiThreading
		{
			public static final ConfigEntry<Integer> numberOfThreads = new ConfigEntry.Builder<Integer>()
					.setChatCommandName("threading.numberOfThreads")
					.setMinDefaultMax(1,
							ThreadPresetConfigEventHandler.getDefaultThreadCount(),
							Runtime.getRuntime().availableProcessors())
					.comment(""
							+ "How many threads should be used by Distant Horizons? \n"
							+ "")
					.build();
			public static final ConfigEntry<Double> threadRunTimeRatio = new ConfigEntry.Builder<Double>()
					.setChatCommandName("threading.threadRunTimeRatio")
					.setMinDefaultMax(0.01, ThreadPresetConfigEventHandler.getDefaultRunTimeRatio(), 1.0)
					.comment(""
							+ "A value between 1.0 and 0.0 that represents the percentage \n"
							+ "of time each thread can run before going idle. \n"
							+ "\n"
							+ "This can be used to reduce CPU usage if the thread count \n"
							+ "is already set to 1 for the given option, or more finely \n"
							+ "tune CPU performance. \n" +
							"")
					.build();
			
			
			
		}
		
		public static class Logging
		{
			// TODO add change all option
			// TODO default to error chat and info file
			public static ConfigEntry<EDhApiLoggerMode> logWorldGenEvent = new ConfigEntry.Builder<EDhApiLoggerMode>()
					.setChatCommandName("logging.logWorldGenEvent")
					.set(EDhApiLoggerMode.LOG_ERROR_TO_CHAT_AND_INFO_TO_FILE)
					.comment(""
							+ "If enabled, the mod will log information about the world generation process. \n"
							+ "This can be useful for debugging.")
					.build();
			
			public static ConfigEntry<EDhApiLoggerMode> logWorldGenPerformance = new ConfigEntry.Builder<EDhApiLoggerMode>()
					.setChatCommandName("logging.logWorldGenPerformance")
					.set(EDhApiLoggerMode.LOG_ERROR_TO_CHAT_AND_INFO_TO_FILE)
					.comment(""
							+ "If enabled, the mod will log performance about the world generation process. \n"
							+ "This can be useful for debugging.")
					.build();
			
			public static ConfigEntry<EDhApiLoggerMode> logWorldGenLoadEvent = new ConfigEntry.Builder<EDhApiLoggerMode>()
					.setChatCommandName("logging.logWorldGenLoadEvent")
					.set(EDhApiLoggerMode.LOG_ERROR_TO_CHAT_AND_INFO_TO_FILE)
					.comment(""
							+ "If enabled, the mod will log information about the world generation process. \n"
							+ "This can be useful for debugging.")
					.build();
			
			public static ConfigEntry<EDhApiLoggerMode> logRendererBufferEvent = new ConfigEntry.Builder<EDhApiLoggerMode>()
					.set(EDhApiLoggerMode.LOG_ERROR_TO_CHAT_AND_INFO_TO_FILE)
					.comment(""
							+ "If enabled, the mod will log information about the renderer buffer process. \n"
							+ "This can be useful for debugging.")
					.build();
			
			public static ConfigEntry<EDhApiLoggerMode> logRendererGLEvent = new ConfigEntry.Builder<EDhApiLoggerMode>()
					.set(EDhApiLoggerMode.LOG_ERROR_TO_CHAT_AND_INFO_TO_FILE)
					.comment(""
							+ "If enabled, the mod will log information about the renderer OpenGL process. \n"
							+ "This can be useful for debugging.")
					.build();
			
			public static ConfigEntry<EDhApiLoggerMode> logNetworkEvent = new ConfigEntry.Builder<EDhApiLoggerMode>()
					.setChatCommandName("logging.logNetworkEvent")
					.set(EDhApiLoggerMode.LOG_ERROR_TO_CHAT_AND_INFO_TO_FILE)
					.comment(""
							+ "If enabled, the mod will log information about network operations. \n"
							+ "This can be useful for debugging.")
					.build();
			
			public static ConfigCategory warning = new ConfigCategory.Builder().set(Warning.class).build();
			
			
			
			public static class Warning
			{
				
				public static ConfigEntry<Boolean> showLowMemoryWarningOnStartup = new ConfigEntry.Builder<Boolean>()
						.set(true)
						.comment(""
								+ "If enabled, a chat message will be displayed if Java doesn't have enough \n"
								+ "memory allocated to run DH well. \n"
								+ "")
						.build();
				
				public static ConfigEntry<Boolean> showPoolInsufficientMemoryWarning = new ConfigEntry.Builder<Boolean>()
						.set(true)
						.comment(""
								+ "If enabled, a chat message will be displayed if DH detects \n"
								+ "that any pooled objects have been garbage collected. \n"
								+ "")
						.build();
				
				public static ConfigEntry<Boolean> showHighVanillaRenderDistanceWarning = new ConfigEntry.Builder<Boolean>()
						.set(true)
						.comment(""
								+ "If enabled, a chat message will be displayed if vanilla MC's \n"
								+ "render distance is higher than the recommended amount. \n"
								+ "")
						.build();
				
				public static ConfigEntry<Boolean> showReplayWarningOnStartup = new ConfigEntry.Builder<Boolean>()
						.set(true)
						.comment(""
								+ "If enabled, a chat message will be displayed when a replay is started \n"
								+ "giving some basic information about how DH will function. \n"
								+ "")
						.build();
				
				public static ConfigEntry<Boolean> showUpdateQueueOverloadedChatWarning = new ConfigEntry.Builder<Boolean>()
						.set(false)
						.comment(""
								+ "If enabled, a chat message will be displayed when DH has too many chunks \n"
								+ "queued for updating. \n"
								+ "")
						.build();
				
				public static ConfigEntry<Boolean> showModCompatibilityWarningsOnStartup = new ConfigEntry.Builder<Boolean>()
						.set(true)
						.comment(""
								+ "If enabled, a chat message will be displayed when a potentially problematic \n"
								+ "mod is installed alongside DH. \n"
								+ "")
						.build();
				
			}
		}
		
	}
	
	public static class Server
	{
		// Level keys
		public static ConfigEntry<Boolean> sendLevelKeys = new ConfigEntry.Builder<Boolean>()
				.setChatCommandName("levelKeys.send")
				.setAppearance(EConfigEntryAppearance.ONLY_IN_FILE)
				.set(true)
				.comment(""
						+ "Makes the server send level keys for each world.\n"
						+ "Disable this if you use alternative ways to send level keys.\n"
						+ "")
				.build();
		
		public static ConfigEntry<String> levelKeyPrefix = new ConfigEntry.Builder<String>()
				.setChatCommandName("levelKeys.prefix")
				.set("")
				.comment(""
						+ "Prefix of the level keys sent to the clients.\n"
						+ "If the mod is running behind a proxy, each backend should use a unique value.\n"
						+ "If this value is empty, level key will be based on the server's seed hash.\n"
						+ "")
				.build();
		
		
		// Generation
		public static ConfigEntry<Integer> generationRequestRateLimit = new ConfigEntry.Builder<Integer>()
				.setChatCommandName("generation.requestRateLimit")
				.setMinDefaultMax(1, 20, 100)
				.comment(""
						+ "How many LOD generation requests per second should a client send? \n"
						+ "Also limits the number of client requests allowed to stay in the server's queue."
						+ "")
				.build();
		
		public static ConfigEntry<Integer> maxGenerationRequestDistance = new ConfigEntry.Builder<Integer>()
				.setChatCommandName("generation.maxRequestDistance")
				.setMinDefaultMax(256, 4096, 4096)
				.comment("" +
						"Defines the distance allowed to generate around the player." +
						"")
				.setPerformance(EConfigEntryPerformance.HIGH)
				.build();
		
		public static ConfigEntry<Integer> generationBoundsX = new ConfigEntry.Builder<Integer>()
				.setChatCommandName("generation.bounds.x")
				.setAppearance(EConfigEntryAppearance.ONLY_IN_FILE)
				.setMinDefaultMax(Integer.MIN_VALUE, 0, Integer.MAX_VALUE)
				.comment("" +
						"Defines the X-coordinate of the central point for generation boundaries, in blocks. \n" +
						"")
				.build();
		public static ConfigEntry<Integer> generationBoundsZ = new ConfigEntry.Builder<Integer>()
				.setChatCommandName("generation.bounds.z")
				.setAppearance(EConfigEntryAppearance.ONLY_IN_FILE)
				.setMinDefaultMax(Integer.MIN_VALUE, 0, Integer.MAX_VALUE)
				.comment("" +
						"Defines the Z-coordinate of the central point for generation boundaries, in blocks. \n" +
						"")
				.build();
		public static ConfigEntry<Integer> generationBoundsRadius = new ConfigEntry.Builder<Integer>()
				.setChatCommandName("generation.bounds.radius")
				.setAppearance(EConfigEntryAppearance.ONLY_IN_FILE)
				.setMinDefaultMax(0, 0, Integer.MAX_VALUE)
				.comment("" +
						"Defines the radius around the central point within which generation is allowed, in blocks. \n" +
						"If this value is set to 0, generation bounds are disabled." +
						"")
				.build();
		
		
		// Real-time updates
		public static ConfigEntry<Boolean> enableRealTimeUpdates = new ConfigEntry.Builder<Boolean>()
				.setChatCommandName("realTimeUpdates.enable")
				.set(true)
				.comment(""
						+ "If true, clients will receive real-time LOD updates for chunks outside the client's render distance."
						+ "")
				.build();
		
		public static ConfigEntry<Integer> realTimeUpdateDistanceRadiusInChunks = new ConfigEntry.Builder<Integer>()
				.setChatCommandName("realTimeUpdates.playerDistance")
				.setMinDefaultMax(32, 256, 4096)
				.comment("" +
						"Defines the distance the player will receive updates around." +
						"")
				.setPerformance(EConfigEntryPerformance.HIGH)
				.build();
		
		
		// Sync on load
		public static ConfigEntry<Boolean> synchronizeOnLoad = new ConfigEntry.Builder<Boolean>()
				.setChatCommandName("syncOnLoad.enable")
				.set(true)
				.comment(""
						+ "If true, clients will receive updated LODs when joining or loading new LODs. \n"
						+ "")
				.build();
		
		public static ConfigEntry<Integer> syncOnLoadRateLimit = new ConfigEntry.Builder<Integer>()
				.setChatCommandName("syncOnLoad.rateLimit")
				.setMinDefaultMax(1, 50, 100)
				.comment(""
						+ "How many LOD sync requests per second should a client send? \n"
						+ "Also limits the amount of player's requests allowed to stay in the server's queue."
						+ "")
				.build();
		
		public static ConfigEntry<Integer> maxSyncOnLoadRequestDistance = new ConfigEntry.Builder<Integer>()
				.setChatCommandName("syncOnLoad.maxRequestDistance")
				.setMinDefaultMax(256, 4096, 4096)
				.comment("" +
						"Defines the distance allowed to be synchronized around the player. \n" +
						"Should be the same or larger than maxGenerationRequestDistance in most cases." +
						"")
				.setPerformance(EConfigEntryPerformance.HIGH)
				.build();
		
		
		// Common
		public static ConfigEntry<Integer> maxDataTransferSpeed = new ConfigEntry.Builder<Integer>()
				.setChatCommandName("common.maxDataTransferSpeed")
				.setMinDefaultMax(0, 500, 1000000 /* 1 GB/s */)
				.comment(""
						+ "Maximum speed for uploading LODs to the clients, in KB/s.\n"
						+ "Value of 0 disables the limit."
						+ "")
				.build();
		
		public static ConfigCategory experimental = new ConfigCategory.Builder().set(Experimental.class).build();
		
		
		
		public static class Experimental
		{
			public static ConfigEntry<Boolean> enableNSizedGeneration = new ConfigEntry.Builder<Boolean>()
					.setChatCommandName("generation.nSized")
					.set(false)
					.comment(""
							+ "When enabled on the client, this allows loading lower detail levels as needed to speed up terrain generation.\n"
							+ "This must also be enabled on the server; otherwise, it will have no effect.\n"
							+ "For better performance when switching LOD detail levels, enabling [upsampleLowerDetailLodsToFillHoles] is recommended.\n"
							+ "")
					.build();
		}
		
	}
	
	
	
	//================//
	// helper methods //
	//================//
	
	/** the setup should only be called once */
	private static boolean complicatedListenerSetupComplete = false;
	/**
	 * Runs any config setup that needs all (or most) config entries be initialized (not null),
	 * but doesn't necessarily require they have the right values yet. <br><br>
	 *
	 * Specially:
	 * Updates any config values that are UI only
	 * and adds any listeners that depend on multiple config values.
	 */
	public static void completeDelayedSetup()
	{
		if (!complicatedListenerSetupComplete)
		{
			complicatedListenerSetupComplete = true;
			
			try
			{
				// TODO automatically get all instances of AbstractPresetConfigEventHandler and fire "setUiOnlyConfigValues"
				ThreadPresetConfigEventHandler.INSTANCE.setUiOnlyConfigValues();
				RenderQualityPresetConfigEventHandler.INSTANCE.setUiOnlyConfigValues();
				QuickRenderToggleConfigEventHandler.INSTANCE.setUiOnlyConfigValues();
				QuickShowWorldGenProgressConfigEventHandler.INSTANCE.setUiOnlyConfigValues();
				RenderCacheConfigEventHandler.getInstance();
			}
			catch (Exception e)
			{
				LOGGER.error("Unexpected exception when running config delayed UI setup. Error: [" + e.getMessage() + "].", e);
			}
		}
	}
	
	/** Guesses whether a dev environment is used based on the current folder path */
	private static boolean isRunningInDevEnvironment()
	{
		IMinecraftSharedWrapper mcShared = SingletonInjector.INSTANCE.get(IMinecraftSharedWrapper.class);
		File installFolder = mcShared.getInstallationDirectory();
		File installParentFolder = installFolder.getParentFile();
		
		// new merged DH format "run/client" or "run/server"
		if (installParentFolder != null && installParentFolder.getName().equals("run"))
		{
			if (installFolder.getName().equals("client")
				|| installFolder.getName().equals("server"))
			{
				return true;
			}
		}
		
		// old DH format "run/"
		if (installFolder.getName().equals("run"))
		{
			return true;
		}
		
		return false;
	}
	
}
