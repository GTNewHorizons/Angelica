package com.gtnewhorizons.angelica.mixins.early.sdlgpu;

import com.gtnewhorizons.angelica.glsm.GLStateManager;
import com.gtnewhorizons.angelica.glsm.backend.BackendManager;
import com.gtnewhorizons.angelica.rendering.voxelization.SdlShadowVoxelizationSink;
import com.gtnewhorizons.angelica.rendering.voxelization.ShadowVoxelizer;
import net.coderbot.iris.Iris;
import net.coderbot.iris.gl.program.ComputeProgram;
import net.coderbot.iris.pipeline.DeferredWorldRenderingPipeline;
import net.coderbot.iris.pipeline.WorldRenderingPipeline;
import net.coderbot.iris.shadows.ShadowRenderingState;
import org.embeddedt.embeddium.impl.gl.device.CommandList;
import org.embeddedt.embeddium.impl.render.chunk.ChunkRenderMatrices;
import org.embeddedt.embeddium.impl.render.chunk.DefaultChunkRenderer;
import org.embeddedt.embeddium.impl.render.chunk.lists.ChunkRenderListIterable;
import org.embeddedt.embeddium.impl.render.chunk.terrain.TerrainRenderPass;
import org.embeddedt.embeddium.impl.render.viewport.CameraTransform;
import org.apache.logging.log4j.LogManager;
import org.lwjgl.opengl.GL20;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.HashSet;
import java.util.Set;

/** SDL_GPU allows read-write image access only in a compute pass, so the shadow VSH stores are replayed here. */
@Mixin(value = DefaultChunkRenderer.class, remap = false)
public abstract class MixinDefaultChunkRenderer_ShadowVoxelization {

    @Shadow protected abstract boolean useBlockFaceCulling();

    @Unique private static final ShadowVoxelizer angelica$voxelizer = new ShadowVoxelizer();
    @Unique private static final SdlShadowVoxelizationSink angelica$sink = new SdlShadowVoxelizationSink();

    @Unique private static final Set<String> angelica$loggedBails = new HashSet<>();

    @Unique
    private static void angelica$bail(String reason) {
        if (angelica$loggedBails.add(reason)) {
            LogManager.getLogger("Angelica").info("shadow voxelization skipped: {}", reason);
        }
    }

    @Inject(method = "render", at = @At("HEAD"))
    private void angelica$voxelizeShadowTerrain(ChunkRenderMatrices matrices, CommandList commandList, ChunkRenderListIterable renderLists, TerrainRenderPass renderPass, CameraTransform occlusionCamera, CameraTransform camera, CallbackInfo ci) {
        if (!BackendManager.RENDER_BACKEND.isSDLGPU()) return;
        if (!ShadowRenderingState.areShadowsCurrentlyBeingRendered()) return;
        if (!renderLists.hasPass(renderPass)) { angelica$bail("pass has no render lists"); return; }

        final WorldRenderingPipeline pipeline = Iris.getPipelineManager().getPipelineNullable();
        if (!(pipeline instanceof DeferredWorldRenderingPipeline deferred)) { angelica$bail("no deferred pipeline"); return; }
        final ComputeProgram voxelCompute = deferred.getShadowVoxelizationCompute();
        if (voxelCompute == null) { angelica$bail("pack declares no shadow voxelization"); return; }

        final int prevProgram = GLStateManager.glGetInteger(GL20.GL_CURRENT_PROGRAM);
        voxelCompute.use();
        deferred.prepareShadowVoxelizationCompute(matrices.modelView());
        try {
            angelica$voxelizer.walkPass(renderLists, renderPass, renderPass.vertexType().getVertexFormat(), camera, occlusionCamera, useBlockFaceCulling() && !renderPass.isSorted(), angelica$sink);
        } finally {
            if (prevProgram != 0) GLStateManager.glUseProgram(prevProgram);
        }
    }
}
