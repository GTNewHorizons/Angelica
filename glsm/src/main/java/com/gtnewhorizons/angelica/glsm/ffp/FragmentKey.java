package com.gtnewhorizons.angelica.glsm.ffp;

import java.util.Arrays;

import com.gtnewhorizons.angelica.glsm.GLStateManager;
import com.gtnewhorizons.angelica.glsm.states.TexEnvState;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL13;

/**
 * Packed-long fragment shader permutation key for FFP emulation.
 *
 * Layout:
 *   long[0]: global bits (10) + unit 0 (47 bits at offset 10)
 *   long[1..3]: unit 1..3 (47 bits each, low-aligned)
 * Only max(1, nrEnabledUnits) longs are significant.
 *
 * Per-unit packing (47 bits):
 *   0:     enabled
 *   1-3:   mode (MODULATE=0..COMBINE=5)
 *   4:     needsEnvColor
 *   5-8:   combineRgb (encoded 0-7)
 *   9-12:  combineAlpha (encoded 0-7)
 *   13-14: scaleShiftRgb
 *   15-16: scaleShiftAlpha
 *   17-21: arg0 RGB (source:3 + operand:2)
 *   22-26: arg1 RGB
 *   27-31: arg2 RGB
 *   32-36: arg0 Alpha
 *   37-41: arg1 Alpha
 *   42-46: arg2 Alpha
 */
public final class FragmentKey {
    private static final Logger LOGGER = LogManager.getLogger("FragmentKey");

    public static final int TEX_ENV_MODULATE = 0;
    public static final int TEX_ENV_REPLACE  = 1;
    public static final int TEX_ENV_ADD      = 2;
    public static final int TEX_ENV_DECAL    = 3;
    public static final int TEX_ENV_BLEND    = 4;
    public static final int TEX_ENV_COMBINE  = 5;

    public static final int COMBINE_REPLACE      = 0;
    public static final int COMBINE_MODULATE     = 1;
    public static final int COMBINE_ADD          = 2;
    public static final int COMBINE_ADD_SIGNED   = 3;
    public static final int COMBINE_INTERPOLATE  = 4;
    public static final int COMBINE_SUBTRACT     = 5;
    public static final int COMBINE_DOT3_RGB     = 6;
    public static final int COMBINE_DOT3_RGBA    = 7;

    public static final int SRC_TEXTURE       = 0;
    public static final int SRC_CONSTANT      = 1;
    public static final int SRC_PRIMARY_COLOR = 2;
    public static final int SRC_PREVIOUS      = 3;

    public static final int OP_SRC_COLOR           = 0;
    public static final int OP_ONE_MINUS_SRC_COLOR = 1;
    public static final int OP_SRC_ALPHA           = 2;
    public static final int OP_ONE_MINUS_SRC_ALPHA = 3;

    public static final int FOG_NONE   = 0;
    public static final int FOG_LINEAR = 1;
    public static final int FOG_EXP    = 2;
    public static final int FOG_EXP2   = 3;

    private static final int GLOBAL_BITS = 10;
    private static final int BIT_FOG_MODE          = 0;  // 2 bits
    private static final int BIT_ALPHA_TEST        = 2;  // 1 bit
    private static final int BIT_ALPHA_FUNC        = 3;  // 3 bits
    private static final int BIT_SEPARATE_SPECULAR = 6;  // 1 bit
    private static final int BIT_NR_ENABLED_UNITS  = 7;  // 3 bits

    private static final int U_ENABLED         = 0;
    private static final int U_MODE            = 1;   // 3 bits
    private static final int U_NEEDS_ENV_COLOR = 4;   // 1 bit
    private static final int U_COMBINE_RGB     = 5;   // 4 bits
    private static final int U_COMBINE_ALPHA   = 9;   // 4 bits
    private static final int U_SCALE_RGB       = 13;  // 2 bits
    private static final int U_SCALE_ALPHA     = 15;  // 2 bits
    private static final int U_ARG0_RGB        = 17;  // 5 bits (source:3 + operand:2)
    private static final int U_ARG1_RGB        = 22;  // 5 bits
    private static final int U_ARG2_RGB        = 27;  // 5 bits
    private static final int U_ARG0_ALPHA      = 32;  // 5 bits
    private static final int U_ARG1_ALPHA      = 37;  // 5 bits
    private static final int U_ARG2_ALPHA      = 42;  // 5 bits

