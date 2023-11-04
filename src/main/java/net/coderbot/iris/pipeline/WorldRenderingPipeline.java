package net.coderbot.iris.pipeline;

import com.gtnewhorizons.angelica.compat.mojang.Camera;
import com.gtnewhorizons.angelica.compat.mojang.LevelRenderer;
import net.coderbot.iris.gbuffer_overrides.matching.InputAvailability;
import net.coderbot.iris.gbuffer_overrides.matching.SpecialCondition;
import net.coderbot.iris.gbuffer_overrides.state.RenderTargetStateListener;
import net.coderbot.iris.shaderpack.CloudSetting;
import net.coderbot.iris.uniforms.FrameUpdateNotifier;

import java.util.List;
import java.util.OptionalInt;

public interface WorldRenderingPipeline {
	void beginLevelRendering();
	void renderShadows(LevelRenderer levelRenderer, Camera camera);
	void addDebugText(List<String> messages);
	OptionalInt getForcedShadowRenderDistanceChunksForDisplay();

	WorldRenderingPhase getPhase();

	void beginSodiumTerrainRendering();
	void endSodiumTerrainRendering();
	void setOverridePhase(WorldRenderingPhase phase);
	void setPhase(WorldRenderingPhase phase);
	void setInputs(InputAvailability availability);
	void setSpecialCondition(SpecialCondition special);
	void syncProgram();
	RenderTargetStateListener getRenderTargetStateListener();

	int getCurrentNormalTexture();
	int getCurrentSpecularTexture();

	void onBindTexture(int id);

	void beginHand();

	void beginTranslucents();
	void finalizeLevelRendering();
	void destroy();

	SodiumTerrainPipeline getSodiumTerrainPipeline();
	FrameUpdateNotifier getFrameUpdateNotifier();

	boolean shouldDisableVanillaEntityShadows();
	boolean shouldDisableDirectionalShading();
	CloudSetting getCloudSetting();
	boolean shouldRenderUnderwaterOverlay();
	boolean shouldRenderVignette();
	boolean shouldRenderSun();
	boolean shouldRenderMoon();
	boolean shouldWriteRainAndSnowToDepthBuffer();
	boolean shouldRenderParticlesBeforeDeferred();
	boolean allowConcurrentCompute();

	float getSunPathRotation();
}
