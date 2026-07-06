package net.coderbot.iris.shaderpack;

import com.gtnewhorizons.angelica.glsm.RenderSystem;

import java.util.List;

/**
 * Shader pack workarounds for specific GLSL versions. These are applied before preprocessing.
 */
public class ShaderPackWorkarounds {

    private record Workaround(int maxVersion, String find, String replace) {
        boolean appliesTo(int glslVersion) {
            return glslVersion <= maxVersion;
        }
    }

    private static final List<Workaround> WORKAROUNDS = List.of(
        // Complementary: Catmull-Rom TAA filter causes "creeping black fog" on OGL 2.1
        new Workaround(120,
            "#define TAA_MOVEMENT_IMPROVEMENT_FILTER",
            "// #define TAA_MOVEMENT_IMPROVEMENT_FILTER // disabled: OGL 2.1 workaround"
        )
    );

    public static String apply(String source) {
        int glslVersion = RenderSystem.getMaxGlslVersion();

        for (Workaround w : WORKAROUNDS) {
            if (w.appliesTo(glslVersion)) {
                source = source.replace(w.find, w.replace);
            }
        }

        return source;
    }
}
