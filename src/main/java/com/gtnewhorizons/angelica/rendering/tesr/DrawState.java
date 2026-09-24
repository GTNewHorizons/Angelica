package com.gtnewhorizons.angelica.rendering.tesr;

import com.gtnewhorizons.angelica.api.tesr.TesrMaterial;
import com.gtnewhorizons.angelica.glsm.GLStateManager;
import it.unimi.dsi.fastutil.Hash;
import it.unimi.dsi.fastutil.objects.ObjectOpenCustomHashSet;
import lombok.Getter;
import org.lwjgl.opengl.GL11;

import javax.annotation.Nullable;

public final class DrawState {

    public static final int DISABLED = 0;
    public static final int CULL_BACK = 1;
    public static final int CULL_FRONT = 2;
    public static final int CULL_BOTH = 3;

    public static final int OPAQUE = 0;
    public static final int TRANSLUCENT = 1;
    public static final int ADDITIVE = 2;
    public static final int ADDITIVE_ALPHA = 3;
    public static final int GLINT = 4;

    private static final ObjectOpenCustomHashSet<DrawState> CACHE = new ObjectOpenCustomHashSet<>(HashStrategy.INSTANCE);

    static void clearInterning() {
        CACHE.clear();
    }

    static int internedStateCount() {
        return CACHE.size();
    }

    @Getter private final int cull;
    @Getter private final int blend;
    @Getter private final boolean depthEqual;
    @Getter private final boolean depthWrite;
    @Getter private final boolean colorWrite;
    @Getter private final float alphaCutoff;
    @Getter private final boolean lit;
    @Getter private final float offsetFactor;
    @Getter private final float offsetUnits;
    private final int hash;

    private DrawState(int cull, int blend, boolean depthEqual, boolean depthWrite, boolean colorWrite, float alphaCutoff, boolean lit, float offsetFactor, float offsetUnits) {
        this.cull = cull;
        this.blend = blend;
        this.depthEqual = depthEqual;
        this.depthWrite = depthWrite;
        this.colorWrite = colorWrite;
        this.alphaCutoff = alphaCutoff;
        this.lit = lit;
        this.offsetFactor = offsetFactor;
        this.offsetUnits = offsetUnits;
        int h = cull;
        h = h * 31 + blend;
        h = h * 31 + (depthEqual ? 1 : 0);
        h = h * 31 + (depthWrite ? 1 : 0);
        h = h * 31 + (colorWrite ? 1 : 0);
        h = h * 31 + Float.floatToIntBits(alphaCutoff);
        h = h * 31 + (lit ? 1 : 0);
        h = h * 31 + Float.floatToIntBits(offsetFactor);
        h = h * 31 + Float.floatToIntBits(offsetUnits);
        this.hash = h;
    }

    public static DrawState of(int cull, int blend, boolean depthEqual, boolean depthWrite, boolean colorWrite, float alphaCutoff, boolean lit, float offsetFactor, float offsetUnits) {
        return CACHE.addOrGet(new DrawState(cull, blend, depthEqual, depthWrite, colorWrite, alphaCutoff, lit, offsetFactor, offsetUnits));
    }

    public static DrawState forMaterial(TesrMaterial material, int cull, boolean lit, float offsetFactor, float offsetUnits) {
        return of(material.isNoCull() ? DISABLED : cull,
            blendFor(material.transparency()),
            material.isDepthEqual(),
            !material.isNoDepthWrite(),
            !material.isDepthOnly(),
            material.cutoutAlpha(),
            lit && !material.isUnlit(),
            offsetFactor, offsetUnits);
    }

    public static int blendFor(TesrMaterial.Transparency transparency) {
        return switch (transparency) {
            case TRANSLUCENT -> TRANSLUCENT;
            case ADDITIVE -> ADDITIVE;
            case ADDITIVE_ALPHA -> ADDITIVE_ALPHA;
            case GLINT -> GLINT;
            case OPAQUE -> OPAQUE;
        };
    }

    public static int packCull(boolean enabled, int faceMode) {
        if (!enabled) return DISABLED;
        return switch (faceMode) {
            case GL11.GL_FRONT -> CULL_FRONT;
            case GL11.GL_FRONT_AND_BACK -> CULL_BOTH;
            default -> CULL_BACK;
        };
    }

