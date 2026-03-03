package com.gtnewhorizons.angelica.glsm.ffp;

import org.lwjgl.opengl.GL11;

/**
 * Generates GLSL 330 core fragment shaders for FFP emulation.
 * Supports per-unit texenv chain including GL_COMBINE mode.
 * Follows Mesa's ff_fragment_shader.c and st_nir_lower_fog.c.
 */
public final class FragmentShaderGenerator {

    private FragmentShaderGenerator() {}

    public static String generate(FragmentKey key) {
        final StringBuilder sb = new StringBuilder(2048);
        sb.append("#version 330 core\n\n");

        emitInputs(sb, key);
        emitUniforms(sb, key);
        sb.append("out vec4 fragColor;\n\n");

        sb.append("void main() {\n");
        emitTextureSampling(sb, key);
        emitTexEnvChain(sb, key);
        emitSpecularAdd(sb, key);
        emitAlphaTest(sb, key);
        emitFog(sb, key);
        sb.append("  fragColor = color;\n");
        sb.append("}\n");

        return sb.toString();
    }

    private static void emitInputs(StringBuilder sb, FragmentKey key) {
        sb.append("// Inputs from vertex shader\n");
        sb.append("in vec4 v_Color;\n");
        if (key.separateSpecular()) {
            sb.append("in vec3 v_SpecularColor;\n");
        }
        if (key.textureEnabled()) {
            sb.append("in vec4 v_TexCoord0;\n");
        }
        if (key.lightmapEnabled()) {
            sb.append("in vec4 v_TexCoord1;\n");
        }
        if (key.fogMode() != FragmentKey.FOG_NONE) {
            sb.append("in float v_FogCoord;\n");
        }
        sb.append('\n');
    }

    private static void emitUniforms(StringBuilder sb, FragmentKey key) {
        // Samplers for each enabled unit
        for (int i = 0; i < key.nrEnabledUnits(); i++) {
            if (key.unitEnabled(i)) {
                sb.append("uniform sampler2D u_Sampler").append(i).append(";\n");
            }
        }
        if (key.alphaTestEnabled()) {
            sb.append("uniform float u_AlphaRef;\n");
        }
        // Per-unit texEnvColor uniforms
        for (int i = 0; i < key.nrEnabledUnits(); i++) {
            if (key.unitEnabled(i) && key.unitNeedsEnvColor(i)) {
                sb.append("uniform vec4 u_TexEnvColor").append(i).append(";\n");
            }
        }
        if (key.fogMode() != FragmentKey.FOG_NONE) {
            sb.append("uniform vec4 u_FogParams;\n");
            sb.append("uniform vec4 u_FogColor;\n");
        }
        sb.append('\n');
    }

    private static void emitTextureSampling(StringBuilder sb, FragmentKey key) {
        for (int i = 0; i < key.nrEnabledUnits(); i++) {
            if (!key.unitEnabled(i)) continue;
            // Unit 1 is the lightmap — uses v_TexCoord1.
            // Units 2-3 intentionally share unit 0's texture coordinates since
            // the vertex shader only provides 2 tex coord varyings (unit 0 and unit 1/lightmap).
            final String texCoord = (i == 1) ? "v_TexCoord1.st" : "v_TexCoord0.st";
            sb.append("  vec4 tex").append(i).append("Color = texture(u_Sampler").append(i).append(", ").append(texCoord).append(");\n");
        }
    }

