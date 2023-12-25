package net.coderbot.iris.pipeline;

import com.gtnewhorizons.angelica.glsm.GLStateManager;
import lombok.Getter;
import net.coderbot.iris.Iris;
import net.coderbot.iris.block_rendering.BlockRenderingSettings;
import net.coderbot.iris.shaderpack.DimensionId;
import net.coderbot.iris.uniforms.SystemTimeUniforms;
import net.minecraft.client.Minecraft;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

public class PipelineManager {
	private static PipelineManager instance;
	private final Function<DimensionId, WorldRenderingPipeline> pipelineFactory;
	private final Map<DimensionId, WorldRenderingPipeline> pipelinesPerDimension = new HashMap<>();
	private WorldRenderingPipeline pipeline = new FixedFunctionWorldRenderingPipeline();
    @Getter
	private int versionCounterForSodiumShaderReload = 0;

	public PipelineManager(Function<DimensionId, WorldRenderingPipeline> pipelineFactory) {
		this.pipelineFactory = pipelineFactory;
	}

	public WorldRenderingPipeline preparePipeline(DimensionId currentDimension) {
		if (!pipelinesPerDimension.containsKey(currentDimension)) {
			SystemTimeUniforms.COUNTER.reset();
			SystemTimeUniforms.TIMER.reset();

			Iris.logger.info("Creating pipeline for dimension {}", currentDimension);
			pipeline = pipelineFactory.apply(currentDimension);
			pipelinesPerDimension.put(currentDimension, pipeline);

			if (BlockRenderingSettings.INSTANCE.isReloadRequired()) {
				if (Minecraft.getMinecraft().renderGlobal != null) {
                    // TODO: Iris
//					Minecraft.getMinecraft().renderGlobal.allChanged();
				}

				BlockRenderingSettings.INSTANCE.clearReloadRequired();
			}
		} else {
			pipeline = pipelinesPerDimension.get(currentDimension);
		}

		return pipeline;
	}

	@Nullable
	public WorldRenderingPipeline getPipelineNullable() {
		return pipeline;
	}

	public Optional<WorldRenderingPipeline> getPipeline() {
		return Optional.ofNullable(pipeline);
	}


	/**
	 * Destroys all the current pipelines.
	 *
	 * <p>This method is <b>EXTREMELY DANGEROUS!</b> It is a huge potential source of hard-to-trace inconsistencies
	 * in program state. You must make sure that you <i>immediately</i> re-prepare the pipeline after destroying
	 * it to prevent the program from falling into an inconsistent state.</p>
	 *
	 * <p>In particular, </p>
	 *
	 * @see <a href="https://github.com/IrisShaders/Iris/issues/1330">this GitHub issue</a>
	 */
	public void destroyPipeline() {
		pipelinesPerDimension.forEach((dimensionId, pipeline) -> {
			Iris.logger.info("Destroying pipeline {}", dimensionId);
			resetTextureState();
			pipeline.destroy();
		});

		pipelinesPerDimension.clear();
		pipeline = null;
		versionCounterForSodiumShaderReload++;
	}

	private void resetTextureState() {
		// Unbind all textures
		//
		// This is necessary because we don't want destroyed render target textures to remain bound to certain texture
		// units. Vanilla appears to properly rebind all textures as needed, and we do so too, so this does not cause
		// issues elsewhere.
		//
		// Without this code, there will be weird issues when reloading certain shaderpacks.
		for (int i = 0; i < 16; i++) {
            GLStateManager.glActiveTexture(GL13.GL_TEXTURE0 + i);
			GLStateManager.glBindTexture(GL11.GL_TEXTURE_2D, 0);
		}

		// Set the active texture unit to unit 0
		//
		// This seems to be what most code expects. It's a sane default in any case.
        GLStateManager.glActiveTexture(GL13.GL_TEXTURE0);
	}
}
