package com.gtnewhorizons.angelica.glsm.ffp;

import com.gtnewhorizon.gtnhlib.client.renderer.vertex.VertexFormatElement;

/**
 * Generates GLSL 330 core vertex shaders for FFP emulation.
 * Follows Mesa's ffvertex_prog.c build order.
 *
 * Attribute locations are sourced from {@link VertexFormatElement.Usage} to stay in sync with GTNHLib's vertex format setup.
 */
public final class VertexShaderGenerator {

    private VertexShaderGenerator() {}

    public static String generate(VertexKey key) {
        final StringBuilder sb = new StringBuilder(2048);
        sb.append("#version 330 core\n\n");

        emitAttributes(sb, key);
        emitUniforms(sb, key);
        emitOutputs(sb, key);

        sb.append("void main() {\n");
        emitPositionTransform(sb, key);
        if (key.lightingEnabled()) {
            emitNormalTransform(sb, key);
            emitLighting(sb, key);
        } else {
            emitColorPassthrough(sb, key);
        }
        emitTexCoordPassthrough(sb, key);
        if (key.fogEnabled()) {
            emitFogDistance(sb, key);
        }
        if (key.clipPlanesEnabled()) {
            emitClipDistances(sb);
        }
        sb.append("}\n");

        return sb.toString();
    }

    private static void emitAttributes(StringBuilder sb, VertexKey key) {
        sb.append("// Vertex attributes\n");
        sb.append("layout(location = ").append(VertexFormatElement.Usage.POSITION.getAttributeLocation()).append(") in vec3 a_Position;\n");
        if (key.hasVertexColor()) {
            sb.append("layout(location = ").append(VertexFormatElement.Usage.COLOR.getAttributeLocation()).append(") in vec4 a_Color;\n");
        }
        if (key.hasVertexTexCoord()) {
            sb.append("layout(location = ").append(VertexFormatElement.Usage.PRIMARY_UV.getAttributeLocation()).append(") in vec2 a_TexCoord0;\n");
        }
        if (key.hasVertexLightmap()) {
            sb.append("layout(location = ").append(VertexFormatElement.Usage.SECONDARY_UV.getAttributeLocation()).append(") in vec2 a_TexCoord1;\n");
        }
        if (key.hasVertexNormal()) {
            sb.append("layout(location = ").append(VertexFormatElement.Usage.NORMAL.getAttributeLocation()).append(") in vec3 a_Normal;\n");
        }
        sb.append('\n');
    }

