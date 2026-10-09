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

        sb.append(FFPUniformBlock.GLSL_DECL);
        sb.append('\n');

        sb.append("// Pass-through varyings\n");
        sb.append("in vec4 v_Color_gs[];\n");
        if (key.separateSpecular()) {
            sb.append("in vec3 v_SpecularColor_gs[];\n");
        }
        if (key.unitTexCoordEnabled(0) || key.texGenEnabled()) sb.append("in vec4 v_TexCoord0_gs[];\n");
        if (key.lightmapEnabled())            sb.append("in vec4 v_TexCoord1_gs[];\n");
        if (key.unitTexCoordEnabled(2))       sb.append("in vec4 v_TexCoord2_gs[];\n");
        if (key.unitTexCoordEnabled(3))       sb.append("in vec4 v_TexCoord3_gs[];\n");
        if (key.fogEnabled()) {
            sb.append("in float v_FogCoord_gs[];\n");
        }
        if (key.lineStipple()) {
            sb.append("flat in vec2 v_LineStart_gs[];\n");
        }
        sb.append('\n');

        sb.append("out vec4 v_Color;\n");
        if (key.separateSpecular()) {
            sb.append("out vec3 v_SpecularColor;\n");
        }
        if (key.unitTexCoordEnabled(0) || key.texGenEnabled()) sb.append("out vec4 v_TexCoord0;\n");
        if (key.lightmapEnabled())            sb.append("out vec4 v_TexCoord1;\n");
        if (key.unitTexCoordEnabled(2))       sb.append("out vec4 v_TexCoord2;\n");
        if (key.unitTexCoordEnabled(3))       sb.append("out vec4 v_TexCoord3;\n");
        if (key.fogEnabled()) {
            sb.append("out float v_FogCoord;\n");
        }
        if (key.lineStipple()) {
            sb.append("flat out vec2 v_LineStart;\n");
        }
        sb.append('\n');

        sb.append("void main() {\n");
        sb.append("    vec4 c0 = gl_in[0].gl_Position;\n");
        sb.append("    vec4 c1 = gl_in[1].gl_Position;\n\n");

        sb.append("    float d0 = c0.z + c0.w;\n");
        sb.append("    float d1 = c1.z + c1.w;\n");
        sb.append("    if (d0 < 0.0 && d1 < 0.0) return;\n");
        sb.append("    float t0 = d0 < 0.0 ? d0 / (d0 - d1) : 0.0;\n");
        sb.append("    float t1 = d1 < 0.0 ? d0 / (d0 - d1) : 1.0;\n");
        sb.append("    vec4 p0 = mix(c0, c1, t0);\n");
        sb.append("    vec4 p1 = mix(c0, c1, t1);\n\n");

        sb.append("    // NDC positions\n");
        sb.append("    vec2 n0 = p0.xy / p0.w;\n");
        sb.append("    vec2 n1 = p1.xy / p1.w;\n\n");

        sb.append("    vec2 pixel = 2.0 / u_ViewportSize;\n");
        sb.append("    float halfWidth = 0.5 * u_LineWidth;\n");
        sb.append("    vec2 offset;\n");
        sb.append("    vec2 shift;\n");
        sb.append("    vec2 span = abs(n1 - n0) * u_ViewportSize;\n");
        sb.append("    if (span.x > span.y) {\n");
        sb.append("        offset = vec2(0.0, halfWidth * pixel.y);\n");
        sb.append("        shift = vec2(n0.x < n1.x ? -0.5 : 0.5, -0.125) * pixel;\n");
        sb.append("    } else {\n");
        sb.append("        offset = vec2(halfWidth * pixel.x, 0.0);\n");
        sb.append("        shift = vec2(0.125, n0.y < n1.y ? -0.5 : 0.5) * pixel;\n");
        sb.append("    }\n\n");

        emitVertex(sb, key, 0, "+offset + shift");
        emitVertex(sb, key, 0, "-offset + shift");
        emitVertex(sb, key, 1, "+offset + shift");
        emitVertex(sb, key, 1, "-offset + shift");

        sb.append("    EndPrimitive();\n");
        sb.append("}\n");

        return sb.toString();
    }

    private static void emitVertex(StringBuilder sb, VertexKey key, int endpointIdx, String offsetExpr) {
        final String pi = "p" + endpointIdx;
        final String ni = "n" + endpointIdx;
        final String ti = "t" + endpointIdx;

        sb.append("    // Endpoint ").append(endpointIdx).append(' ').append(offsetExpr).append('\n');

        sb.append("    v_Color = ").append(lerp("v_Color_gs", ti)).append(";\n");
        if (key.separateSpecular()) {
            sb.append("    v_SpecularColor = ").append(lerp("v_SpecularColor_gs", ti)).append(";\n");
        }
        if (key.unitTexCoordEnabled(0) || key.texGenEnabled()) sb.append("    v_TexCoord0 = ").append(lerp("v_TexCoord0_gs", ti)).append(";\n");
        if (key.lightmapEnabled())            sb.append("    v_TexCoord1 = ").append(lerp("v_TexCoord1_gs", ti)).append(";\n");
        if (key.unitTexCoordEnabled(2))       sb.append("    v_TexCoord2 = ").append(lerp("v_TexCoord2_gs", ti)).append(";\n");
        if (key.unitTexCoordEnabled(3))       sb.append("    v_TexCoord3 = ").append(lerp("v_TexCoord3_gs", ti)).append(";\n");
        if (key.fogEnabled()) {
            sb.append("    v_FogCoord = ").append(lerp("v_FogCoord_gs", ti)).append(";\n");
        }
        if (key.lineStipple()) {
            sb.append("    v_LineStart = v_LineStart_gs[0];\n");
        }

        if (key.clipPlanesEnabled()) {
            for (int i = 0; i < 8; i++) {
                sb.append("    gl_ClipDistance[").append(i).append("] = mix(gl_in[0].gl_ClipDistance[").append(i).append("], gl_in[1].gl_ClipDistance[").append(i).append("], ").append(ti).append(");\n");
            }
        }

        sb.append("    gl_Position = vec4((").append(ni).append(' ').append(offsetExpr).append(") * ").append(pi).append(".w, ").append(pi).append(".z, ").append(pi).append(".w);\n");
        sb.append("    EmitVertex();\n\n");
    }

    private static String lerp(String input, String t) {
        return "mix(" + input + "[0], " + input + "[1], " + t + ")";
    }
}
