package com.gtnewhorizons.angelica.glsm;

import com.gtnewhorizons.angelica.glsm.recording.commands.DisplayListCommand;
import com.gtnewhorizons.angelica.glsm.stacks.CowStateStack;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import org.lwjgl.opengl.GL11;

public final class StateSet {

    public static final int R_DEPTH = 1;
    public static final int R_BLEND = 1 << 1;
    public static final int R_COLOR_MASK = 1 << 2;
    public static final int R_CLEAR_COLOR = 1 << 3;
    public static final int R_DRAW_BUFFER = 1 << 4;
    public static final int R_LOGIC_OP = 1 << 5;
    public static final int R_SECONDARY_COLOR = 1 << 6;
    public static final int R_STENCIL = 1 << 7;
    public static final int R_VIEWPORT = 1 << 8;
    public static final int R_LINE = 1 << 9;
    public static final int R_POINT = 1 << 10;
    public static final int R_POLYGON = 1 << 11;
    public static final int R_TEXTURE = 1 << 12;
    public static final int R_ACTIVE_UNIT = 1 << 13;
    public static final int R_PROGRAM = 1 << 14;
    public static final int R_SCISSOR = 1 << 15;

    public static final int B_FRAG_COLORBUF = 1;
    public static final int B_FRAG_TEXTURE = 1 << 1;
    public static final int B_FRAG_FOG = 1 << 2;
    public static final int B_CURRENT = 1 << 3;
    public static final int B_LIGHTING = 1 << 4;
    public static final int B_TRANSFORM = 1 << 5;

    private static final long[] NO_MEMBERS = new long[0];
    private static final Int2ObjectMap<StateSet> maskToStateSetMap = new Int2ObjectOpenHashMap<>();

    public final int glMask;
    public final long[] members;
    public final int restore;
    public final int bump;
    public final boolean blendSnapshot;
    public final DisplayListCommand pushCommand;

    StateSet(int glMask, long[] members, int restore, int bump, boolean blendSnapshot) {
        this.glMask = glMask;
        this.members = members;
        this.restore = restore;
        this.bump = bump;
        this.blendSnapshot = blendSnapshot;
        this.pushCommand = () -> GLStateManager.pushStateLive(this);
    }

    public boolean contains(int id) {
        if (id < 0) return false;
        final int word = id >> 6;
        return word < members.length && (members[word] & (1L << id)) != 0;
    }

    private static long[] ids(CowStateStack<?>... stacks) {
        if (stacks.length == 0) return NO_MEMBERS;
        int max = 0;
        for (CowStateStack<?> s : stacks) max = Math.max(max, s.stackId());
        final long[] bits = new long[(max >> 6) + 1];
        for (CowStateStack<?> s : stacks) {
            final int id = s.stackId();
            bits[id >> 6] |= 1L << id;
        }
        return bits;
    }

    public static final StateSet BATCH = buildBatch();
    public static final StateSet FONT = buildFont();
    public static final StateSet FONT_PIPELINE = buildFontPipeline();
    public static final StateSet HAND = buildHand();
    public static final StateSet BLEND = buildBlend();
    public static final StateSet ALPHA = buildAlpha();
    public static final StateSet CUTOUT = buildCutout();
    public static final StateSet MASKS = buildMasks();
    public static final StateSet CULL = buildCull();

    private static StateSet buildBatch() {
        return new StateSet(0,
            ids(
                GLStateManager.getBlendState(), GLStateManager.getAlphaState(), GLStateManager.getDepthState(),
                GLStateManager.getColorMask(), GLStateManager.getPolygonState(),
                GLStateManager.getTextures().getTextureUnitBindings(0), GLStateManager.getTextures().getTextureUnitBindings(1),
                GLStateManager.getActiveTextureUnitStack(),
                GLStateManager.getBlendMode(), GLStateManager.getAlphaTest(), GLStateManager.getDepthTest(),
                GLStateManager.getLightingState(), GLStateManager.getCullState(), GLStateManager.getPolygonOffsetFillState(),
                GLStateManager.getTextures().getTextureUnitStates(0)
            ),
            R_BLEND | R_DEPTH | R_COLOR_MASK | R_POLYGON | R_TEXTURE | R_ACTIVE_UNIT,
            B_FRAG_COLORBUF,
            true);
    }

    private static StateSet buildFont() {
        return new StateSet(0,
            ids(
                GLStateManager.getBlendState(), GLStateManager.getDepthState(), GLStateManager.getShadeModelState(),
                GLStateManager.getTextures().getTextureUnitBindings(0), GLStateManager.getTextures().getTextureUnitBindings(1),
                GLStateManager.getActiveTextureUnitStack(), GLStateManager.getProgramStack(),
                GLStateManager.getBlendMode(), GLStateManager.getAlphaTest(), GLStateManager.getDepthTest(),
                GLStateManager.getTextures().getTextureUnitStates(0)
            ),
            R_BLEND | R_DEPTH | R_TEXTURE | R_ACTIVE_UNIT | R_PROGRAM,
            0,
            true);
    }

