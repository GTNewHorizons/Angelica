package com.gtnewhorizons.angelica.glsm.ffp;

import org.lwjgl.opengl.GL11;

/**
 * Generates GLSL 330 core fragment shaders for FFP emulation.
 * Follows Mesa's ff_fragment_shader.c and st_nir_lower_fog.c.
 */
public final class FragmentShaderGenerator {

    private FragmentShaderGenerator() {}

    public static String generate(FragmentKey key) {
        final StringBuilder sb = new StringBuilder(1024);
        sb.append("#version 330 core\n\n");

        emitInputs(sb, key);
        emitUniforms(sb, key);
        sb.append("out vec4 fragColor;\n\n");

        sb.append("void main() {\n");
        emitTextureSampling(sb, key);
        emitColorCombine(sb, key);
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
            sb.append("in vec2 v_TexCoord0;\n");
        }
        if (key.lightmapEnabled()) {
            sb.append("in vec2 v_TexCoord1;\n");
        }
        if (key.fogMode() != FragmentKey.FOG_NONE) {
            sb.append("in float v_FogCoord;\n");
        }
        sb.append('\n');
    }

    private static void emitUniforms(StringBuilder sb, FragmentKey key) {
        if (key.textureEnabled()) {
            sb.append("uniform sampler2D u_Sampler0;\n");
        }
        if (key.lightmapEnabled()) {
            sb.append("uniform sampler2D u_Sampler1;\n");
        }
        if (key.alphaTestEnabled()) {
            sb.append("uniform float u_AlphaRef;\n");
        }
        if (key.textureEnabled() && key.texEnvMode() == FragmentKey.TEX_ENV_BLEND) {
            sb.append("uniform vec4 u_TexEnvColor;\n");
        }
        if (key.fogMode() != FragmentKey.FOG_NONE) {
            sb.append("uniform vec4 u_FogParams;\n");
            sb.append("uniform vec4 u_FogColor;\n");
        }
        sb.append('\n');
    }

    private static void emitTextureSampling(StringBuilder sb, FragmentKey key) {
        if (key.textureEnabled()) {
            sb.append("  vec4 texColor = texture(u_Sampler0, v_TexCoord0);\n");
        }
        if (key.lightmapEnabled()) {
            sb.append("  vec4 lightmapColor = texture(u_Sampler1, v_TexCoord1);\n");
        }
    }

    private static void emitColorCombine(StringBuilder sb, FragmentKey key) {
        if (!key.textureEnabled()) {
            sb.append("  // No texture — vertex color only\n");
            sb.append("  vec4 color = v_Color;\n");
        } else {
            final int envMode = key.texEnvMode();
            switch (envMode) {
                case FragmentKey.TEX_ENV_REPLACE -> {
                    sb.append("  // Color combine (REPLACE)\n");
                    sb.append("  vec4 color = texColor;\n");
                }
                case FragmentKey.TEX_ENV_ADD -> {
                    sb.append("  // Color combine (ADD)\n");
                    sb.append("  vec4 color = vec4(clamp(v_Color.rgb + texColor.rgb, 0.0, 1.0), v_Color.a * texColor.a);\n");
                }
                case FragmentKey.TEX_ENV_DECAL -> {
                    sb.append("  // Color combine (DECAL)\n");
                    sb.append("  vec4 color = vec4(mix(v_Color.rgb, texColor.rgb, texColor.a), v_Color.a);\n");
                }
                case FragmentKey.TEX_ENV_BLEND -> {
                    sb.append("  // Color combine (BLEND)\n");
                    sb.append("  vec4 color = vec4(v_Color.rgb * (vec3(1.0) - texColor.rgb) + u_TexEnvColor.rgb * texColor.rgb, v_Color.a * texColor.a);\n");
                }
                default -> {
                    sb.append("  // Color combine (MODULATE)\n");
                    sb.append("  vec4 color = v_Color * texColor;\n");
                }
            }
        }
        if (key.lightmapEnabled()) {
            sb.append("  color *= lightmapColor;\n");
        }
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
