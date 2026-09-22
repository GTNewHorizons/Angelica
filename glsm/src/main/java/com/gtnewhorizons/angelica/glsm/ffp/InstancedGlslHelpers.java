package com.gtnewhorizons.angelica.glsm.ffp;

import com.gtnewhorizon.gtnhlib.client.renderer.vertex.VertexFormatElement;

import java.util.ArrayList;

public final class InstancedGlslHelpers {

    private InstancedGlslHelpers() {}

    public static String mat4FromRows(String row0, String row1, String row2) {
        return "mat4("
            + "vec4(" + row0 + ".x, " + row1 + ".x, " + row2 + ".x, 0.0), "
            + "vec4(" + row0 + ".y, " + row1 + ".y, " + row2 + ".y, 0.0), "
            + "vec4(" + row0 + ".z, " + row1 + ".z, " + row2 + ".z, 0.0), "
            + "vec4(" + row0 + ".w, " + row1 + ".w, " + row2 + ".w, 1.0))";
    }

    public static String mat3FromRows(String row0, String row1, String row2) {
        return "mat3("
            + "normalize(vec3(" + row0 + ".x, " + row1 + ".x, " + row2 + ".x)), "
            + "normalize(vec3(" + row0 + ".y, " + row1 + ".y, " + row2 + ".y)), "
            + "normalize(vec3(" + row0 + ".z, " + row1 + ".z, " + row2 + ".z)))";
    }

    public static String particlePos(String centerHalf, String offset) {
        return "vec4(" + centerHalf + ".xyz + " + offset + " * " + centerHalf + ".w, 1.0)";
    }

    public static String particleUv(String uv, String corner) {
        return "vec4("
            + "mix(" + uv + ".x, " + uv + ".z, " + corner + ".x), "
            + "mix(" + uv + ".y, " + uv + ".w, " + corner + ".y), "
            + "0.0, 1.0)";
    }

    public static String weatherPrologue(String prefix, String columnSpan, String jitter, String params,
        String corner, String vertex, String color, String tex0, String tex1, String sep) {
        final String p0 = prefix + "WeatherParams0";
        final String p1 = prefix + "WeatherParams1";
        final String p2 = prefix + "WeatherParams2";
        final String v = prefix + "w";
        return "vec2 " + v + "Col = " + columnSpan + ".xy;" + sep
            // Vanilla's rainXCoords table, degenerate at the camera's own column exactly as it is there.
            + "vec2 " + v + "Half = vec2(-" + v + "Col.y, " + v + "Col.x) * (0.5 / sqrt(dot(" + v + "Col, " + v + "Col)));" + sep
            + "float " + v + "Y = mix(" + columnSpan + ".z, " + columnSpan + ".w, " + corner + ".y);" + sep
            + "vec2 " + v + "Edge = " + v + "Col + vec2(0.5) + (" + corner + ".x * 2.0 - 1.0) * " + v + "Half;" + sep
            + vertex + " = vec4(" + v + "Edge.x + " + p0 + ".x, " + v + "Y + " + p0 + ".y, " + v + "Edge.y + " + p0 + ".z, 1.0);" + sep
            + "float " + v + "Dist = length(" + v + "Col + vec2(0.5) - " + p1 + ".xy) * " + p0 + ".w;" + sep
            + "float " + v + "Fall = 1.0 - " + v + "Dist * " + v + "Dist;" + sep
            + "float " + v + "Snow = " + params + ".w;" + sep
            + "float " + v + "Scroll = " + v + "Snow < 0.5" + sep
            + "    ? (mod(" + p2 + ".x + " + params + ".z, 32.0) + " + p1 + ".z) / 32.0 * (3.0 + " + jitter + ".x)" + sep
            + "    : (" + p2 + ".y + " + p1 + ".z) / 512.0;" + sep
            + "vec2 " + v + "Drift = " + v + "Snow < 0.5 ? vec2(0.0) : vec2(" + sep
            + "    " + jitter + ".x + " + p1 + ".w * 0.01 * " + jitter + ".y," + sep
            + "    " + jitter + ".z + " + p1 + ".w * " + jitter + ".w * 0.001);" + sep
            + tex0 + " = vec4(" + corner + ".x + " + v + "Drift.x, " + v + "Y * 0.25 + " + v + "Scroll + " + v + "Drift.y, 0.0, 1.0);" + sep
            + tex1 + " = vec4(" + params + ".xy, 0.0, 1.0);" + sep
            + color + " = vec4(1.0, 1.0, 1.0, (" + v + "Snow < 0.5 ? " + v + "Fall * 0.5 + 0.5 : " + v + "Fall * 0.3 + 0.5) * " + p2 + ".z);";
    }