    private static void emitUniforms(StringBuilder sb, VertexKey key) {
        sb.append("// Matrices\n");
        sb.append("uniform mat4 u_ModelViewMatrix;\n");
        sb.append("uniform mat4 u_ProjectionMatrix;\n");
        sb.append("uniform mat4 u_MVPMatrix;\n");

        if (key.lightingEnabled()) {
            sb.append("uniform mat3 u_NormalMatrix;\n");
        }

        if (key.textureMatrixEnabled()) {
            sb.append("uniform mat4 u_TextureMatrix0;\n");
        }

        // TexGen plane uniforms
        if (key.texGenModeS() == VertexKey.TG_OBJ_LINEAR) sb.append("uniform vec4 u_TexGenObjPlaneS;\n");
        if (key.texGenModeS() == VertexKey.TG_EYE_LINEAR) sb.append("uniform vec4 u_TexGenEyePlaneS;\n");
        if (key.texGenModeT() == VertexKey.TG_OBJ_LINEAR) sb.append("uniform vec4 u_TexGenObjPlaneT;\n");
        if (key.texGenModeT() == VertexKey.TG_EYE_LINEAR) sb.append("uniform vec4 u_TexGenEyePlaneT;\n");
        if (key.texGenModeR() == VertexKey.TG_OBJ_LINEAR) sb.append("uniform vec4 u_TexGenObjPlaneR;\n");
        if (key.texGenModeR() == VertexKey.TG_EYE_LINEAR) sb.append("uniform vec4 u_TexGenEyePlaneR;\n");
        if (key.texGenModeQ() == VertexKey.TG_OBJ_LINEAR) sb.append("uniform vec4 u_TexGenObjPlaneQ;\n");
        if (key.texGenModeQ() == VertexKey.TG_EYE_LINEAR) sb.append("uniform vec4 u_TexGenEyePlaneQ;\n");

        if (!key.hasVertexNormal() && key.lightingEnabled()) {
            sb.append("uniform vec3 u_CurrentNormal;\n");
        }

        if (!key.hasVertexColor()) {
            sb.append("uniform vec4 u_CurrentColor;\n");
        }

        if (!key.hasVertexTexCoord() && key.textureEnabled()) {
            sb.append("uniform vec4 u_CurrentTexCoord;\n");
        }

        if (key.lightmapEnabled() && !key.hasVertexLightmap()) {
            sb.append("uniform vec2 u_CurrentLightmapCoord;\n");
        }

        if (key.lightmapEnabled()) {
            sb.append("uniform mat4 u_LightmapTextureMatrix;\n");
        }

        if (key.lightingEnabled()) {
            emitLightingUniforms(sb, key);
        }

        if (key.lightingEnabled() && (key.normalizeEnabled() || key.rescaleNormalsEnabled())) {
            // normalize is done in shader; rescale uses normalScale
            if (key.rescaleNormalsEnabled() && !key.normalizeEnabled()) {
                sb.append("uniform float u_NormalScale;\n");
            }
        }

        if (key.clipPlanesEnabled()) {
            sb.append("uniform vec4 u_ClipPlane[8];\n");
        }

        sb.append('\n');
    }

    private static void emitLightingUniforms(StringBuilder sb, VertexKey key) {
        sb.append("\n// Lighting\n");

        if (key.colorMaterialEnabled()) {
            // When color material is active, we can't pre-compute light products so we upload raw light and material values
            sb.append("uniform vec4 u_LightModelAmbient;\n");
            sb.append("uniform vec4 u_MaterialEmission;\n");
            sb.append("uniform vec4 u_MaterialAmbient;\n");
            sb.append("uniform vec4 u_MaterialDiffuse;\n");
            sb.append("uniform vec4 u_MaterialSpecular;\n");
            sb.append("uniform float u_MaterialShininess;\n");

            if (key.light0Enabled()) {
                sb.append("uniform vec4 u_Light0Ambient;\n");
                sb.append("uniform vec4 u_Light0Diffuse;\n");
                sb.append("uniform vec4 u_Light0Specular;\n");
                sb.append("uniform vec4 u_Light0Position;\n");
            }
            if (key.light1Enabled()) {
                sb.append("uniform vec4 u_Light1Ambient;\n");
                sb.append("uniform vec4 u_Light1Diffuse;\n");
                sb.append("uniform vec4 u_Light1Specular;\n");
                sb.append("uniform vec4 u_Light1Position;\n");
            }
        } else {
            // Pre-computed: sceneColor + lightProducts
            sb.append("uniform vec4 u_SceneColor;\n");

            if (key.light0Enabled()) {
                sb.append("uniform vec4 u_Light0Position;\n");
                sb.append("uniform vec3 u_LightProd0Ambient;\n");
                sb.append("uniform vec3 u_LightProd0Diffuse;\n");
                sb.append("uniform vec3 u_LightProd0Specular;\n");
            }
            if (key.light1Enabled()) {
                sb.append("uniform vec4 u_Light1Position;\n");
                sb.append("uniform vec3 u_LightProd1Ambient;\n");
                sb.append("uniform vec3 u_LightProd1Diffuse;\n");
                sb.append("uniform vec3 u_LightProd1Specular;\n");
            }
            sb.append("uniform float u_MaterialShininess;\n");
        }
    }

