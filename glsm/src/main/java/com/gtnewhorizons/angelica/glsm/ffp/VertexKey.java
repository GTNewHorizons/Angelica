package com.gtnewhorizons.angelica.glsm.ffp;

import com.gtnewhorizon.gtnhlib.client.renderer.MatrixHelper;
import com.gtnewhorizons.angelica.glsm.DisplayListManager;
import com.gtnewhorizons.angelica.glsm.GLStateManager;
import com.gtnewhorizons.angelica.glsm.states.TexGenState;
import com.gtnewhorizons.angelica.glsm.states.TextureUnitArray;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;

/**
 * Compact key capturing the vertex shader permutation axes for FFP emulation.
 * Packed into a single long for fast hash/compare.
 *
 * Per-unit fields are 4-bit base-indexed: bit (BASE + i) for unit i in [0..3].
 * Unit 1 is the lightmap; texcoord enable for unit 1 also gates the lightmap-specific code paths.
 */
public final class VertexKey {

    public static final int MAX_UNITS = 4;

    private final long packed;

    private static final int BIT_LIGHTING            = 0;
    private static final int BIT_LIGHT0              = 1;
    private static final int BIT_LIGHT1              = 2;
    private static final int BIT_LIGHT0_DIR          = 3;
    private static final int BIT_LIGHT1_DIR          = 4;
    private static final int BIT_COLOR_MATERIAL      = 5;
    private static final int BIT_SEPARATE_SPECULAR   = 6;
    private static final int BIT_FOG                 = 7;
    private static final int BIT_FOG_DIST_MODE       = 8;  // 2 bits: 8-9
    private static final int BIT_NORMALIZE           = 10;
    private static final int BIT_RESCALE_NORMAL      = 11;
    // Per-unit texcoord enable: bits 12-15 (unit 0..3). Unit 1 doubles as the lightmap flag.
    private static final int BIT_UNIT_TEX_BASE       = 12;
    private static final int BIT_HAS_VERTEX_COLOR    = 16;
    private static final int BIT_HAS_VERTEX_NORMAL   = 17;
    private static final int BIT_HAS_VERTEX_TEX      = 18;
    private static final int BIT_HAS_VERTEX_LIGHTMAP = 19;
    private static final int BIT_COLOR_MAT_MODE      = 20;  // 3 bits: 20-22
    // TexGen modes (unit 0 only, for now): 3 bits each (0=NONE, 1=OBJ_LINEAR, 2=EYE_LINEAR).
    private static final int BIT_TEXGEN_S            = 23;  // 3 bits: 23-25
    private static final int BIT_TEXGEN_T            = 26;  // 3 bits: 26-28
    private static final int BIT_TEXGEN_R            = 29;  // 3 bits: 29-31
    private static final int BIT_TEXGEN_Q            = 32;  // 3 bits: 32-34
    private static final int BIT_CLIP_PLANES         = 35;
    private static final int BIT_WIDE_LINE           = 36;
    // Per-unit texmat enable: bits 37-40 (unit 0..3). Unit 1 is the lightmap matrix.
    private static final int BIT_UNIT_TEXMAT_BASE    = 37;
    private static final int BIT_UNIT23_UV_FROM_UNIT0 = 41;

    public static final int TG_NONE                  = 0;
    public static final int TG_OBJ_LINEAR            = 1;
    public static final int TG_EYE_LINEAR            = 2;

    public static final int CM_AMBIENT               = 0;
    public static final int CM_DIFFUSE               = 1;
    public static final int CM_SPECULAR              = 2;
    public static final int CM_EMISSION              = 3;
    public static final int CM_AMBIENT_AND_DIFFUSE   = 4;

    private VertexKey(long packed) {
        this.packed = packed;
    }

    public long pack() { return packed; }

