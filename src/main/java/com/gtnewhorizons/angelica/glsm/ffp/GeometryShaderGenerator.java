package com.gtnewhorizons.angelica.glsm.ffp;

/**
 * Generates GLSL 330 core geometry shaders for wide line emulation.
 */
public final class GeometryShaderGenerator {

    private GeometryShaderGenerator() {}

    public static String generate(VertexKey key) {
        final StringBuilder sb = new StringBuilder(1024);
        sb.append("#version 330 core\n\n");

        sb.append("layout(lines) in;\n");
        sb.append("layout(triangle_strip, max_vertices = 4) out;\n\n");

        sb.append("uniform vec2 u_ViewportSize;\n");
        sb.append("uniform float u_LineWidth;\n\n");

        sb.append("// Pass-through varyings\n");
        sb.append("in vec4 v_Color_gs[];\n");
        if (key.separateSpecular()) {
            sb.append("in vec3 v_SpecularColor_gs[];\n");
        }
        if (key.textureEnabled() || key.hasVertexTexCoord() || key.texGenEnabled()) {
            sb.append("in vec4 v_TexCoord0_gs[];\n");
        }
        if (key.lightmapEnabled()) {
            sb.append("in vec4 v_TexCoord1_gs[];\n");
        }
        if (key.fogEnabled()) {
            sb.append("in float v_FogCoord_gs[];\n");
        }
        sb.append('\n');

        sb.append("out vec4 v_Color;\n");
        if (key.separateSpecular()) {
            sb.append("out vec3 v_SpecularColor;\n");
        }
        if (key.textureEnabled() || key.hasVertexTexCoord() || key.texGenEnabled()) {
            sb.append("out vec4 v_TexCoord0;\n");
        }
        if (key.lightmapEnabled()) {
            sb.append("out vec4 v_TexCoord1;\n");
        }
        if (key.fogEnabled()) {
            sb.append("out float v_FogCoord;\n");
        }
        sb.append('\n');

        sb.append("void main() {\n");
        sb.append("    vec4 c0 = gl_in[0].gl_Position;\n");
        sb.append("    vec4 c1 = gl_in[1].gl_Position;\n\n");

        sb.append("    // NDC positions\n");
        sb.append("    vec2 n0 = c0.xy / c0.w;\n");
        sb.append("    vec2 n1 = c1.xy / c1.w;\n\n");

        sb.append("    // Screen-space direction and perpendicular\n");
        sb.append("    vec2 dir = normalize((n1 - n0) * u_ViewportSize);\n");
        sb.append("    vec2 offset = vec2(-dir.y, dir.x) * u_LineWidth / u_ViewportSize;\n\n");

        emitVertex(sb, key, 0, "+offset");
        emitVertex(sb, key, 0, "-offset");
        emitVertex(sb, key, 1, "+offset");
        emitVertex(sb, key, 1, "-offset");

        sb.append("    EndPrimitive();\n");
        sb.append("}\n");

        return sb.toString();
    }

    private static void emitVertex(StringBuilder sb, VertexKey key, int endpointIdx, String offsetExpr) {
        final String ci = "c" + endpointIdx;
        final String ni = "n" + endpointIdx;
        final String idx = "[" + endpointIdx + "]";

        sb.append("    // Endpoint ").append(endpointIdx).append(' ').append(offsetExpr).append('\n');

        sb.append("    v_Color = v_Color_gs").append(idx).append(";\n");
        if (key.separateSpecular()) {
            sb.append("    v_SpecularColor = v_SpecularColor_gs").append(idx).append(";\n");
        }
        if (key.textureEnabled() || key.hasVertexTexCoord() || key.texGenEnabled()) {
            sb.append("    v_TexCoord0 = v_TexCoord0_gs").append(idx).append(";\n");
        }
        if (key.lightmapEnabled()) {
            sb.append("    v_TexCoord1 = v_TexCoord1_gs").append(idx).append(";\n");
        }
        if (key.fogEnabled()) {
            sb.append("    v_FogCoord = v_FogCoord_gs").append(idx).append(";\n");
        }

        if (key.clipPlanesEnabled()) {
            for (int i = 0; i < 8; i++) {
                sb.append("    gl_ClipDistance[").append(i).append("] = gl_in").append(idx).append(".gl_ClipDistance[").append(i).append("];\n");
            }
        }

        sb.append("    gl_Position = vec4((").append(ni).append(' ').append(offsetExpr).append(") * ").append(ci).append(".w, ").append(ci).append(".z, ").append(ci).append(".w);\n");
        sb.append("    EmitVertex();\n\n");
    }
}