    private static void emitOutputs(StringBuilder sb, VertexKey key) {
        sb.append("// Outputs\n");
        sb.append("out vec4 v_Color;\n");
        if (key.separateSpecular()) {
            sb.append("out vec3 v_SpecularColor;\n");
        }
        if (key.textureEnabled() || key.hasVertexTexCoord() || key.texGenEnabled()) {
            sb.append("out vec2 v_TexCoord0;\n");
        }
        if (key.lightmapEnabled()) {
            sb.append("out vec2 v_TexCoord1;\n");
        }
        if (key.fogEnabled()) {
            sb.append("out float v_FogCoord;\n");
        }
        sb.append('\n');
    }

    private static void emitPositionTransform(StringBuilder sb, VertexKey key) {
        sb.append("  // Position transform\n");
        sb.append("  vec4 pos4 = vec4(a_Position, 1.0);\n");
        sb.append("  gl_Position = u_MVPMatrix * pos4;\n");

        // Eye position needed for lighting, fog, EYE_LINEAR texgen, and clip planes
        if (key.lightingEnabled() || key.fogEnabled() || texGenNeedsEyePos(key) || key.clipPlanesEnabled()) {
            sb.append("  vec4 eyePos = u_ModelViewMatrix * pos4;\n");
        }
        sb.append('\n');
    }

    private static void emitNormalTransform(StringBuilder sb, VertexKey key) {
        sb.append("  // Normal transform\n");
        if (key.hasVertexNormal()) {
            sb.append("  vec3 normal = u_NormalMatrix * a_Normal;\n");
        } else {
            sb.append("  vec3 normal = u_NormalMatrix * u_CurrentNormal;\n");
        }

        if (key.normalizeEnabled()) {
            sb.append("  normal = normalize(normal);\n");
        } else if (key.rescaleNormalsEnabled()) {
            sb.append("  normal *= u_NormalScale;\n");
        }
        sb.append('\n');
    }

    private static void emitLighting(StringBuilder sb, VertexKey key) {
        sb.append("  // Gouraud lighting\n");

        if (key.colorMaterialEnabled()) {
            // Color material: vertex color replaces specific material properties per glColorMaterial mode (Mesa ffvertex_prog.c get_material() bitmask approach)
            sb.append("  vec4 matColor = ");
            sb.append(key.hasVertexColor() ? "a_Color" : "u_CurrentColor");
            sb.append(";\n");

            // Emission source: vertex color for CM_EMISSION, else material uniform
            final String emSrc = key.cmReplacesEmission() ? "matColor.rgb" : "u_MaterialEmission.rgb";
            // Ambient source for scene color: vertex color for CM_AMBIENT / CM_AMBIENT_AND_DIFFUSE
            final String amSrc = key.cmReplacesAmbient() ? "matColor.rgb" : "u_MaterialAmbient.rgb";
            sb.append("  vec3 sceneColor = ").append(emSrc).append(" + ").append(amSrc).append(" * u_LightModelAmbient.rgb;\n");

            // Alpha from diffuse source (per GL spec)
            sb.append("  float outAlpha = ").append(key.cmReplacesDiffuse() ? "matColor.a" : "u_MaterialDiffuse.a").append(";\n");
        } else {
            // Pre-computed scene color
            sb.append("  vec3 sceneColor = u_SceneColor.rgb;\n");
            sb.append("  float outAlpha = u_SceneColor.a;\n");
        }

        sb.append("  vec3 color0 = sceneColor;\n");
        if (key.separateSpecular()) {
            sb.append("  vec3 specAccum = vec3(0.0);\n");
        }

        // Per-light accumulation
        if (key.light0Enabled()) {
            emitLightContribution(sb, key, 0);
        }
        if (key.light1Enabled()) {
            emitLightContribution(sb, key, 1);
        }

        sb.append("  v_Color = vec4(clamp(color0, 0.0, 1.0), outAlpha);\n");
        if (key.separateSpecular()) {
            sb.append("  v_SpecularColor = clamp(specAccum, 0.0, 1.0);\n");
        }
        sb.append('\n');
    }