    public boolean lightingEnabled()       { return bit(BIT_LIGHTING); }
    public boolean light0Enabled()         { return bit(BIT_LIGHT0); }
    public boolean light1Enabled()         { return bit(BIT_LIGHT1); }
    public boolean light0Directional()     { return bit(BIT_LIGHT0_DIR); }
    public boolean light1Directional()     { return bit(BIT_LIGHT1_DIR); }
    public boolean colorMaterialEnabled()  { return bit(BIT_COLOR_MATERIAL); }
    public boolean separateSpecular()      { return bit(BIT_SEPARATE_SPECULAR); }
    public boolean fogEnabled()            { return bit(BIT_FOG); }
    public int fogDistanceMode()           { return (int)((packed >> BIT_FOG_DIST_MODE) & 0x3); }
    public boolean normalizeEnabled()      { return bit(BIT_NORMALIZE); }
    public boolean rescaleNormalsEnabled() { return bit(BIT_RESCALE_NORMAL); }
    public boolean unitTexCoordEnabled(int unit) { return bit(BIT_UNIT_TEX_BASE + unit); }
    public boolean unitTexMatEnabled(int unit)   { return bit(BIT_UNIT_TEXMAT_BASE + unit); }
    public boolean anyUnitTexCoordEnabled() { return ((packed >> BIT_UNIT_TEX_BASE) & 0xF) != 0; }
    public boolean lightmapEnabled()       { return unitTexCoordEnabled(1); }
    public boolean hasVertexColor()        { return bit(BIT_HAS_VERTEX_COLOR); }
    public boolean hasVertexNormal()       { return bit(BIT_HAS_VERTEX_NORMAL); }
    public boolean hasVertexTexCoord()     { return bit(BIT_HAS_VERTEX_TEX); }
    public boolean hasVertexLightmap()    { return bit(BIT_HAS_VERTEX_LIGHTMAP); }
    public boolean unit23UvFromUnit0()     { return bit(BIT_UNIT23_UV_FROM_UNIT0); }
    /** Color material mode (CM_AMBIENT, CM_DIFFUSE, CM_SPECULAR, CM_EMISSION, CM_AMBIENT_AND_DIFFUSE). Only meaningful when colorMaterialEnabled(). */
    public int colorMaterialMode()        { return (int)((packed >> BIT_COLOR_MAT_MODE) & 0x7); }

    /** TexGen mode for S coordinate (TG_NONE, TG_OBJ_LINEAR, TG_EYE_LINEAR). Unit 0 only; see TODO in packFromState. */
    public int texGenModeS()              { return (int)((packed >> BIT_TEXGEN_S) & 0x7); }
    public int texGenModeT()              { return (int)((packed >> BIT_TEXGEN_T) & 0x7); }
    public int texGenModeR()              { return (int)((packed >> BIT_TEXGEN_R) & 0x7); }
    public int texGenModeQ()              { return (int)((packed >> BIT_TEXGEN_Q) & 0x7); }
    public boolean texGenEnabled()        { return texGenModeS() != TG_NONE || texGenModeT() != TG_NONE || texGenModeR() != TG_NONE || texGenModeQ() != TG_NONE; }
    public boolean clipPlanesEnabled()    { return bit(BIT_CLIP_PLANES); }
    public boolean wideLineEmulation()   { return bit(BIT_WIDE_LINE); }

    public boolean cmReplacesAmbient()    { final int m = colorMaterialMode(); return m == CM_AMBIENT || m == CM_AMBIENT_AND_DIFFUSE; }
    public boolean cmReplacesDiffuse()    { final int m = colorMaterialMode(); return m == CM_DIFFUSE || m == CM_AMBIENT_AND_DIFFUSE; }
    public boolean cmReplacesSpecular()   { return colorMaterialMode() == CM_SPECULAR; }
    public boolean cmReplacesEmission()   { return colorMaterialMode() == CM_EMISSION; }

    private boolean bit(int pos) { return ((packed >> pos) & 1) != 0; }

