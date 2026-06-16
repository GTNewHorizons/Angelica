package net.coderbot.iris.pipeline.transform;

import net.coderbot.iris.shaderpack.ProgramSource;
import net.coderbot.iris.shaderpack.loading.ProgramId;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * For some reason not all shaders read from the gl_MultiCoord0 to scroll the enchant glint? Maybe a modern thing but
 * I've found a few shaders that just look wrong without it. Technically not in spec, but it looks better to do this.
 */
public final class GlintScrollInjector {

    private static final Pattern MULTI_TEX_COORD0 = Pattern.compile("\\bgl_MultiTexCoord0\\b");
    private static final String SCROLLED = Matcher.quoteReplacement("(gl_TextureMatrix[0] * gl_MultiTexCoord0)");

    private GlintScrollInjector() {}

    public static boolean shouldInject(ProgramId id, ProgramSource source) {
        return id == ProgramId.ArmorGlint
            && source != null
            && ProgramId.ArmorGlint.getSourceName().equals(source.getName());
    }

    public static String apply(String vertexSource) {
        if (vertexSource == null) return null;
        if (vertexSource.contains("gl_TextureMatrix[0]")) return vertexSource;
        if (!vertexSource.contains("gl_MultiTexCoord0")) return vertexSource;
        return MULTI_TEX_COORD0.matcher(vertexSource).replaceAll(SCROLLED);
    }
}