    private static void emitLightContribution(StringBuilder sb, VertexKey key, int lightIndex) {
        final String li = String.valueOf(lightIndex);
        final boolean directional = (lightIndex == 0) ? key.light0Directional() : key.light1Directional();
        final String posUniform = "u_Light" + li + "Position";

        sb.append("  { // Light ").append(li).append('\n');

        // Light vector
        if (directional) {
            // Directional: position is already normalized direction
            sb.append("    vec3 VPpli = normalize(").append(posUniform).append(".xyz);\n");
        } else {
            // Positional light
            sb.append("    vec3 VPpli = ").append(posUniform).append(".xyz - eyePos.xyz;\n");
            sb.append("    VPpli = normalize(VPpli);\n");
        }

        // Dot products
        sb.append("    float NdotVP = max(dot(normal, VPpli), 0.0);\n");

        // Half vector for specular (infinite viewer: H = normalize(L + (0,0,1)))
        sb.append("    vec3 halfVec = normalize(VPpli + vec3(0.0, 0.0, 1.0));\n");
        sb.append("    float NdotHV = max(dot(normal, halfVec), 0.0);\n");

        // Specular power
        sb.append("    float spec = (NdotVP > 0.0 && u_MaterialShininess > 0.0) ? pow(NdotHV, u_MaterialShininess) : 0.0;\n");

        // Accumulate — per-property source depends on color material mode
        if (key.colorMaterialEnabled()) {
            final String ambU = "u_Light" + li + "Ambient";
            final String difU = "u_Light" + li + "Diffuse";
            final String speU = "u_Light" + li + "Specular";
            // Ambient product: light.ambient * (vertex color or material)
            final String ambMat = key.cmReplacesAmbient() ? "matColor.rgb" : "u_MaterialAmbient.rgb";
            sb.append("    vec3 ambient = ").append(ambU).append(".rgb * ").append(ambMat).append(";\n");
            // Diffuse product
            final String difMat = key.cmReplacesDiffuse() ? "matColor.rgb" : "u_MaterialDiffuse.rgb";
            sb.append("    vec3 diffuse = ").append(difU).append(".rgb * ").append(difMat).append(";\n");
            // Specular product
            final String speMat = key.cmReplacesSpecular() ? "matColor.rgb" : "u_MaterialSpecular.rgb";
            sb.append("    vec3 specular = ").append(speU).append(".rgb * ").append(speMat).append(";\n");
        } else {
            sb.append("    vec3 ambient = u_LightProd").append(li).append("Ambient;\n");
            sb.append("    vec3 diffuse = u_LightProd").append(li).append("Diffuse;\n");
            sb.append("    vec3 specular = u_LightProd").append(li).append("Specular;\n");
        }

        sb.append("    color0 += ambient + NdotVP * diffuse;\n");
        if (key.separateSpecular()) {
            sb.append("    specAccum += spec * specular;\n");
        } else {
            sb.append("    color0 += spec * specular;\n");
        }

        sb.append("  }\n");
    }

    private static void emitColorPassthrough(StringBuilder sb, VertexKey key) {
        sb.append("  // Color passthrough (no lighting)\n");
        if (key.hasVertexColor()) {
            sb.append("  v_Color = a_Color;\n");
        } else {
            sb.append("  v_Color = u_CurrentColor;\n");
        }
        sb.append('\n');
    }

    private static void emitTexCoordPassthrough(StringBuilder sb, VertexKey key) {
        if (key.texGenEnabled()) {
            emitTexGenCoordGeneration(sb, key);
        } else if (key.textureEnabled() || key.hasVertexTexCoord()) {
            sb.append("  // Texture coordinates\n");
            if (key.hasVertexTexCoord()) {
                if (key.textureMatrixEnabled()) {
                    sb.append("  v_TexCoord0 = (u_TextureMatrix0 * vec4(a_TexCoord0, 0.0, 1.0)).st;\n");
                } else {
                    sb.append("  v_TexCoord0 = a_TexCoord0;\n");
                }
            } else {
                if (key.textureMatrixEnabled()) {
                    sb.append("  v_TexCoord0 = (u_TextureMatrix0 * u_CurrentTexCoord).st;\n");
                } else {
                    sb.append("  v_TexCoord0 = u_CurrentTexCoord.st;\n");
                }
            }
        }
        if (key.lightmapEnabled()) {
            if (key.hasVertexLightmap()) {
                sb.append("  v_TexCoord1 = (u_LightmapTextureMatrix * vec4(a_TexCoord1, 0.0, 1.0)).st;\n");
            } else {
                sb.append("  v_TexCoord1 = (u_LightmapTextureMatrix * vec4(u_CurrentLightmapCoord, 0.0, 1.0)).st;\n");
            }
        }
    }

