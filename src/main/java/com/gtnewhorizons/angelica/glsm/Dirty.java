package com.gtnewhorizons.angelica.glsm;

import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;

public class Dirty {
    private Dirty() {}

    public static final long
        ALPHA_TEST              = 1L << 0,
        BLEND                   = 1L << 1,
        DEPTH_TEST              = 1L << 2,
        CULL                    = 1L << 3,
        LIGHTING                = 1L << 4,
        RESCALE_NORMAL          = 1L << 5,
        TEXTURE_2D              = 1L << 6,
        FOG                     = 1L << 7,
        BLEND_STATE             = 1L << 8,
        DEPTH_FUNC              = 1L << 9,
        DEPTH_MASK              = 1L << 10,
        COLOR                   = 1L << 11,
        COLOR_MASK              = 1L << 12,
        CLEAR_COLOR             = 1L << 13,
        ACTIVE_TEXTURE          = 1L << 14,
        BOUND_TEXTURE           = 1L << 15,
        SHADE_MODEL             = 1L << 16,
    ALL = ALPHA_TEST | BLEND | DEPTH_TEST | CULL | LIGHTING | RESCALE_NORMAL | TEXTURE_2D | FOG | BLEND_STATE | DEPTH_FUNC | DEPTH_MASK | COLOR | COLOR_MASK | CLEAR_COLOR | ACTIVE_TEXTURE | BOUND_TEXTURE | SHADE_MODEL;

    public static long getFlagFromCap(int cap) {
        return switch (cap) {
            case GL11.GL_ALPHA_TEST -> ALPHA_TEST;
            case GL11.GL_BLEND -> BLEND;
            case GL11.GL_DEPTH_TEST -> DEPTH_TEST;
            case GL11.GL_CULL_FACE -> CULL;
            case GL11.GL_LIGHTING -> LIGHTING;
            case GL12.GL_RESCALE_NORMAL -> RESCALE_NORMAL;
            case GL11.GL_TEXTURE_2D -> TEXTURE_2D;
            case GL11.GL_FOG -> FOG;
            default -> throw new RuntimeException("Invalid cap: " + cap);
        };
    }
}
