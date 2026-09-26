package com.gtnewhorizons.angelica.rendering.tesr;

import com.gtnewhorizons.angelica.glsm.GLStateManager;
import com.gtnewhorizons.angelica.glsm.ffp.FragmentKey;
import com.gtnewhorizons.angelica.glsm.hooks.GLSMConfig;
import com.gtnewhorizons.angelica.glsm.states.BlendState;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.coderbot.batchedentityrendering.impl.AngelicaBufferSource;
import net.minecraft.util.ResourceLocation;
import org.joml.Matrix4f;

import java.util.Arrays;

/** Reuses captured instance records across armor and item glint material passes. */
public final class GlintCapture {
    private static final class Range {
        RetainedTesrGroups.Group group;
        RetainedTesrGroups.InstanceColumns columns;
        RetainedTesrGroups.TexRun source;
        int start, end, instances;
    }

    private final ObjectArrayList<Range> ranges = new ObjectArrayList<>();
    private final Matrix4f parent = new Matrix4f();
    private final long[] fragment = new long[4], scratch = new long[4];
    private final BlendState blend = new BlendState(), scratchBlend = new BlendState();
    private boolean blendEnabled, depthMask;
    private int depthFunc;
    private int used, texture, color, light, overlay, fragmentLength;
    private long draws;
    private long pass;
    private RetainedTesrGroups owner;
    private boolean valid;
    private boolean base;
    private boolean trusted;
    private boolean poseRestored;
    private int restoredGeneration;
    private boolean uniformLight;

    public void reset() {
        if (owner != null && owner.glintCapture == this) owner.glintCapture = null;
        for (int i = 0; i < used; i++) {
            final Range range = ranges.get(i);
            range.group = null;
            range.columns = null;
            range.source = null;
        }
        used = 0;
        valid = false;
        owner = null;
        base = false;
        trusted = false;
        poseRestored = false;
    }

    boolean begin(RetainedTesrGroups groups) {
        if (!open(groups)) return false;
        parent.set(GLStateManager.getModelViewMatrix());
        texture = GLStateManager.getBoundTextureForServerState();
        color = currentColor();
        light = GLSMConfig.packedLastBrightness();
        overlay = currentOverlay();
        fragmentLength = FragmentKey.packFromState(fragment);
        GLStateManager.getEffectiveBlendState(blend);
        blendEnabled = GLStateManager.isEffectiveBlendEnabled();
        depthMask = GLStateManager.isEffectiveDepthMaskEnabled();
        depthFunc = GLStateManager.getDepthState().getFunc();
        return true;
    }

    /**
     * Base passes are only copied under the same parent pose. Light and overlay are recorded so the glint can draw the base instances in place when every part still carries them.
     */
    boolean beginBase(RetainedTesrGroups groups) {
        if (!open(groups)) return false;
        parent.set(GLStateManager.getModelViewMatrix());
        light = GLSMConfig.packedLastBrightness();
        overlay = currentOverlay();
        base = true;
        return true;
    }

    /** For records queued without running model or mod code; the caller replays before any GL state changes. */
    boolean beginTrusted(RetainedTesrGroups groups) {
        if (!open(groups)) return false;
        trusted = true;
        return true;
    }

    private boolean open(RetainedTesrGroups groups) {
        if (groups.glintCapture != null) return false;
        reset();
        owner = groups;
        pass = groups.captureVersion();
        groups.glintCapture = this;
        draws = GLStateManager.drawCalls;
        valid = true;
        uniformLight = true;
        return true;
    }

    void invalidate() { valid = false; }
    void allowBase() { base = true; }
    boolean capturesBase() { return base && valid; }

    void record(RetainedTesrGroups.Group group, RetainedTesrGroups.InstanceColumns columns, RetainedTesrGroups.TexRun source, int start, int end, int instances) {
        if (!valid) return;
        if (!base && group.material != EntityMaterials.GLINT && group.material != EntityMaterials.ITEM_GLINT) {
            invalidate();
            return;
        }
        Range range = used > 0 ? ranges.get(used - 1) : null;
        if (range == null || range.group != group || range.columns != columns || range.source != source || range.end != start) {
            if (used == ranges.size()) ranges.add(new Range());
            range = ranges.get(used++);
            range.group = group;
            range.columns = columns;
            range.source = source;
            range.start = start;
            range.instances = 0;
        }
        range.end = end;
        range.instances += instances;
        if (base && uniformLight) {
            for (int i = start; i < end; i++) {
                if (columns.lights.getInt(i) != light || columns.overlays.getInt(i) != overlay) {
                    uniformLight = false;
                    break;
                }
            }
        }
    }