    /**
     * Emit per-coordinate texgen code following Mesa's ffvertex_prog.c build order.
     * OBJ_LINEAR: dot(pos4, objPlane), EYE_LINEAR: dot(eyePos, eyePlane).
     * Coordinates without texgen use vertex attrib or default (0,0,0,1).
     */
    private static void emitTexGenCoordGeneration(StringBuilder sb, VertexKey key) {
        sb.append("  // TexGen coordinate generation\n");
        sb.append("  vec4 texGenCoord = vec4(0.0, 0.0, 0.0, 1.0);\n");

        emitTexGenComponent(sb, key.texGenModeS(), "s", "S", key);
        emitTexGenComponent(sb, key.texGenModeT(), "t", "T", key);
        emitTexGenComponent(sb, key.texGenModeR(), "r", "R", key);
        emitTexGenComponent(sb, key.texGenModeQ(), "q", "Q", key);

        // Always apply texture matrix when texgen is active (forced on in VertexKey)
        sb.append("  v_TexCoord0 = (u_TextureMatrix0 * texGenCoord).st;\n");
    }

    private static void emitTexGenComponent(StringBuilder sb, int mode, String swizzle, String coordName, VertexKey key) {
        switch (mode) {
            case VertexKey.TG_OBJ_LINEAR ->
                sb.append("  texGenCoord.").append(swizzle).append(" = dot(pos4, u_TexGenObjPlane").append(coordName).append(");\n");
            case VertexKey.TG_EYE_LINEAR ->
                sb.append("  texGenCoord.").append(swizzle).append(" = dot(eyePos, u_TexGenEyePlane").append(coordName).append(");\n");
            // TG_NONE: keep default (0 for s/t/r, 1 for q) — already set in texGenCoord init
        }
    }

    private static boolean texGenNeedsEyePos(VertexKey key) {
        return key.texGenModeS() == VertexKey.TG_EYE_LINEAR
            || key.texGenModeT() == VertexKey.TG_EYE_LINEAR
            || key.texGenModeR() == VertexKey.TG_EYE_LINEAR
            || key.texGenModeQ() == VertexKey.TG_EYE_LINEAR;
    }

    private static void emitFogDistance(StringBuilder sb, VertexKey key) {
        sb.append("  // Fog distance\n");
        // Mesa ffvertex_prog.c build_fog(): FDM_EYE_RADIAL/EYE_PLANE/EYE_PLANE_ABS
        final int fogDistMode = key.fogDistanceMode();
        switch (fogDistMode) {
            case 0 -> // FDM_EYE_RADIAL: Euclidean distance
                sb.append("  v_FogCoord = length(eyePos.xyz);\n");
            case 1 -> // FDM_EYE_PLANE: raw Z (can be negative, clamped by fog factor)
                sb.append("  v_FogCoord = eyePos.z;\n");
            case 2 -> // FDM_EYE_PLANE_ABS: absolute Z
                sb.append("  v_FogCoord = abs(eyePos.z);\n");
            default ->
                sb.append("  v_FogCoord = abs(eyePos.z);\n");
        }
    }

    private static void emitClipDistances(StringBuilder sb) {
        sb.append("  // Clip distances\n");
        for (int i = 0; i < 8; i++) {
            sb.append("  gl_ClipDistance[").append(i).append("] = dot(u_ClipPlane[").append(i).append("], eyePos);\n");
        }
    }
}
