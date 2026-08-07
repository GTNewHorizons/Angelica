package net.coderbot.iris.uniforms;

import com.gtnewhorizons.angelica.glsm.backend.BackendManager;
import com.gtnewhorizons.angelica.glsm.hooks.GLSMHooks;
import com.gtnewhorizons.angelica.glsm.hooks.PerFrameUniformBlock;
import com.gtnewhorizons.angelica.glsm.hooks.PerFrameUniformBlock.Member;
import net.coderbot.iris.Iris;
import net.coderbot.iris.gl.state.FogMode;
import net.coderbot.iris.gl.uniform.ManifestCollectingHolder;
import net.coderbot.iris.gl.uniform.UniformManifest;
import net.coderbot.iris.shaderpack.ProgramSet;
import net.coderbot.iris.uniforms.builtin.BuiltinReplacementUniforms;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class PerFrameUniformBlockHarvester {

    private PerFrameUniformBlockHarvester() {}

    public static void clear() {
        GLSMHooks.perFrameUniformBlock = null;
        GLSMHooks.perPassUniformBlock = null;
    }

    public static void harvest(ProgramSet programs) {
        if (!BackendManager.RENDER_BACKEND.isSDLGPU()) {
            clear();
            return;
        }
        try {
            final UniformManifest primary = collect(programs, FogMode.PER_VERTEX);
            final UniformManifest secondary = collect(programs, FogMode.OFF);
            GLSMHooks.perFrameUniformBlock = toBlock(primary, secondary);
            GLSMHooks.perPassUniformBlock = toPerPassBlock(primary, secondary);
        } catch (Exception e) {
            Iris.logger.error("Failed to harvest the shared uniform blocks; falling back to loose uniforms", e);
            clear();
        }
    }

    /**
     * Declared PER_FRAME but not frame-global
     */
    private static final Set<String> LIVE_STATE_UNIFORMS = Set.of(
        "gbufferModelView", "gbufferModelViewInverse", "gbufferPreviousModelView",
        "gbufferProjection", "gbufferProjectionInverse", "gbufferPreviousProjection",
        "fogColor", "iris_FogColor", "iris_FogStart", "iris_FogEnd", "iris_FogDensity");

    /**
     * @param primary   manifest whose entry order defines the block layout
     * @param secondary same declarations under a different fog mode, used only to widen the exclusions
     */
    static PerFrameUniformBlock toBlock(UniformManifest primary, UniformManifest secondary) {
        return collectMembers(primary, secondary, false);
    }

    static PerFrameUniformBlock toPerPassBlock(UniformManifest primary, UniformManifest secondary) {
        return collectMembers(primary, secondary, true);
    }

    private static PerFrameUniformBlock collectMembers(UniformManifest primary, UniformManifest secondary, boolean liveState) {
        final Set<String> excluded = new HashSet<>();
        excluded.addAll(primary.dynamicNames());
        excluded.addAll(secondary.dynamicNames());
        excluded.addAll(primary.externallyManagedNames());
        excluded.addAll(secondary.externallyManagedNames());

        final List<Member> members = new ArrayList<>();
        for (UniformManifest.Entry e : primary.entries()) {
            if (LIVE_STATE_UNIFORMS.contains(e.name()) != liveState) continue;
            if (excluded.contains(e.name())) continue;
            if (e.type() == null) continue;
            members.add(new Member(e.name(), e.type()));
        }
        return new PerFrameUniformBlock(List.copyOf(members));
    }

    private static UniformManifest collect(ProgramSet programs, FogMode fogMode) {
        final ManifestCollectingHolder holder = new ManifestCollectingHolder();
        final FrameUpdateNotifier notifier = new FrameUpdateNotifier();
        CommonUniforms.addNonDynamicUniforms(holder, programs.getPack().getIdMap(), programs.getPackDirectives(), notifier);
        CommonUniforms.addDynamicUniforms(holder, fogMode);
        BuiltinReplacementUniforms.addBuiltinReplacementUniforms(holder);
        return holder.build();
    }

}