    public void end() {
        close(false);
    }

    public void endWithRestoredPose() {
        close(true);
    }

    private void close(boolean poseRestored) {
        if (owner == null || owner.glintCapture != this) return;
        owner.glintCapture = null;
        valid &= GLStateManager.drawCalls == draws && used > 0;
        this.poseRestored = poseRestored;
        restoredGeneration = GLStateManager.getMvGeneration();
    }

    private boolean sameParentPose() {
        return poseRestored && restoredGeneration == GLStateManager.getMvGeneration() || parent.equals(GLStateManager.getModelViewMatrix());
    }

    boolean replay(RetainedTesrGroups groups) {
        return replay(groups, GLStateManager.getTextures().getTextureUnitMatrix(0));
    }

    boolean replay(RetainedTesrGroups groups, Matrix4f textureMatrix) {
        if (base || !valid || owner != groups || pass != groups.captureVersion() || groups.glintCapture != null || GLStateManager.drawCalls != draws) return false;
        if (!trusted && (blendEnabled != GLStateManager.isEffectiveBlendEnabled() || depthMask != GLStateManager.isEffectiveDepthMaskEnabled() || depthFunc != GLStateManager.getDepthState().getFunc()
            || !blend.sameAs(GLStateManager.getEffectiveBlendState(scratchBlend)) || !sameParentPose() || texture != GLStateManager.getBoundTextureForServerState() || color != currentColor() || light != GLSMConfig.packedLastBrightness()
            || fragmentLength != FragmentKey.packFromState(scratch) || !Arrays.equals(fragment, 0, fragmentLength, scratch, 0, fragmentLength))) return false;
        for (int i = 0; i < used; i++) {
            final Range range = ranges.get(i);
            if (!groups.captureStillValid(range.group)) return false;
        }
        for (int i = 0; i < used; i++) {
            final Range range = ranges.get(i);
            range.group.replayRange(range.columns, range.source, range.start, range.end, range.instances, textureMatrix);
        }
        valid = false;
        return true;
    }

    boolean copyBase(ModelPartBatcher batcher, RetainedTesrGroups groups) {
        if (!baseStillValid(groups) || EntityMaterials.fromCurrentState(false) != EntityMaterials.GLINT || !batcher.prepareArmorGlint()) return false;
        final boolean inPlace = sameLightAndOverlay();
        for (int i = 0; i < used; i++) {
            final Range range = ranges.get(i);
            batcher.copyArmorGlint(range.columns, range.start, range.end, inPlace);
        }
        valid = false;
        return true;
    }

    boolean queueBothLayers(ModelPartBatcher batcher, RetainedTesrGroups groups, ResourceLocation glintTexture) {
        if (!baseStillValid(groups) || !sameLightAndOverlay() || !batcher.prepareSkippedArmorGlint(glintTexture)) return false;
        for (int i = 0; i < used; i++) {
            final Range range = ranges.get(i);
            batcher.queueArmorGlintLayers(range.columns, range.source, range.start, range.end);
        }
        valid = false;
        return true;
    }

    private boolean baseStillValid(RetainedTesrGroups groups) {
        if (!valid || !base || owner != groups || pass != groups.captureVersion() || !groups.canCopyInstances(EntityMaterials.GLINT) || !sameParentPose()) return false;
        for (int i = 0; i < used; i++) {
            final Range range = ranges.get(i);
            if (!groups.captureStillValid(range.group) || range.end > range.columns.size) return false;
        }
        return true;
    }

    private boolean sameLightAndOverlay() {
        return uniformLight && light == GLSMConfig.packedLastBrightness() && overlay == currentOverlay();
    }

    private static int currentColor() {
        final var c = GLStateManager.getColor();
        return AngelicaBufferSource.packAbgr(c.getRed(), c.getGreen(), c.getBlue(), c.getAlpha());
    }

    private static int currentOverlay() {
        return AngelicaBufferSource.packAbgr(GLStateManager.getOverlayR(), GLStateManager.getOverlayG(), GLStateManager.getOverlayB(), GLStateManager.getOverlayA());
    }
}
