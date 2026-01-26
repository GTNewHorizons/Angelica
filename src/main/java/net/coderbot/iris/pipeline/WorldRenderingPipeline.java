package net.coderbot.iris.pipeline;

import com.gtnewhorizons.angelica.compat.mojang.Camera;
import net.coderbot.iris.compat.dh.DHCompat;
import net.coderbot.iris.gbuffer_overrides.matching.InputAvailability;
import net.coderbot.iris.gbuffer_overrides.matching.SpecialCondition;
import net.coderbot.iris.gbuffer_overrides.state.RenderTargetStateListener;
import net.coderbot.iris.celeritas.CeleritasTerrainPipeline;
import net.coderbot.iris.shaderpack.CloudSetting;
import net.coderbot.iris.uniforms.FrameUpdateNotifier;
import net.minecraft.client.renderer.EntityRenderer;

import java.util.List;
import java.util.OptionalInt;

public interface WorldRenderingPipeline {
	void beginLevelRendering();
	void renderShadows(EntityRenderer levelRenderer, Camera camera);
	void addDebugText(List<String> messages);
	OptionalInt getForcedShadowRenderDistanceChunksForDisplay();

	WorldRenderingPhase getPhase();

	void setOverridePhase(WorldRenderingPhase phase);
	void setPhase(WorldRenderingPhase phase);
	void setInputs(InputAvailability availability);
	void setSpecialCondition(SpecialCondition special);
	RenderTargetStateListener getRenderTargetStateListener();

	int getCurrentNormalTexture();
	int getCurrentSpecularTexture();

	void onBindTexture(int id);

	void beginHand();

	void beginTranslucents();
	void finalizeLevelRendering();
	void destroy();

	CeleritasTerrainPipeline getCeleritasTerrainPipeline();
	FrameUpdateNotifier getFrameUpdateNotifier();
	DHCompat getDHCompat();

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
