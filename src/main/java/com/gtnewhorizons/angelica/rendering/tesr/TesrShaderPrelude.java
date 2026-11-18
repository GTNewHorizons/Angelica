package com.gtnewhorizons.angelica.rendering.tesr;

import com.gtnewhorizon.gtnhlib.client.renderer.vertex.VertexFormatElement.Usage;
import com.gtnewhorizons.angelica.glsm.ffp.FfpGlsl;
import com.gtnewhorizons.angelica.glsm.ffp.FragmentKey;
import com.gtnewhorizons.angelica.glsm.ffp.InstancedGlslHelpers;
import com.gtnewhorizons.angelica.glsm.ffp.Instancing;
import com.gtnewhorizons.angelica.glsm.states.FogState;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

final class TesrShaderPrelude {

    static final String LAYER_SAMPLER = "angelica_LayerTexture";
    static final String LIGHTMAP_SAMPLER = "angelica_LightmapTexture";

    private static final Logger LOG = LogManager.getLogger("AngelicaTesrShaders");
    private static final String VERSION_DIRECTIVE = "#version";
    private static final ObjectOpenHashSet<String> WARNED_VERSION = new ObjectOpenHashSet<>();

    private static final String VERTEX_BAKED = buildVertex(false);
    private static final String VERTEX_INSTANCED = buildVertex(true);
    private static final String FRAGMENT = buildFragment();

    private TesrShaderPrelude() {}

    static String vertex(boolean instanced) {
        return instanced ? VERTEX_INSTANCED : VERTEX_BAKED;
    }

    static String fragment() {
        return FRAGMENT;
    }

    private static String buildVertex(boolean instanced) {
        final StringBuilder sb = new StringBuilder(2048);
        sb.append("#version 330 core\n\n");
        sb.append("#define angelica_out out\n");
        sb.append("invariant gl_Position;\n\n");

        sb.append("// Vertex attributes\n");
        attribute(sb, Usage.POSITION, "vec3", "angelica_Position");
        attribute(sb, Usage.COLOR, "vec4", "angelica_VertexColor");
        attribute(sb, Usage.PRIMARY_UV, "vec2", "angelica_TexCoord");
        attribute(sb, Usage.NORMAL, "vec3", "angelica_Normal");
        if (instanced) {
            for (String d : InstancedGlslHelpers.attributeDecls("angelica_", Instancing.TEMPLATE)) {
                sb.append(d).append('\n');
            }
        } else {
            attribute(sb, Usage.SECONDARY_UV, "vec2", "angelica_VertexLightmap");
        }
        sb.append('\n');

        sb.append("// Uniforms\n");
        if (instanced) {
            sb.append("uniform mat4 iris_TextureMatrix;\n");
        } else {
            sb.append("uniform mat4 angelica_ModelViewMatrix;\n");
            sb.append("uniform mat4 angelica_ModelViewProjectionMatrix;\n");
            sb.append("uniform mat3 angelica_NormalMatrix;\n");
        }
        sb.append("uniform mat4 angelica_ProjectionMatrix;\n");
        sb.append("uniform mat4 angelica_LightmapTextureMatrix;\n");
        sb.append("uniform int angelica_FogDistanceMode;\n");
        sb.append("struct angelica_LightSourceParameters { vec4 diffuse; vec4 position; };\n");
        sb.append("uniform angelica_LightSourceParameters angelica_LightSource[2];\n\n");

        sb.append("// Accessors\n");
        if (instanced) {
            sb.append("mat4 angelica_modelView() { return ")
                .append(InstancedGlslHelpers.mat4FromRows("angelica_InstRow0", "angelica_InstRow1", "angelica_InstRow2")).append("; }\n");
            sb.append("vec4 angelica_eyePosition() { return angelica_modelView() * vec4(angelica_Position, 1.0); }\n");
            sb.append("vec4 angelica_clipPosition() { return angelica_ProjectionMatrix * angelica_eyePosition(); }\n");
            sb.append("vec3 angelica_eyeNormal() { return normalize(mat3(angelica_modelView()) * angelica_Normal); }\n");
            sb.append("vec4 angelica_color() { return angelica_VertexColor * angelica_InstColor; }\n");
            sb.append("vec2 angelica_uv() { return (iris_TextureMatrix * vec4(angelica_TexCoord, 0.0, 1.0)).xy; }\n");
            sb.append("vec2 angelica_lightmapUv() { return (angelica_LightmapTextureMatrix * vec4(angelica_InstLightmap, 0.0, 1.0)).xy; }\n");
        } else {
            sb.append("mat4 angelica_modelView() { return angelica_ModelViewMatrix; }\n");
            sb.append("vec4 angelica_eyePosition() { return angelica_ModelViewMatrix * vec4(angelica_Position, 1.0); }\n");
            sb.append("vec4 angelica_clipPosition() { return angelica_ModelViewProjectionMatrix * vec4(angelica_Position, 1.0); }\n");
            sb.append("vec3 angelica_eyeNormal() { return normalize(angelica_NormalMatrix * angelica_Normal); }\n");
            sb.append("vec4 angelica_color() { return angelica_VertexColor; }\n");
            sb.append("vec2 angelica_uv() { return angelica_TexCoord; }\n");
            sb.append("vec2 angelica_lightmapUv() { return (angelica_LightmapTextureMatrix * vec4(angelica_VertexLightmap, 0.0, 1.0)).xy; }\n");
        }
        sb.append("float angelica_fogCoord() {\n");
        sb.append("  vec4 eye = angelica_eyePosition();\n");
        for (final int mode : new int[] { FogState.DISTANCE_EYE_RADIAL, FogState.DISTANCE_EYE_PLANE }) {
            sb.append("  if (angelica_FogDistanceMode == ").append(mode).append(") return ").append(FfpGlsl.fogDistance(mode, "eye")).append(";\n");
        }
        sb.append("  return ").append(FfpGlsl.fogDistance(FogState.DISTANCE_EYE_PLANE_ABS, "eye")).append(";\n");
        sb.append("}\n");
        sb.append("vec3 angelica_lightDirection(int i) { return angelica_LightSource[i].position.xyz; }\n");
        sb.append("vec3 angelica_lightDiffuse(int i) { return angelica_LightSource[i].diffuse.rgb; }\n\n");
        return sb.toString();
    }

