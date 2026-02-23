package com.gtnewhorizons.angelica.glsm.ffp;

import com.gtnewhorizons.angelica.glsm.GLStateManager;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;

/**
 * Compact key capturing the fragment shader permutation axes for FFP emulation.
 * Packed into a single long for fast hash/compare.
 */
public final class FragmentKey {

    private final long packed;

    private static final int BIT_FOG_MODE          = 0;
    private static final int BIT_ALPHA_TEST        = 2;
    private static final int BIT_ALPHA_FUNC        = 3;
    private static final int BIT_SEPARATE_SPECULAR = 6;
    private static final int BIT_TEXTURE           = 7;
    private static final int BIT_LIGHTMAP          = 8;
    private static final int BIT_TEX_ENV_MODE      = 9; // 3 bits: 0=MODULATE, 1=REPLACE, 2=ADD, 3=DECAL, 4=BLEND

    public static final int FOG_NONE   = 0;
    public static final int FOG_LINEAR = 1;
    public static final int FOG_EXP    = 2;
    public static final int FOG_EXP2   = 3;

    private FragmentKey(long packed) {
        this.packed = packed;
    }

    public long pack() { return packed; }

    public int fogMode()               { return (int)(packed & 0x3); }
    public boolean alphaTestEnabled()  { return ((packed >> BIT_ALPHA_TEST) & 1) != 0; }
    public int alphaTestFunc()         { return (int)((packed >> BIT_ALPHA_FUNC) & 0x7); }
    public boolean separateSpecular()  { return ((packed >> BIT_SEPARATE_SPECULAR) & 1) != 0; }
    public boolean textureEnabled()    { return ((packed >> BIT_TEXTURE) & 1) != 0; }
    public boolean lightmapEnabled()   { return ((packed >> BIT_LIGHTMAP) & 1) != 0; }
    public int texEnvMode()            { return (int)((packed >> BIT_TEX_ENV_MODE) & 0x7); }

    public static final int TEX_ENV_MODULATE = 0;
    public static final int TEX_ENV_REPLACE  = 1;
    public static final int TEX_ENV_ADD      = 2;
    public static final int TEX_ENV_DECAL    = 3;
    public static final int TEX_ENV_BLEND    = 4;

    public static long packFromState() {
        long bits = 0;

        // Fog
        if (GLStateManager.getFogMode().isEnabled()) {
            final int glFogMode = GLStateManager.getFogState().getFogMode();
            final int fogMode = switch (glFogMode) {
                case GL11.GL_LINEAR -> FOG_LINEAR;
                case GL11.GL_EXP    -> FOG_EXP;
                case GL11.GL_EXP2   -> FOG_EXP2;
                default -> FOG_NONE;
            };
            bits |= ((long) fogMode & 0x3);
        }

        // Alpha test
        if (GLStateManager.getAlphaTest().isEnabled()) {
            bits |= (1L << BIT_ALPHA_TEST);
            final int func = encodeAlphaFunc(GLStateManager.getAlphaState().getFunction());
            bits |= ((long) func & 0x7) << BIT_ALPHA_FUNC;
        }

        // Separate specular
        if (GLStateManager.getLightingState().isEnabled()
            && GLStateManager.getLightModel().colorControl == GL12.GL_SEPARATE_SPECULAR_COLOR) {
            bits |= (1L << BIT_SEPARATE_SPECULAR);
        }

        // Texture unit 0
        if (GLStateManager.getTextures().getTextureUnitStates(0).isEnabled()) {
            bits |= (1L << BIT_TEXTURE);

            // Tex env mode only matters when texture is enabled
            final int envMode = encodeTexEnvMode(GLStateManager.getTexEnvMode().getValue());
            bits |= ((long) envMode & 0x7) << BIT_TEX_ENV_MODE;
        }
        // Texture unit 1 (lightmap)
        if (GLStateManager.getTextures().getTextureUnitStates(1).isEnabled()) {
            bits |= (1L << BIT_LIGHTMAP);
        }

        return bits;
    }

    public static FragmentKey fromState() {
        return new FragmentKey(packFromState());
    }

    static FragmentKey fromPacked(long packed) {
        return new FragmentKey(packed);
    }

    private static int encodeAlphaFunc(int glFunc) {
        return glFunc & 0x7;
    }

    private static int encodeTexEnvMode(int glMode) {
        return switch (glMode) {
            case GL11.GL_MODULATE -> TEX_ENV_MODULATE;
            case GL11.GL_REPLACE  -> TEX_ENV_REPLACE;
            case GL11.GL_ADD      -> TEX_ENV_ADD;
            case GL11.GL_DECAL    -> TEX_ENV_DECAL;
            case GL11.GL_BLEND    -> TEX_ENV_BLEND;
            default -> TEX_ENV_MODULATE;
        };
    }

    public static int decodeAlphaFunc(int encoded) {
        return 0x0200 | encoded;
    }

    @Override
    public int hashCode() {
        return Long.hashCode(packed);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof FragmentKey other)) return false;
        return packed == other.packed;
    }

    @Override
    public String toString() {
        final String fogName = switch (fogMode()) {
            case FOG_NONE   -> "NONE";
            case FOG_LINEAR -> "LINEAR";
            case FOG_EXP    -> "EXP";
            case FOG_EXP2   -> "EXP2";
            default -> "?";
        };
        final String envName = switch (texEnvMode()) {
            case TEX_ENV_MODULATE -> "MOD";
            case TEX_ENV_REPLACE  -> "REPL";
            case TEX_ENV_ADD      -> "ADD";
            case TEX_ENV_DECAL    -> "DEC";
            case TEX_ENV_BLEND    -> "BL";
            default -> "?";
        };
        return String.format("FFPFragmentKey[0x%03X: fog=%s alpha=%b(%s) specSep=%b tex=%b(env=%s) lm=%b]",
            packed, fogName, alphaTestEnabled(),
            alphaTestEnabled() ? String.format("0x%04X", decodeAlphaFunc(alphaTestFunc())) : "-",
            separateSpecular(), textureEnabled(), envName, lightmapEnabled());
    }
}