    static int encodeTexGenMode(int glMode) {
        return switch (glMode) {
            case GL11.GL_OBJECT_LINEAR -> TG_OBJ_LINEAR;
            case GL11.GL_EYE_LINEAR -> TG_EYE_LINEAR;
            default -> TG_NONE;
        };
    }

    static int encodeColorMaterialMode(int glMode) {
        return switch (glMode) {
            case GL11.GL_AMBIENT -> CM_AMBIENT;
            case GL11.GL_DIFFUSE -> CM_DIFFUSE;
            case GL11.GL_SPECULAR -> CM_SPECULAR;
            case GL11.GL_EMISSION -> CM_EMISSION;
            case GL11.GL_AMBIENT_AND_DIFFUSE -> CM_AMBIENT_AND_DIFFUSE;
            default -> CM_AMBIENT_AND_DIFFUSE;
        };
    }

    public static long packFromState(boolean hasColor, boolean hasNormal, boolean hasTexCoord, boolean hasLightmap, int fragUnitMask) {
        long bits = 0;

        final boolean lighting = GLStateManager.getLightingState().isEnabled();
        if (lighting) {
            bits |= (1L << BIT_LIGHTING);

            if (GLStateManager.getLightStates()[0].isEnabled()) {
                bits |= (1L << BIT_LIGHT0);
                // position.w == 0 means directional
                if (GLStateManager.getLightDataStates()[0].position.w == 0.0f) {
                    bits |= (1L << BIT_LIGHT0_DIR);
                }
            }
            if (GLStateManager.getLightStates()[1].isEnabled()) {
                bits |= (1L << BIT_LIGHT1);
                if (GLStateManager.getLightDataStates()[1].position.w == 0.0f) {
                    bits |= (1L << BIT_LIGHT1_DIR);
                }
            }

            if (GLStateManager.getColorMaterial().isEnabled()) {
                bits |= (1L << BIT_COLOR_MATERIAL);
                final int cmMode = encodeColorMaterialMode(GLStateManager.getColorMaterialParameter().getValue());
                bits |= ((long) cmMode & 0x7) << BIT_COLOR_MAT_MODE;
            }

            if (GLStateManager.getLightModel().colorControl == GL12.GL_SEPARATE_SPECULAR_COLOR) {
                bits |= (1L << BIT_SEPARATE_SPECULAR);
            }
        }

        if (GLStateManager.getFogMode().isEnabled()) {
            bits |= (1L << BIT_FOG);
            final int fogDistMode = GLStateManager.getFogState().getFogDistanceMode();
            bits |= ((long) fogDistMode & 0x3) << BIT_FOG_DIST_MODE;
        }

        if (GLStateManager.getNormalizeState().isEnabled()) {
            bits |= (1L << BIT_NORMALIZE);
        }
        if (GLStateManager.getRescaleNormalState().isEnabled()) {
            bits |= (1L << BIT_RESCALE_NORMAL);
        }

        final TextureUnitArray texUnit = GLStateManager.getTextures();
        for (int i = 0; i < MAX_UNITS; i++) {
            if ((fragUnitMask & (1 << i)) != 0) {
                bits |= (1L << (BIT_UNIT_TEX_BASE + i));
            }
            if (!MatrixHelper.isIdentity(texUnit.getTextureUnitMatrix(i))) {
                bits |= (1L << (BIT_UNIT_TEXMAT_BASE + i));
            }
        }

        // TexGen (unit 0 only) - per-coordinate mode if enabled.
        // TODO: per-unit texgen for units 2/3. Deferred - no MC code path uses glTexGen on those.
        final TexGenState tg = texUnit.getTexGenState(0);
        boolean anyTexGen = false;
        if (texUnit.getTexGenSStates(0).isEnabled() && tg.getMode(GL11.GL_S) != 0) {
            final int m = encodeTexGenMode(tg.getMode(GL11.GL_S));
            bits |= ((long) m & 0x7) << BIT_TEXGEN_S;
            if (m != TG_NONE) anyTexGen = true;
        }
        if (texUnit.getTexGenTStates(0).isEnabled() && tg.getMode(GL11.GL_T) != 0) {
            final int m = encodeTexGenMode(tg.getMode(GL11.GL_T));
            bits |= ((long) m & 0x7) << BIT_TEXGEN_T;
            if (m != TG_NONE) anyTexGen = true;
        }
        if (texUnit.getTexGenRStates(0).isEnabled() && tg.getMode(GL11.GL_R) != 0) {
            final int m = encodeTexGenMode(tg.getMode(GL11.GL_R));
            bits |= ((long) m & 0x7) << BIT_TEXGEN_R;
            if (m != TG_NONE) anyTexGen = true;
        }
        if (texUnit.getTexGenQStates(0).isEnabled() && tg.getMode(GL11.GL_Q) != 0) {
            final int m = encodeTexGenMode(tg.getMode(GL11.GL_Q));
            bits |= ((long) m & 0x7) << BIT_TEXGEN_Q;
            if (m != TG_NONE) anyTexGen = true;
        }
        // Force unit-0 texture matrix on when texgen is active (end portal pattern always uses it).
        if (anyTexGen) {
            bits |= (1L << BIT_UNIT_TEXMAT_BASE);
        }

        // Vertex format flags
        if (hasColor) bits |= (1L << BIT_HAS_VERTEX_COLOR);
        if (hasNormal) bits |= (1L << BIT_HAS_VERTEX_NORMAL);
        if (hasTexCoord) bits |= (1L << BIT_HAS_VERTEX_TEX);
        if (hasLightmap) bits |= (1L << BIT_HAS_VERTEX_LIGHTMAP);

        if (GLStateManager.consumeUnit23TexCoordSetDuringDraw() && hasTexCoord) {
            bits |= (1L << BIT_UNIT23_UV_FROM_UNIT0);
        }

        if (GLStateManager.anyClipPlaneEnabled()) {
            bits |= (1L << BIT_CLIP_PLANES);
        }

        if (GLStateManager.wideLineEmulationActive) {
            bits |= (1L << BIT_WIDE_LINE);
        }

        return bits;
    }

