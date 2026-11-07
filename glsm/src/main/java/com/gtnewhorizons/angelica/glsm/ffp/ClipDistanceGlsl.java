package com.gtnewhorizons.angelica.glsm.ffp;

public final class ClipDistanceGlsl {

    public static final String VS_OUT_DIST0 = "out vec4 angelica_ClipDist0;";
    public static final String VS_OUT_DIST1 = "out vec4 angelica_ClipDist1;";
    public static final String FS_IN_DIST0 = "in vec4 angelica_ClipDist0;";
    public static final String FS_IN_DIST1 = "in vec4 angelica_ClipDist1;";
    public static final String FS_DISCARD = "if (any(lessThan(angelica_ClipDist0, vec4(0.0))) || any(lessThan(angelica_ClipDist1, vec4(0.0)))) discard;\n";

    private ClipDistanceGlsl() {}

    public static void appendVsAssign(StringBuilder sb, String[] distances) {
        sb.append("  angelica_ClipDist0 = vec4(").append(distances[0]).append(", ").append(distances[1]).append(", ").append(distances[2]).append(", ").append(distances[3]).append(");\n");
        sb.append("  angelica_ClipDist1 = vec4(").append(distances[4]).append(", ").append(distances[5]).append(", ").append(distances[6]).append(", ").append(distances[7]).append(");\n");
    }
}