    public static int liveCull() {
        return packCull(GLStateManager.getCullState().isEnabled(), GLStateManager.getPolygonState().getCullFaceMode());
    }

    public static boolean liveLit(TesrMaterial material) {
        return GLStateManager.getLightingState().isEnabled() && !material.isUnlit();
    }

    public void apply() {
        switch (blend) {
            case TRANSLUCENT -> {
                GLStateManager.enableBlend();
                GLStateManager.tryBlendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, GL11.GL_ONE, GL11.GL_ONE_MINUS_SRC_ALPHA);
            }
            case ADDITIVE -> {
                GLStateManager.enableBlend();
                GLStateManager.glBlendFunc(GL11.GL_ONE, GL11.GL_ONE);
            }
            case ADDITIVE_ALPHA -> {
                GLStateManager.enableBlend();
                GLStateManager.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE);
            }
            case GLINT -> {
                GLStateManager.enableBlend();
                GLStateManager.tryBlendFuncSeparate(GL11.GL_SRC_COLOR, GL11.GL_ONE, GL11.GL_ZERO, GL11.GL_ONE);
            }
            default -> GLStateManager.disableBlend();
        }

        GLStateManager.enableDepthTest();
        GLStateManager.glDepthFunc(depthEqual ? GL11.GL_EQUAL : GL11.GL_LEQUAL);
        GLStateManager.glDepthMask(depthWrite);
        GLStateManager.glColorMask(colorWrite, colorWrite, colorWrite, colorWrite);

        if (alphaCutoff > 0.0f) {
            GLStateManager.enableAlphaTest();
            GLStateManager.glAlphaFunc(GL11.GL_GREATER, alphaCutoff);
        } else {
            GLStateManager.disableAlphaTest();
        }

        if (lit) {
            GLStateManager.enableLighting();
        } else {
            GLStateManager.disableLighting();
        }

        switch (cull) {
            case CULL_BACK -> {
                GLStateManager.enableCull();
                GLStateManager.glCullFace(GL11.GL_BACK);
            }
            case CULL_FRONT -> {
                GLStateManager.enableCull();
                GLStateManager.glCullFace(GL11.GL_FRONT);
            }
            case CULL_BOTH -> {
                GLStateManager.enableCull();
                GLStateManager.glCullFace(GL11.GL_FRONT_AND_BACK);
            }
            default -> GLStateManager.disableCull();
        }

        if (offsetFactor != 0.0f || offsetUnits != 0.0f) {
            GLStateManager.glEnable(GL11.GL_POLYGON_OFFSET_FILL);
            GLStateManager.glPolygonOffset(offsetFactor, offsetUnits);
        } else {
            GLStateManager.glDisable(GL11.GL_POLYGON_OFFSET_FILL);
            GLStateManager.glPolygonOffset(0.0f, 0.0f);
        }
    }

    @Override
    public boolean equals(@Nullable Object o) {
        return this == o;
    }

    @Override
    public int hashCode() {
        return hash;
    }

    @Override
    public String toString() {
        return "DrawState[cull=" + cull + ", blend=" + blend + ", depthEqual=" + depthEqual + ", depthWrite=" + depthWrite + ", colorWrite=" + colorWrite + ", alphaCutoff=" + alphaCutoff + ", lit=" + lit + ", offset=" + offsetFactor + '/' + offsetUnits + ']';
    }

    private enum HashStrategy implements Hash.Strategy<DrawState> {
        INSTANCE;

        @Override
        public int hashCode(@Nullable DrawState state) {
            return state == null ? 0 : state.hash;
        }

        @Override
        public boolean equals(@Nullable DrawState a, @Nullable DrawState b) {
            if (a == b) return true;
            if (a == null || b == null) return false;
            return a.cull == b.cull && a.blend == b.blend && a.depthEqual == b.depthEqual && a.depthWrite == b.depthWrite && a.colorWrite == b.colorWrite && Float.floatToIntBits(a.alphaCutoff) == Float.floatToIntBits(b.alphaCutoff) && a.lit == b.lit && Float.floatToIntBits(a.offsetFactor) == Float.floatToIntBits(b.offsetFactor) && Float.floatToIntBits(a.offsetUnits) == Float.floatToIntBits(b.offsetUnits);
        }
    }
}