    private static void emitTexEnvChain(StringBuilder sb, FragmentKey key) {
        if (!key.textureEnabled()) {
            sb.append("  // No texture — vertex color only\n");
            sb.append("  vec4 color = v_Color;\n");
            return;
        }

        // Process units in order; "previous" starts as vertex color
        boolean firstUnit = true;
        for (int i = 0; i < key.nrEnabledUnits(); i++) {
            if (!key.unitEnabled(i)) continue;

            final String texVar = "tex" + i + "Color";
            final String envColorVar = "u_TexEnvColor" + i;
            final String prevVar = firstUnit ? "v_Color" : "color";
            final String assign = firstUnit ? "  vec4 color = " : "  color = ";

            if (key.unitMode(i) == FragmentKey.TEX_ENV_COMBINE) {
                emitCombineUnit(sb, key, i, texVar, envColorVar, prevVar, assign);
            } else {
                emitSimpleUnit(sb, key, i, texVar, envColorVar, prevVar, assign);
            }
            firstUnit = false;
        }
    }

    private static void emitSimpleUnit(StringBuilder sb, FragmentKey key, int unit, String texVar, String envColorVar, String prevVar, String assign) {
        switch (key.unitMode(unit)) {
            case FragmentKey.TEX_ENV_REPLACE -> {
                sb.append("  // Unit ").append(unit).append(" (REPLACE)\n");
                sb.append(assign).append(texVar).append(";\n");
            }
            case FragmentKey.TEX_ENV_ADD -> {
                sb.append("  // Unit ").append(unit).append(" (ADD)\n");
                sb.append(assign).append("vec4(clamp(").append(prevVar).append(".rgb + ").append(texVar).append(".rgb, 0.0, 1.0), ")
                    .append(prevVar).append(".a * ").append(texVar).append(".a);\n");
            }
            case FragmentKey.TEX_ENV_DECAL -> {
                sb.append("  // Unit ").append(unit).append(" (DECAL)\n");
                sb.append(assign).append("vec4(mix(").append(prevVar).append(".rgb, ").append(texVar).append(".rgb, ").append(texVar).append(".a), ")
                    .append(prevVar).append(".a);\n");
            }
            case FragmentKey.TEX_ENV_BLEND -> {
                sb.append("  // Unit ").append(unit).append(" (BLEND)\n");
                sb.append(assign).append("vec4(").append(prevVar).append(".rgb * (vec3(1.0) - ").append(texVar).append(".rgb) + ")
                    .append(envColorVar).append(".rgb * ").append(texVar).append(".rgb, ")
                    .append(prevVar).append(".a * ").append(texVar).append(".a);\n");
            }
            default -> { // MODULATE
                sb.append("  // Unit ").append(unit).append(" (MODULATE)\n");
                sb.append(assign).append(prevVar).append(" * ").append(texVar).append(";\n");
            }
        }
    }

    /** Emit a GL_COMBINE texenv unit */
    private static void emitCombineUnit(StringBuilder sb, FragmentKey key, int unit, String texVar, String envColorVar, String prevVar, String assign) {
        sb.append("  // Unit ").append(unit).append(" (COMBINE)\n");

        final String rgbResult = "combRgb" + unit;
        final String alphaResult = "combAlpha" + unit;

        if (key.unitCombineRgb(unit) == FragmentKey.COMBINE_DOT3_RGBA) {
            sb.append("  vec3 ").append(rgbResult).append(";\n");
            sb.append("  {\n");
            emitCombineChannel(sb, key, unit, texVar, envColorVar, prevVar, true);
            sb.append("  }\n");
            sb.append(assign).append("vec4(").append(rgbResult).append(", ").append(rgbResult).append(".x);\n");
        } else {
            sb.append("  vec3 ").append(rgbResult).append(";\n");
            sb.append("  float ").append(alphaResult).append(";\n");
            sb.append("  {\n");
            emitCombineChannel(sb, key, unit, texVar, envColorVar, prevVar, true);
            emitCombineChannel(sb, key, unit, texVar, envColorVar, prevVar, false);
            sb.append("  }\n");
            sb.append(assign).append("vec4(").append(rgbResult).append(", ").append(alphaResult).append(");\n");
        }
    }

