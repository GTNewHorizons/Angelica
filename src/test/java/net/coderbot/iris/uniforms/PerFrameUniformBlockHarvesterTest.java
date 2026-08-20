package net.coderbot.iris.uniforms;

import com.gtnewhorizons.angelica.glsm.backend.BackendManager;
import com.gtnewhorizons.angelica.glsm.hooks.GLSMHooks;
import com.gtnewhorizons.angelica.glsm.hooks.PerFrameUniformBlock;
import net.coderbot.iris.gl.uniform.ManifestCollectingHolder;
import net.coderbot.iris.gl.uniform.UniformManifest;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.joml.Vector3f;
import org.joml.Vector3fc;
import org.junit.jupiter.api.Test;

import java.util.List;

import static net.coderbot.iris.gl.uniform.UniformUpdateFrequency.PER_FRAME;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PerFrameUniformBlockHarvesterTest {

    private static UniformManifest manifest() {
        final ManifestCollectingHolder h = new ManifestCollectingHolder();
        final Vector3fc vec = new Vector3f();
        final Matrix4fc mat = new Matrix4f();
        h.uniform3f(PER_FRAME, "cameraPosition", () -> vec)
            .uniform3f(PER_FRAME, "fogColor", () -> vec)
            .uniform1f(PER_FRAME, "iris_FogStart", () -> 0.0f)
            .uniform1f(PER_FRAME, "iris_FogEnd", () -> 0.0f)
            .uniform1f(PER_FRAME, "iris_FogDensity", () -> 0.0f)
            .uniformMatrix(PER_FRAME, "gbufferModelView", () -> mat)
            .uniformMatrix(PER_FRAME, "gbufferModelViewInverse", () -> mat)
            .uniformMatrix(PER_FRAME, "gbufferProjection", () -> mat)
            .uniformMatrix(PER_FRAME, "shadowModelView", () -> mat);
        return h.build();
    }

    private static List<String> blockNames() {
        final PerFrameUniformBlock block = PerFrameUniformBlockHarvester.toBlock(manifest(), manifest());
        return block.members().stream().map(PerFrameUniformBlock.Member::name).toList();
    }

    @Test
    void liveStateUniformsAreExcluded() {
        final List<String> names = blockNames();
        for (String excluded : List.of("fogColor", "iris_FogStart", "iris_FogEnd", "iris_FogDensity", "gbufferModelView", "gbufferModelViewInverse", "gbufferProjection")) {
            assertFalse(names.contains(excluded), excluded + " reads live per-draw state and must stay on the push path");
        }
    }

    @Test
    void frameGlobalUniformsAreKept() {
        final List<String> names = blockNames();
        assertTrue(names.contains("cameraPosition"), "camera position is frame-scoped");
        assertTrue(names.contains("shadowModelView"), "shadow matrices are pure functions of pack directives");
    }

    @Test
    void orderIsDeterministic() {
        assertEquals(blockNames(), blockNames());
    }

    private static List<String> perPassNames() {
        final PerFrameUniformBlock block = PerFrameUniformBlockHarvester.toPerPassBlock(manifest(), manifest());
        return block.members().stream().map(PerFrameUniformBlock.Member::name).toList();
    }

    @Test
    void perPassBlockHoldsExactlyTheLiveStateUniforms() {
        assertEquals(List.of("fogColor", "iris_FogStart", "iris_FogEnd", "iris_FogDensity", "gbufferModelView", "gbufferModelViewInverse", "gbufferProjection"), perPassNames());
    }

    @Test
    void blocksAreDisjoint() {
        final List<String> perFrame = blockNames();
        for (String name : perPassNames()) {
            assertFalse(perFrame.contains(name), name + " must live in exactly one block");
        }
    }

    @Test
    void noBlockIsHarvestedOffTheSdlBackend() {
        assertFalse(BackendManager.RENDER_BACKEND.isSDLGPU(), "test backend must not be SDL-GPU for this guard to mean anything");
        GLSMHooks.perFrameUniformBlock = new PerFrameUniformBlock(List.of());
        GLSMHooks.perPassUniformBlock = new PerFrameUniformBlock(List.of());
        PerFrameUniformBlockHarvester.harvest(null);
        assertNull(GLSMHooks.perFrameUniformBlock, "harvest must clear the block on any non-SDL backend");
        assertNull(GLSMHooks.perPassUniformBlock, "harvest must clear the per-pass block on any non-SDL backend");
    }
}
