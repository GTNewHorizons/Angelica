package com.gtnewhorizons.angelica.glsm.stacks;

import com.gtnewhorizons.angelica.glsm.ClipPlaneBooleanState;
import com.gtnewhorizons.angelica.glsm.states.TexEnvState;

/**
 *            __n__n__
 *     .------`-\00/-'
 *    /  ##  ## (oo)    Casts look redundant, they aren't. Casting to the
 *   / \## __   ./      final class avoids an interface lookup on every
 *      |//YY \|/       call. 74-85% faster push/pops in JMH testing.
 *      |||   |||
 *    Copy On Write Dispatch
 */
@SuppressWarnings("RedundantCast")
public final class CowDispatch {

    private CowDispatch() {}

    public static final byte BLEND = 0;
    public static final byte BOOLEAN = 1;
    public static final byte CLIP_PLANE_BOOLEAN = 2;
    public static final byte TEXTURE_UNIT_BOOLEAN = 3;
    public static final byte COLOR_MASK = 4;
    public static final byte LIGHT_STATE = 5;
    public static final byte MATERIAL = 6;
    public static final byte STENCIL = 7;
    public static final byte MATRIX_MODE = 8;
    public static final byte LIGHT_MODEL = 9;
    public static final byte ALPHA = 10;
    public static final byte POINT = 11;
    public static final byte FOG = 12;
    public static final byte VEC4F = 13;
    public static final byte VEC3F = 14;
    public static final byte INTEGER = 15;
    public static final byte DEPTH = 16;
    public static final byte VIEWPORT = 17;
    public static final byte POLYGON = 18;
    public static final byte COLOR4 = 19;
    public static final byte LINE = 20;
    public static final byte TEXTURE_BINDING = 21;
    public static final byte TEX_ENV = 22;

    public static byte kindOf(CowStateStack<?> s) {
        final Class<?> c = s.getClass();
        if (c == TextureUnitBooleanStateStack.class) return TEXTURE_UNIT_BOOLEAN;
        if (c == ClipPlaneBooleanState.class) return CLIP_PLANE_BOOLEAN;
        if (c == BooleanStateStack.class) return BOOLEAN;
        if (c == BlendStateStack.class) return BLEND;
        if (c == ColorMaskStack.class) return COLOR_MASK;
        if (c == LightStateStack.class) return LIGHT_STATE;
        if (c == MaterialStateStack.class) return MATERIAL;
        if (c == StencilStateStack.class) return STENCIL;
        if (c == MatrixModeStack.class) return MATRIX_MODE;
        if (c == LightModelStateStack.class) return LIGHT_MODEL;
        if (c == AlphaStateStack.class) return ALPHA;
        if (c == PointStateStack.class) return POINT;
        if (c == FogStateStack.class) return FOG;
        if (c == Vec4fStack.class) return VEC4F;
        if (c == Vec3fStack.class) return VEC3F;
        if (c == IntegerStateStack.class) return INTEGER;
        if (c == DepthStateStack.class) return DEPTH;
        if (c == ViewPortStateStack.class) return VIEWPORT;
        if (c == PolygonStateStack.class) return POLYGON;
        if (c == Color4Stack.class) return COLOR4;
        if (c == LineStateStack.class) return LINE;
        if (c == TextureBindingStack.class) return TEXTURE_BINDING;
        if (c == TexEnvState.class) return TEX_ENV;
        throw new IllegalStateException("Unknown CowStateStack class: " + c.getName());
    }