    private static StateSet buildFontPipeline() {
        return new StateSet(0,
            ids(
                GLStateManager.getBlendState(), GLStateManager.getDepthState(),
                GLStateManager.getTextures().getTextureUnitBindings(0), GLStateManager.getTextures().getTextureUnitBindings(1),
                GLStateManager.getActiveTextureUnitStack(),
                GLStateManager.getBlendMode(), GLStateManager.getAlphaTest(), GLStateManager.getDepthTest(),
                GLStateManager.getTextures().getTextureUnitStates(0)
            ),
            R_BLEND | R_DEPTH | R_TEXTURE | R_ACTIVE_UNIT,
            0,
            true);
    }

    private static StateSet buildHand() {
        return new StateSet(0,
            ids(
                GLStateManager.getBlendState(), GLStateManager.getAlphaState(), GLStateManager.getDepthState(),
                GLStateManager.getBlendMode(), GLStateManager.getAlphaTest(), GLStateManager.getTextures().getTextureUnitStates(1)
            ),
            R_BLEND | R_DEPTH,
            B_FRAG_COLORBUF,
            true);
    }

    private static StateSet buildBlend() {
        return new StateSet(0,
            ids(GLStateManager.getBlendState(), GLStateManager.getBlendMode()),
            R_BLEND,
            0,
            true);
    }

    private static StateSet buildAlpha() {
        return new StateSet(0,
            ids(GLStateManager.getAlphaState(), GLStateManager.getAlphaTest()),
            0,
            B_FRAG_COLORBUF,
            false);
    }

    private static StateSet buildCutout() {
        return new StateSet(0,
            ids(GLStateManager.getAlphaState(), GLStateManager.getAlphaTest(), GLStateManager.getTextures().getTextureUnitStates(1)),
            0,
            B_FRAG_COLORBUF,
            false);
    }

    private static StateSet buildMasks() {
        return new StateSet(0,
            ids(GLStateManager.getDepthState(), GLStateManager.getColorMask()),
            R_DEPTH | R_COLOR_MASK,
            0,
            false);
    }

    private static StateSet buildCull() {
        return new StateSet(0,
            ids(GLStateManager.getPolygonState(), GLStateManager.getCullState()),
            R_POLYGON,
            0,
            false);
    }

    public static StateSet forMask(int mask) {
        StateSet cached = maskToStateSetMap.get(mask);
        if (cached != null) {
            return cached;
        }
        cached = buildForMask(mask);
        maskToStateSetMap.put(mask, cached);
        return cached;
    }

    private static StateSet buildForMask(int mask) {
        int restore = 0;
        int bump = 0;
        final boolean blendSnapshot = (mask & (GL11.GL_COLOR_BUFFER_BIT | GL11.GL_ENABLE_BIT)) != 0;

        if ((mask & GL11.GL_DEPTH_BUFFER_BIT) != 0) {
            restore |= R_DEPTH;
        }
        if ((mask & GL11.GL_COLOR_BUFFER_BIT) != 0) {
            restore |= R_BLEND | R_COLOR_MASK | R_CLEAR_COLOR | R_DRAW_BUFFER | R_LOGIC_OP;
            bump |= B_FRAG_COLORBUF;
        }
        if ((mask & GL11.GL_CURRENT_BIT) != 0) {
            restore |= R_SECONDARY_COLOR;
            bump |= B_CURRENT;
        }
        if ((mask & GL11.GL_STENCIL_BUFFER_BIT) != 0) {
            restore |= R_STENCIL;
        }
        if ((mask & GL11.GL_VIEWPORT_BIT) != 0) {
            restore |= R_VIEWPORT;
        }
        if ((mask & GL11.GL_SCISSOR_BIT) != 0) {
            restore |= R_SCISSOR;
        }
        if ((mask & GL11.GL_LINE_BIT) != 0) {
            restore |= R_LINE;
        }
        if ((mask & GL11.GL_POINT_BIT) != 0) {
            restore |= R_POINT;
        }
        if ((mask & GL11.GL_POLYGON_BIT) != 0) {
            restore |= R_POLYGON;
        }
        if ((mask & GL11.GL_TEXTURE_BIT) != 0) {
            restore |= R_TEXTURE | R_ACTIVE_UNIT;
            bump |= B_FRAG_TEXTURE;
        }
        if ((mask & GL11.GL_LIGHTING_BIT) != 0) {
            bump |= B_LIGHTING;
        }
        if ((mask & GL11.GL_FOG_BIT) != 0) {
            bump |= B_FRAG_FOG;
        }
        if ((mask & GL11.GL_TRANSFORM_BIT) != 0) {
            bump |= B_TRANSFORM;
        }

        final long[] members = Feature.maskToMembers(mask);

        return new StateSet(mask, members, restore, bump, blendSnapshot);
    }
}