    public static String cubePrelude(String prefix, String mid, String delta, String normal, String tex, String sep) {
        return "float " + prefix + "cubeMirror = " + tex + ".z < 0.0 ? 1.0 : 0.0;" + sep
            + "vec2 " + prefix + "cubeMidU = " + mid + ".xy - " + prefix + "cubeMirror * " + normal + ".x;" + sep
            + "vec2 " + prefix + "cubeU = " + prefix + "cubeMidU + (1.0 - 2.0 * " + prefix + "cubeMirror) * " + delta + ".xy;" + sep
            + "vec2 " + prefix + "cubeV = " + mid + ".zw + " + delta + ".zw;";
    }

    public static String cubeUv(String tex, String lightScale, String uA, String uZ, String vA, String vZ) {
        return "vec4("
            + tex + ".x + " + uA + " * abs(" + tex + ".z) + " + uZ + " * " + lightScale + ".z, "
            + tex + ".y + " + vA + " * " + tex + ".w + " + vZ + " * " + lightScale + ".w, "
            + "0.0, 1.0)";
    }

    public static String[] attributeDecls(String prefix, Instancing kind) {
        final ArrayList<String> decls = new ArrayList<>(9);
        switch (kind) {
            case NONE -> {}
            case TEMPLATE -> addHead(decls, prefix, "vec2", "InstLightmap");
            case CUBE -> {
                decls.add(decl(VertexFormatElement.Usage.PRIMARY_UV.getAttributeLocation(), "vec4", prefix + "CubeMid"));
                decls.add(decl(VertexFormatElement.Usage.SECONDARY_UV.getAttributeLocation(), "vec4", prefix + "CubeDelta"));
                addHead(decls, prefix, "vec4", "InstLightmapScale");
                decls.add(decl(CubeInstancedAttribs.LOC_CUBE_TEX, "vec4", prefix + "CubeTex"));
            }
            case PARTICLE -> {
                decls.add(decl(ParticleInstancedAttribs.LOC_CENTER_HALF, "vec4", prefix + "InstCenterHalf"));
                decls.add(decl(ParticleInstancedAttribs.LOC_UV, "vec4", prefix + "InstUv"));
                decls.add(decl(ParticleInstancedAttribs.LOC_COLOR, "vec4", prefix + "InstColor"));
                decls.add(decl(ParticleInstancedAttribs.LOC_LIGHTMAP, "vec2", prefix + "InstLightmap"));
            }
            case WEATHER -> {
                decls.add(decl(WeatherInstancedAttribs.LOC_COLUMN_SPAN, "vec4", prefix + "InstColumnSpan"));
                decls.add(decl(WeatherInstancedAttribs.LOC_JITTER, "vec4", prefix + "InstJitter"));
                decls.add(decl(WeatherInstancedAttribs.LOC_PARAMS, "vec4", prefix + "InstWeatherParams"));
            }
        }
        return decls.toArray(String[]::new);
    }

    private static void addHead(ArrayList<String> decls, String prefix, String lightmapType, String lightmapName) {
        decls.add(decl(InstancedAttribs.LOC_ROW0, "vec4", prefix + "InstRow0"));
        decls.add(decl(InstancedAttribs.LOC_ROW1, "vec4", prefix + "InstRow1"));
        decls.add(decl(InstancedAttribs.LOC_ROW2, "vec4", prefix + "InstRow2"));
        decls.add(decl(InstancedAttribs.LOC_COLOR, "vec4", prefix + "InstColor"));
        decls.add(decl(InstancedAttribs.LOC_OVERLAY, "vec4", prefix + "InstOverlay"));
        decls.add(decl(InstancedAttribs.LOC_LIGHTMAP, lightmapType, prefix + lightmapName));
    }

    private static String decl(int loc, String type, String name) {
        return "layout(location = " + loc + ") in " + type + " " + name + ";";
    }
}
