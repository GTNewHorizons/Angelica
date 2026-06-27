package net.coderbot.iris.pipeline.transform;

import net.coderbot.iris.shaderpack.ProgramSource;
import net.coderbot.iris.shaderpack.loading.ProgramId;

/**
 * For some reason not all shaders read from gl_MultiTexCoord0 through the texture matrix to scroll the enchant glint?
 * Maybe a modern thing but a few shaders just look wrong without it. Technically not in spec, but it looks better.
 */
public final class GlintScrollInjector {

    private GlintScrollInjector() {}

    public static boolean shouldInject(ProgramId id, ProgramSource source) {
        if (id != ProgramId.ArmorGlint || source == null) return false;
        if (!ProgramId.ArmorGlint.getSourceName().equals(source.getName())) return false;

        final String vertex = source.getVertexSource().orElse(null);
        if (vertex == null) return false;
        // Already scrolls through the texture matrix itself
        if (vertex.contains("gl_TextureMatrix[0]")) return false;
        return vertex.contains("gl_MultiTexCoord0");
    }
}