    /**
     * Emit one channel (RGB or Alpha) of a GL_COMBINE unit.
     */
    private static void emitCombineChannel(StringBuilder sb, FragmentKey key, int unit, String texVar, String envColorVar, String prevVar, boolean isRgb) {
        final String suffix = isRgb ? "Rgb" : "Alpha";
        final String type = isRgb ? "vec3" : "float";
        final int combineFunc = isRgb ? key.unitCombineRgb(unit) : key.unitCombineAlpha(unit);
        final int scaleShift = isRgb ? key.unitScaleShiftRgb(unit) : key.unitScaleShiftAlpha(unit);

        // Guard: DOT3 is invalid for alpha channel — fallback to REPLACE
        final int effectiveFunc;
        if (!isRgb && (combineFunc == FragmentKey.COMBINE_DOT3_RGB || combineFunc == FragmentKey.COMBINE_DOT3_RGBA)) {
            effectiveFunc = FragmentKey.COMBINE_REPLACE;
        } else {
            effectiveFunc = combineFunc;
        }

        // Determine how many args we need
        final int numArgs = combineNumArgs(effectiveFunc);

        // Emit argument resolution
        for (int a = 0; a < numArgs; a++) {
            final int source = isRgb ? key.unitSourceRgb(unit, a) : key.unitSourceAlpha(unit, a);
            final int operand = isRgb ? key.unitOperandRgb(unit, a) : key.unitOperandAlpha(unit, a);
            final String argName = "arg" + suffix + a + "_" + unit;
            final String sourceExpr = resolveSource(source, texVar, envColorVar, prevVar);
            final String operandExpr = applyOperand(sourceExpr, operand, isRgb);
            sb.append("    ").append(type).append(" ").append(argName).append(" = ").append(operandExpr).append(";\n");
        }

        // Emit combine operation
        final String resultName = "comb" + suffix + unit;
        final String a0 = "arg" + suffix + "0_" + unit;
        final String a1 = "arg" + suffix + "1_" + unit;
        final String a2 = "arg" + suffix + "2_" + unit;

        sb.append("    ").append(resultName).append(" = ");
        switch (effectiveFunc) {
            case FragmentKey.COMBINE_REPLACE -> sb.append(a0);
            case FragmentKey.COMBINE_MODULATE -> sb.append(a0).append(" * ").append(a1);
            case FragmentKey.COMBINE_ADD -> sb.append(a0).append(" + ").append(a1);
            case FragmentKey.COMBINE_ADD_SIGNED ->
                sb.append(a0).append(" + ").append(a1).append(" - ").append(isRgb ? "vec3(0.5)" : "0.5");
            case FragmentKey.COMBINE_SUBTRACT -> sb.append(a0).append(" - ").append(a1);
            case FragmentKey.COMBINE_INTERPOLATE ->
                sb.append("mix(").append(a1).append(", ").append(a0).append(", ").append(a2).append(")");
            case FragmentKey.COMBINE_DOT3_RGB, FragmentKey.COMBINE_DOT3_RGBA ->
                sb.append("vec3(4.0 * dot(").append(a0).append(" - vec3(0.5), ").append(a1).append(" - vec3(0.5)))");
            default -> sb.append(a0); // fallback to REPLACE
        }
        sb.append(";\n");

        // Apply scale
        if (scaleShift > 0) {
            final String scaleFactor = scaleShift == 1 ? "2.0" : "4.0";
            sb.append("    ").append(resultName).append(" *= ").append(scaleFactor).append(";\n");
        }

        // Clamp
        sb.append("    ").append(resultName).append(" = clamp(").append(resultName).append(", ")
            .append(isRgb ? "vec3(0.0), vec3(1.0)" : "0.0, 1.0").append(");\n");
    }

    private static String resolveSource(int source, String texVar, String envColorVar, String prevVar) {
        return switch (source) {
            case FragmentKey.SRC_TEXTURE -> texVar;
            case FragmentKey.SRC_CONSTANT -> envColorVar;
            case FragmentKey.SRC_PRIMARY_COLOR -> "v_Color";
            case FragmentKey.SRC_PREVIOUS -> prevVar;
            default -> prevVar;
        };
    }