    public static boolean topSlotChanged(byte kind, CowStateStack<?> s) {
        return switch (kind) {
            case BLEND -> ((BlendStateStack) s).topSlotChanged();
            case BOOLEAN -> ((BooleanStateStack) s).topSlotChanged();
            case CLIP_PLANE_BOOLEAN -> ((ClipPlaneBooleanState) s).topSlotChanged();
            case TEXTURE_UNIT_BOOLEAN -> ((TextureUnitBooleanStateStack) s).topSlotChanged();
            case COLOR_MASK -> ((ColorMaskStack) s).topSlotChanged();
            case LIGHT_STATE -> ((LightStateStack) s).topSlotChanged();
            case MATERIAL -> ((MaterialStateStack) s).topSlotChanged();
            case STENCIL -> ((StencilStateStack) s).topSlotChanged();
            case MATRIX_MODE -> ((MatrixModeStack) s).topSlotChanged();
            case LIGHT_MODEL -> ((LightModelStateStack) s).topSlotChanged();
            case ALPHA -> ((AlphaStateStack) s).topSlotChanged();
            case POINT -> ((PointStateStack) s).topSlotChanged();
            case FOG -> ((FogStateStack) s).topSlotChanged();
            case VEC4F -> ((Vec4fStack) s).topSlotChanged();
            case VEC3F -> ((Vec3fStack) s).topSlotChanged();
            case INTEGER -> ((IntegerStateStack) s).topSlotChanged();
            case DEPTH -> ((DepthStateStack) s).topSlotChanged();
            case VIEWPORT -> ((ViewPortStateStack) s).topSlotChanged();
            case POLYGON -> ((PolygonStateStack) s).topSlotChanged();
            case COLOR4 -> ((Color4Stack) s).topSlotChanged();
            case LINE -> ((LineStateStack) s).topSlotChanged();
            case TEXTURE_BINDING -> ((TextureBindingStack) s).topSlotChanged();
            case TEX_ENV -> ((TexEnvState) s).topSlotChanged();
            default -> throw new IllegalStateException("Unknown kind: " + kind);
        };
    }

    public static void restoreSlot(byte kind, CowStateStack<?> s, int slot) {
        switch (kind) {
            case BLEND -> ((BlendStateStack) s).restoreSlot(slot);
            case BOOLEAN -> ((BooleanStateStack) s).restoreSlot(slot);
            case CLIP_PLANE_BOOLEAN -> ((ClipPlaneBooleanState) s).restoreSlot(slot);
            case TEXTURE_UNIT_BOOLEAN -> ((TextureUnitBooleanStateStack) s).restoreSlot(slot);
            case COLOR_MASK -> ((ColorMaskStack) s).restoreSlot(slot);
            case LIGHT_STATE -> ((LightStateStack) s).restoreSlot(slot);
            case MATERIAL -> ((MaterialStateStack) s).restoreSlot(slot);
            case STENCIL -> ((StencilStateStack) s).restoreSlot(slot);
            case MATRIX_MODE -> ((MatrixModeStack) s).restoreSlot(slot);
            case LIGHT_MODEL -> ((LightModelStateStack) s).restoreSlot(slot);
            case ALPHA -> ((AlphaStateStack) s).restoreSlot(slot);
            case POINT -> ((PointStateStack) s).restoreSlot(slot);
            case FOG -> ((FogStateStack) s).restoreSlot(slot);
            case VEC4F -> ((Vec4fStack) s).restoreSlot(slot);
            case VEC3F -> ((Vec3fStack) s).restoreSlot(slot);
            case INTEGER -> ((IntegerStateStack) s).restoreSlot(slot);
            case DEPTH -> ((DepthStateStack) s).restoreSlot(slot);
            case VIEWPORT -> ((ViewPortStateStack) s).restoreSlot(slot);
            case POLYGON -> ((PolygonStateStack) s).restoreSlot(slot);
            case COLOR4 -> ((Color4Stack) s).restoreSlot(slot);
            case LINE -> ((LineStateStack) s).restoreSlot(slot);
            case TEXTURE_BINDING -> ((TextureBindingStack) s).restoreSlot(slot);
            case TEX_ENV -> ((TexEnvState) s).restoreSlot(slot);
            default -> throw new IllegalStateException("Unknown kind: " + kind);
        }
    }
}