    static final int MAX_UNITS = 4;

    private final long[] packed;
    private final int hash;

    private FragmentKey(long[] packed, int hash) {
        this.packed = packed;
        this.hash = hash;
    }

    /**
     * Pack current GLSM fragment state into pre-allocated output array.
     * @param out must be at least MAX_UNITS (4) longs
     * @return number of significant longs (1..4)
     */
    public static int packFromState(long[] out) {
        long global = 0;

        // Fog
        if (GLStateManager.getFogMode().isEnabled()) {
            final int fogMode = switch (GLStateManager.getFogState().getFogMode()) {
                case GL11.GL_LINEAR -> FOG_LINEAR;
                case GL11.GL_EXP    -> FOG_EXP;
                case GL11.GL_EXP2   -> FOG_EXP2;
                default -> FOG_NONE;
            };
            global |= (long) fogMode;
        }

        // Alpha test
        if (GLStateManager.getAlphaTest().isEnabled()) {
            global |= (1L << BIT_ALPHA_TEST);
            global |= ((long) (encodeAlphaFunc(GLStateManager.getAlphaState().getFunction()) & 0x7)) << BIT_ALPHA_FUNC;
        }

        // Separate specular
        if (GLStateManager.getLightingState().isEnabled()
            && GLStateManager.getLightModel().colorControl == GL12.GL_SEPARATE_SPECULAR_COLOR) {
            global |= (1L << BIT_SEPARATE_SPECULAR);
        }

        // Per-unit state
        int highestEnabled = -1;
        long unit0Bits = 0;
        for (int i = 0; i < MAX_UNITS; i++) {
            final boolean texEnabled = GLStateManager.getTextures().getTextureUnitStates(i).isEnabled();
            if (texEnabled) highestEnabled = i;

            final long unitBits = packUnit(i, texEnabled);
            if (i == 0) {
                unit0Bits = unitBits;
            } else {
                out[i] = unitBits;
            }
        }

        final int nrEnabled = highestEnabled + 1;
        global |= ((long) (nrEnabled & 0x7)) << BIT_NR_ENABLED_UNITS;
        global |= (unit0Bits << GLOBAL_BITS);
        out[0] = global;

        return Math.max(1, nrEnabled);
    }

    private static long packUnit(int unitIndex, boolean texEnabled) {
        if (!texEnabled) return 0;

        final TexEnvState envState = GLStateManager.getTextures().getTexEnvState(unitIndex);
        final int mode = encodeTexEnvMode(envState.mode);

        long bits = 1L; // enabled
        bits |= ((long) (mode & 0x7)) << U_MODE;

        boolean needsEnvColor = (mode == TEX_ENV_BLEND);

        if (mode == TEX_ENV_COMBINE) {
            final int combRgb = encodeCombineFunc(envState.combineRgb);
            final int combAlpha = encodeCombineFunc(envState.combineAlpha);
            bits |= ((long) (combRgb & 0xF)) << U_COMBINE_RGB;
            bits |= ((long) (combAlpha & 0xF)) << U_COMBINE_ALPHA;
            bits |= ((long) (encodeScale(envState.scaleRgb) & 0x3)) << U_SCALE_RGB;
            bits |= ((long) (encodeScale(envState.scaleAlpha) & 0x3)) << U_SCALE_ALPHA;

            // Only pack args actually used by each channel's combine function.
            // Unused arg slots stay 0, preventing TexEnvState defaults from inflating the cache.
            final int numArgsRgb = combineNumArgs(combRgb);
            final int numArgsAlpha = combineNumArgs(combAlpha);
            final int maxArgs = Math.max(numArgsRgb, numArgsAlpha);

            for (int a = 0; a < maxArgs; a++) {
                if (a < numArgsRgb) {
                    final int srcR = encodeSource(envState.sourceRgb[a]);
                    final int opR = encodeOperand(envState.operandRgb[a]);
                    bits |= ((long) ((srcR & 0x7) | ((opR & 0x3) << 3))) << (U_ARG0_RGB + a * 5);
                    if (srcR == SRC_CONSTANT) needsEnvColor = true;
                }
                if (a < numArgsAlpha) {
                    final int srcA = encodeSource(envState.sourceAlpha[a]);
                    final int opA = encodeOperand(envState.operandAlpha[a]);
                    bits |= ((long) ((srcA & 0x7) | ((opA & 0x3) << 3))) << (U_ARG0_ALPHA + a * 5);
                    if (srcA == SRC_CONSTANT) needsEnvColor = true;
                }
            }
        }

        if (needsEnvColor) {
            bits |= (1L << U_NEEDS_ENV_COLOR);
        }

        return bits;
    }