    private static String applyOperand(String sourceExpr, int operand, boolean isRgb) {
        if (isRgb) {
            return switch (operand) {
                case FragmentKey.OP_SRC_COLOR -> sourceExpr + ".rgb";
                case FragmentKey.OP_ONE_MINUS_SRC_COLOR -> "(vec3(1.0) - " + sourceExpr + ".rgb)";
                case FragmentKey.OP_SRC_ALPHA -> "vec3(" + sourceExpr + ".a)";
                case FragmentKey.OP_ONE_MINUS_SRC_ALPHA -> "vec3(1.0 - " + sourceExpr + ".a)";
                default -> sourceExpr + ".rgb";
            };
        } else {
            return switch (operand) {
                case FragmentKey.OP_SRC_ALPHA -> sourceExpr + ".a";
                case FragmentKey.OP_ONE_MINUS_SRC_ALPHA -> "(1.0 - " + sourceExpr + ".a)";
                // Per GL spec, alpha channel operands are SRC_ALPHA or ONE_MINUS_SRC_ALPHA only, but handle color operands gracefully
                case FragmentKey.OP_SRC_COLOR -> sourceExpr + ".a";
                case FragmentKey.OP_ONE_MINUS_SRC_COLOR -> "(1.0 - " + sourceExpr + ".a)";
                default -> sourceExpr + ".a";
            };
        }
    }

    private static int combineNumArgs(int func) {
        return FragmentKey.combineNumArgs(func);
    }

    private static void emitSpecularAdd(StringBuilder sb, FragmentKey key) {
        if (key.separateSpecular()) {
            sb.append("  // Add separate specular\n");
            sb.append("  color.rgb += v_SpecularColor;\n");
        }
    }

    private static void emitAlphaTest(StringBuilder sb, FragmentKey key) {
        if (!key.alphaTestEnabled()) return;

        sb.append("  // Alpha test\n");
        final int func = FragmentKey.decodeAlphaFunc(key.alphaTestFunc());

        switch (func) {
            case GL11.GL_NEVER ->
                sb.append("  discard;\n");
            case GL11.GL_LESS ->
                sb.append("  if (color.a >= u_AlphaRef) discard;\n");
            case GL11.GL_EQUAL ->
                sb.append("  if (color.a != u_AlphaRef) discard;\n");
            case GL11.GL_LEQUAL ->
                sb.append("  if (color.a > u_AlphaRef) discard;\n");
            case GL11.GL_GREATER ->
                sb.append("  if (color.a <= u_AlphaRef) discard;\n");
            case GL11.GL_NOTEQUAL ->
                sb.append("  if (color.a == u_AlphaRef) discard;\n");
            case GL11.GL_GEQUAL ->
                sb.append("  if (color.a < u_AlphaRef) discard;\n");
            case GL11.GL_ALWAYS -> {}
        }
    }

    private static void emitFog(StringBuilder sb, FragmentKey key) {
        final int fogMode = key.fogMode();
        if (fogMode == FragmentKey.FOG_NONE) return;

        sb.append("  // Fog\n");
        sb.append("  float f;\n");

        switch (fogMode) {
            case FragmentKey.FOG_LINEAR ->
                sb.append("  f = v_FogCoord * u_FogParams.x + u_FogParams.y;\n");
            case FragmentKey.FOG_EXP ->
                sb.append("  f = exp2(-(v_FogCoord * u_FogParams.z));\n");
            case FragmentKey.FOG_EXP2 -> {
                sb.append("  float fogTmp = v_FogCoord * u_FogParams.w;\n");
                sb.append("  f = exp2(-(fogTmp * fogTmp));\n");
            }
        }

        sb.append("  f = clamp(f, 0.0, 1.0);\n");
        sb.append("  color.rgb = mix(u_FogColor.rgb, color.rgb, f);\n");
    }
}
