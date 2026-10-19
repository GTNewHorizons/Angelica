package com.gtnewhorizons.angelica.glsm.ffp;

import com.gtnewhorizons.angelica.glsm.states.FogState;

public final class FfpGlsl {

    private FfpGlsl() {}

    // Mesa ffvertex_prog.c build_fog(): FDM_EYE_RADIAL/EYE_PLANE/EYE_PLANE_ABS
    public static String fogDistance(int mode, String eye) {
        return switch (mode) {
            case FogState.DISTANCE_EYE_RADIAL -> "length(" + eye + ".xyz)";
            case FogState.DISTANCE_EYE_PLANE -> eye + ".z";
            default -> "abs(" + eye + ".z)";
        };
    }

    public static void appendFogFactor(StringBuilder sb, String indent, int fogMode, String coord, String params) {
        switch (fogMode) {
            case FragmentKey.FOG_LINEAR -> sb.append(indent).append("f = ").append(coord).append(" * ").append(params).append(".x + ").append(params).append(".y;\n");
            case FragmentKey.FOG_EXP -> sb.append(indent).append("f = exp2(-(").append(coord).append(" * ").append(params).append(".z));\n");
            case FragmentKey.FOG_EXP2 -> {
                sb.append(indent).append("float fogTmp = ").append(coord).append(" * ").append(params).append(".w;\n");
                sb.append(indent).append("f = exp2(-(fogTmp * fogTmp));\n");
            }
        }
    }

    public static String quantizeAlpha(String alpha) {
        return "round(" + alpha + " * 255.0)";
    }
}