    /** Create FragmentKey from packed scratch data. Only called on cache miss. */
    public static FragmentKey fromPacked(long[] scratch, int len) {
        final long[] copy = Arrays.copyOf(scratch, len);
        return new FragmentKey(copy, Arrays.hashCode(copy));
    }

    /** Convenience: pack + create. Allocates. For infrequent paths. */
    public static FragmentKey fromState() {
        final long[] scratch = new long[MAX_UNITS];
        final int len = packFromState(scratch);
        return fromPacked(scratch, len);
    }

    @Override
    public int hashCode() {
        return hash;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof FragmentKey other)) return false;
        if (hash != other.hash) return false;
        return Arrays.equals(packed, other.packed);
    }

    public int fogMode()              { return (int) (packed[0] & 0x3); }
    public boolean alphaTestEnabled() { return ((packed[0] >> BIT_ALPHA_TEST) & 1) != 0; }
    public int alphaTestFunc()        { return (int) ((packed[0] >> BIT_ALPHA_FUNC) & 0x7); }
    public boolean separateSpecular() { return ((packed[0] >> BIT_SEPARATE_SPECULAR) & 1) != 0; }
    public int nrEnabledUnits()       { return (int) ((packed[0] >> BIT_NR_ENABLED_UNITS) & 0x7); }

    private long unitBits(int i) {
        return (i == 0) ? (packed[0] >>> GLOBAL_BITS) : packed[i];
    }

    public boolean unitEnabled(int i)       { return (unitBits(i) & 1) != 0; }
    public int unitMode(int i)              { return (int) ((unitBits(i) >> U_MODE) & 0x7); }
    public boolean unitNeedsEnvColor(int i) { return ((unitBits(i) >> U_NEEDS_ENV_COLOR) & 1) != 0; }
    public int unitCombineRgb(int i)        { return (int) ((unitBits(i) >> U_COMBINE_RGB) & 0xF); }
    public int unitCombineAlpha(int i)      { return (int) ((unitBits(i) >> U_COMBINE_ALPHA) & 0xF); }
    public int unitScaleShiftRgb(int i)     { return (int) ((unitBits(i) >> U_SCALE_RGB) & 0x3); }
    public int unitScaleShiftAlpha(int i)   { return (int) ((unitBits(i) >> U_SCALE_ALPHA) & 0x3); }

    public int unitSourceRgb(int i, int arg)    { return (int) ((unitBits(i) >> (U_ARG0_RGB + arg * 5)) & 0x7); }
    public int unitOperandRgb(int i, int arg)   { return (int) ((unitBits(i) >> (U_ARG0_RGB + arg * 5 + 3)) & 0x3); }
    public int unitSourceAlpha(int i, int arg)  { return (int) ((unitBits(i) >> (U_ARG0_ALPHA + arg * 5)) & 0x7); }
    public int unitOperandAlpha(int i, int arg) { return (int) ((unitBits(i) >> (U_ARG0_ALPHA + arg * 5 + 3)) & 0x3); }

    public boolean textureEnabled() {
        for (int i = 0; i < nrEnabledUnits(); i++) {
            if (unitEnabled(i)) return true;
        }
        return false;
    }

    public boolean lightmapEnabled() {
        return nrEnabledUnits() > 1 && unitEnabled(1);
    }

    private static int encodeAlphaFunc(int glFunc) {
        return glFunc & 0x7;
    }

    static int encodeTexEnvMode(int glMode) {
        return switch (glMode) {
            case GL11.GL_MODULATE -> TEX_ENV_MODULATE;
            case GL11.GL_REPLACE  -> TEX_ENV_REPLACE;
            case GL11.GL_ADD      -> TEX_ENV_ADD;
            case GL11.GL_DECAL    -> TEX_ENV_DECAL;
            case GL11.GL_BLEND    -> TEX_ENV_BLEND;
            case GL13.GL_COMBINE -> TEX_ENV_COMBINE;
            default -> TEX_ENV_MODULATE;
        };
    }

    static int encodeCombineFunc(int glFunc) {
        return switch (glFunc) {
            case GL11.GL_REPLACE     -> COMBINE_REPLACE;
            case GL11.GL_MODULATE    -> COMBINE_MODULATE;
            case GL11.GL_ADD         -> COMBINE_ADD;
            case GL13.GL_ADD_SIGNED  -> COMBINE_ADD_SIGNED;
            case GL13.GL_INTERPOLATE -> COMBINE_INTERPOLATE;
            case GL13.GL_SUBTRACT    -> COMBINE_SUBTRACT;
            case GL13.GL_DOT3_RGB    -> COMBINE_DOT3_RGB;
            case GL13.GL_DOT3_RGBA   -> COMBINE_DOT3_RGBA;
            default -> COMBINE_REPLACE;
        };
    }

    static int encodeSource(int glSource) {
        return switch (glSource) {
            case GL11.GL_TEXTURE       -> SRC_TEXTURE;
            case GL13.GL_CONSTANT      -> SRC_CONSTANT;
            case GL13.GL_PRIMARY_COLOR -> SRC_PRIMARY_COLOR;
            case GL13.GL_PREVIOUS      -> SRC_PREVIOUS;
            default -> {
                LOGGER.debug("Unknown TexEnv source 0x{}, falling back to SRC_PREVIOUS", Integer.toHexString(glSource));
                yield SRC_PREVIOUS;
            }
        };
    }

    static int encodeOperand(int glOperand) {
        return switch (glOperand) {
            case GL11.GL_SRC_COLOR           -> OP_SRC_COLOR;
            case GL11.GL_ONE_MINUS_SRC_COLOR -> OP_ONE_MINUS_SRC_COLOR;
            case GL11.GL_SRC_ALPHA           -> OP_SRC_ALPHA;
            case GL11.GL_ONE_MINUS_SRC_ALPHA -> OP_ONE_MINUS_SRC_ALPHA;
            default -> OP_SRC_COLOR;
        };
    }

    /** Number of source/operand args used by the given (encoded) combine function. */
    public static int combineNumArgs(int func) {
        return switch (func) {
            case COMBINE_REPLACE -> 1;
            case COMBINE_INTERPOLATE -> 3;
            default -> 2; // MODULATE, ADD, ADD_SIGNED, SUBTRACT, DOT3_*
        };
    }

    private static int encodeScale(float scale) {
        if (scale >= 4.0f) return 2;
        if (scale >= 2.0f) return 1;
        return 0;
    }

    public static int decodeAlphaFunc(int encoded) {
        return 0x0200 | encoded;
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
        final StringBuilder sb = new StringBuilder();
        sb.append(String.format("FFPFragmentKey[fog=%s alpha=%b(%s) specSep=%b units=%d",
            fogName, alphaTestEnabled(),
            alphaTestEnabled() ? String.format("0x%04X", decodeAlphaFunc(alphaTestFunc())) : "-",
            separateSpecular(), nrEnabledUnits()));
        for (int i = 0; i < nrEnabledUnits(); i++) {
            if (!unitEnabled(i)) {
                sb.append(String.format(" u%d=OFF", i));
            } else {
                final String modeName = switch (unitMode(i)) {
                    case TEX_ENV_MODULATE -> "MOD";
                    case TEX_ENV_REPLACE  -> "REPL";
                    case TEX_ENV_ADD      -> "ADD";
                    case TEX_ENV_DECAL    -> "DEC";
                    case TEX_ENV_BLEND    -> "BL";
                    case TEX_ENV_COMBINE  -> "COMB";
                    default -> "?";
                };
                sb.append(String.format(" u%d=%s", i, modeName));
            }
        }
        sb.append(']');
        return sb.toString();
    }
}