    private static String buildFragment() {
        final StringBuilder sb = new StringBuilder(1024);
        sb.append("#version 330 core\n\n");
        sb.append("#define angelica_in in\n\n");

        sb.append("// Outputs\n");
        sb.append("layout(location = 0) out vec4 angelica_FragColor;\n\n");

        sb.append("// Uniforms\n");
        sb.append("uniform sampler2D ").append(LAYER_SAMPLER).append(";\n");
        sb.append("uniform sampler2D ").append(LIGHTMAP_SAMPLER).append(";\n");
        sb.append("uniform int angelica_FogMode;\n");
        sb.append("uniform vec4 angelica_FogParams;\n");
        sb.append("uniform vec4 angelica_FogColor;\n");
        sb.append("uniform float angelica_currentAlphaTest;\n\n");

        sb.append("// Helpers\n");
        sb.append("vec4 angelica_sampleTexture(vec2 uv) { return texture(").append(LAYER_SAMPLER).append(", uv); }\n");
        sb.append("vec4 angelica_sampleLightmap(vec2 uv) { return texture(").append(LIGHTMAP_SAMPLER).append(", uv); }\n");
        sb.append("vec3 angelica_fog(vec3 color, float fogCoord) {\n");
        sb.append("  float f;\n");
        fogBranch(sb, "if", FragmentKey.FOG_LINEAR);
        fogBranch(sb, "} else if", FragmentKey.FOG_EXP);
        fogBranch(sb, "} else if", FragmentKey.FOG_EXP2);
        sb.append("  } else {\n");
        sb.append("    return color;\n");
        sb.append("  }\n");
        sb.append("  return mix(angelica_FogColor.rgb, color, clamp(f, 0.0, 1.0));\n");
        sb.append("}\n");
        sb.append("bool angelica_alphaTest(float a) { return ").append(FfpGlsl.quantizeAlpha("a"))
            .append(" > angelica_currentAlphaTest * 255.0; }\n\n");
        return sb.toString();
    }

    private static void fogBranch(StringBuilder sb, String keyword, int fogMode) {
        sb.append("  ").append(keyword).append(" (angelica_FogMode == ").append(fogMode).append(") {\n");
        FfpGlsl.appendFogFactor(sb, "    ", fogMode, "fogCoord", "angelica_FogParams");
    }

    static String stripVersion(String shaderName, String body) {
        final int start = skipCommentsAndWhitespace(body);
        if (!body.startsWith(VERSION_DIRECTIVE, start)) return body;
        if (WARNED_VERSION.add(shaderName)) {
            LOG.warn("TESR shader {} declares #version; Angelica supplies it, the line is ignored", shaderName);
        }
        final int end = body.indexOf('\n', start);
        return end < 0 ? "" : body.substring(end + 1);
    }

    private static int skipCommentsAndWhitespace(String s) {
        int i = 0;
        while (i < s.length()) {
            if (Character.isWhitespace(s.charAt(i))) {
                i++;
            } else if (s.startsWith("//", i)) {
                final int eol = s.indexOf('\n', i);
                i = eol < 0 ? s.length() : eol + 1;
            } else if (s.startsWith("/*", i)) {
                final int close = s.indexOf("*/", i + 2);
                i = close < 0 ? s.length() : close + 2;
            } else {
                break;
            }
        }
        return i;
    }

    private static void attribute(StringBuilder sb, Usage usage, String type, String name) {
        sb.append("layout(location = ").append(usage.getAttributeLocation()).append(") in ").append(type).append(' ').append(name).append(";\n");
    }
}