    public static VertexKey fromState(boolean hasColor, boolean hasNormal, boolean hasTexCoord, boolean hasLightmap, int fragUnitMask) {
        return new VertexKey(packFromState(hasColor, hasNormal, hasTexCoord, hasLightmap, fragUnitMask));
    }

    static VertexKey fromPacked(long packed) {
        return new VertexKey(packed);
    }

    @Override
    public int hashCode() {
        return Long.hashCode(packed);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof VertexKey other)) return false;
        return packed == other.packed;
    }

    @Override
    public String toString() {
        return String.format("FFPVertexKey[0x%011X: lit=%b l0=%b l1=%b cm=%b fog=%b tex=%d%d%d%d texmat=%d%d%d%d col=%b nrm=%b vtex=%b vlm=%b tg=%d/%d/%d/%d clip=%b wline=%b]",
            packed, lightingEnabled(), light0Enabled(), light1Enabled(),
            colorMaterialEnabled(), fogEnabled(),
            unitTexCoordEnabled(0)?1:0, unitTexCoordEnabled(1)?1:0, unitTexCoordEnabled(2)?1:0, unitTexCoordEnabled(3)?1:0,
            unitTexMatEnabled(0)?1:0, unitTexMatEnabled(1)?1:0, unitTexMatEnabled(2)?1:0, unitTexMatEnabled(3)?1:0,
            hasVertexColor(), hasVertexNormal(), hasVertexTexCoord(), hasVertexLightmap(),
            texGenModeS(), texGenModeT(), texGenModeR(), texGenModeQ(), clipPlanesEnabled(), wideLineEmulation());
    }
}
