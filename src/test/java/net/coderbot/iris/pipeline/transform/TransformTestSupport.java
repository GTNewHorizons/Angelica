package net.coderbot.iris.pipeline.transform;

import com.gtnewhorizons.angelica.glsm.RenderSystem;
import com.gtnewhorizons.angelica.glsm.testutil.Reflect;
import net.coderbot.iris.gbuffer_overrides.matching.InputAvailability;
import org.taumc.glsl.ShaderParser;
import org.taumc.glsl.ShaderPrinter;
import org.taumc.glsl.Transformer;

public final class TransformTestSupport {

    public static final InputAvailability TEX_LM = InputAvailability.of(true, true);

    private TransformTestSupport() {}

    /** Returns the previous value for {@link #restoreGlslVersion}. */
    public static int pinGlslVersion(int version) {
        final int previous = Reflect.getStatic(RenderSystem.class, "maxGlslVersion");
        Reflect.setStatic(RenderSystem.class, "maxGlslVersion", version);
        ShaderTransformer.init();
        return previous;
    }

    public static void restoreGlslVersion(int previous) {
        Reflect.setStatic(RenderSystem.class, "maxGlslVersion", previous);
        clearCaches();
        ShaderTransformer.init();
    }

    public static void clearCaches() {
        TransformPatcher.clearCache();
        ShaderTransformer.clearCache();
    }

    public static Transformer parse(String src) {
        return new Transformer(ShaderParser.parseShader(src).full());
    }

    public static String format(Transformer t) {
        final StringBuilder sb = new StringBuilder();
        t.mutateTree(tree -> sb.append(ShaderPrinter.getFormattedShader(tree)));
        return sb.toString();
    }

    public static int occurrences(String haystack, String needle) {
        int n = 0, i = 0;
        while ((i = haystack.indexOf(needle, i)) != -1) { n++; i += needle.length(); }
        return n;
    }

    /** Whitespace runs to one space, trimmed. */
    public static String norm(String s) {
        return s.replaceAll("\\s+", " ").trim();
    }

    /** All whitespace removed. */
    public static String squash(String s) {
        return s.replaceAll("\\s+", "");
    }
}
