package com.gtnewhorizons.angelica.glsm;

import com.gtnewhorizons.angelica.glsm.DisplayListManager.RecordMode;
import com.gtnewhorizons.angelica.glsm.stacks.BooleanStateStack;
import com.gtnewhorizons.angelica.glsm.stacks.IntegerStateStack;
import com.gtnewhorizons.angelica.glsm.stacks.LineStateStack;
import com.gtnewhorizons.angelica.glsm.stacks.PointStateStack;
import com.gtnewhorizons.angelica.glsm.stacks.PolygonStateStack;
import com.gtnewhorizons.angelica.glsm.stacks.ScissorStateStack;
import com.gtnewhorizons.angelica.glsm.stacks.ViewPortStateStack;
import com.gtnewhorizons.angelica.glsm.states.ClipPlaneState;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL14;
import org.lwjgl.opengl.GL20;
import java.nio.ByteBuffer;
import java.nio.DoubleBuffer;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;

import static com.gtnewhorizons.angelica.glsm.GLStateManager.LOGGER;
import static com.gtnewhorizons.angelica.glsm.GLStateManager.MAX_CLIP_PLANES;
import static com.gtnewhorizons.angelica.glsm.GLStateManager.beforeUncapturedStateChange;
import static com.gtnewhorizons.angelica.glsm.GLStateManager.ctx;
import static com.gtnewhorizons.angelica.glsm.GLStateManager.guardUnsupportedFFP;
import static com.gtnewhorizons.angelica.glsm.GLStateManager.isCachingEnabled;
import static com.gtnewhorizons.angelica.glsm.GLStateManager.mod;
import static com.gtnewhorizons.angelica.glsm.GLStateManager.rejectExitCapture;
import static com.gtnewhorizons.angelica.glsm.backend.BackendManager.RENDER_BACKEND;

public final class GLSMRasterizer {

    private GLSMRasterizer() {}

    static float maxPointSize;

    // Line width range queried at init from GL_ALIASED_LINE_WIDTH_RANGE
    static float lineWidthMin = 1.0f;
    static float lineWidthMax = 1.0f;
    // The driver cannot draw wide lines (Mesa forward-compat, SDL_GPU), so FFP widens them from LineQuads in the vertex shader
    public static boolean wideLineEmulationEnabled = false;

    public static void glGetClipPlane(int plane, DoubleBuffer equation) {
        rejectExitCapture();
        final int index = plane - GL11.GL_CLIP_PLANE0;
        if (index < 0 || index >= MAX_CLIP_PLANES) return;
        ctx().clipPlaneState.getEyePlane(index, equation);
    }

    static void probeLineWidthRange() {
        final FloatBuffer lwRange = BufferUtils.createFloatBuffer(16);
        RENDER_BACKEND.getFloat(GL12.GL_ALIASED_LINE_WIDTH_RANGE, lwRange);
        lineWidthMin = lwRange.get(0);
        lineWidthMax = lwRange.get(1);
        final float queriedMax = lineWidthMax;
        // Probe whether the driver actually accepts the queried max (Mesa forward-compat rejects > 1.0)
        if (lineWidthMax > 1.0f) {
            while (RENDER_BACKEND.getError() != GL11.GL_NO_ERROR) {}
            RENDER_BACKEND.lineWidth(lineWidthMax);
            if (RENDER_BACKEND.getError() != GL11.GL_NO_ERROR) {
                // Binary search for the actual accepted max between 1.0 and queriedMax
                float lo = 1.0f, hi = queriedMax;
                while (hi - lo > 0.01f) {
                    final float mid = (lo + hi) * 0.5f;
                    while (RENDER_BACKEND.getError() != GL11.GL_NO_ERROR) {}
                    RENDER_BACKEND.lineWidth(mid);
                    if (RENDER_BACKEND.getError() == GL11.GL_NO_ERROR) {
                        lo = mid;
                    } else {
                        hi = mid;
                    }
                }
                lineWidthMax = lo;
            }
            RENDER_BACKEND.lineWidth(1.0f);
            while (RENDER_BACKEND.getError() != GL11.GL_NO_ERROR) {}
        }
        wideLineEmulationEnabled = lineWidthMax <= 1.0f;
        if (wideLineEmulationEnabled) {
            LOGGER.info("GL line width: aliased range [{}, {}], probe at {} rejected, native [{}, {}], line quads widen ({}, {}]", lineWidthMin, queriedMax, queriedMax, lineWidthMin, lineWidthMax, lineWidthMax, queriedMax);
        } else {
            LOGGER.info("GL line width: aliased range [{}, {}], probe at {} accepted, native [{}, {}]", lineWidthMin, queriedMax, queriedMax, lineWidthMin, lineWidthMax);
        }
    }

    static void probePointSizeRange(GLContextState glCtx) {
        final FloatBuffer range = glCtx.queryScratch;
        range.clear();
        RENDER_BACKEND.getFloat(GL11.GL_POINT_SIZE_RANGE, range);
        final float smooth = range.get(1);
        RENDER_BACKEND.getFloat(GL12.GL_ALIASED_POINT_SIZE_RANGE, range);
        maxPointSize = Math.max(smooth, range.get(1));
    }

    public static void enableScissorTest() {
        final RecordMode mode = DisplayListManager.getRecordMode();
        if (mode != RecordMode.NONE) {
            DisplayListManager.recordEnable(GL11.GL_SCISSOR_TEST);
            if (mode == RecordMode.COMPILE) {
                return;
            }
        }
        enableScissorTest(ctx());
    }

    static void enableScissorTest(GLContextState glCtx) {
        glCtx.scissorTest.enable();
    }

    public static void disableScissorTest() {
        final RecordMode mode = DisplayListManager.getRecordMode();
        if (mode != RecordMode.NONE) {
            DisplayListManager.recordDisable(GL11.GL_SCISSOR_TEST);
            if (mode == RecordMode.COMPILE) {
                return;
            }
        }
        disableScissorTest(ctx());
    }

    static void disableScissorTest(GLContextState glCtx) {
        glCtx.scissorTest.disable();
    }

    public static void glPointParameterf(int pname, float param) {
        pointParameter(pname, param, 0.0f, 0.0f);
    }

    public static void glPointParameteri(int pname, int param) {
        pointParameter(pname, param, 0.0f, 0.0f);
    }

    public static void glPointParameter(int pname, FloatBuffer params) {
        final int pos = params.position();
        final boolean attenuation = pname == GL14.GL_POINT_DISTANCE_ATTENUATION;
        pointParameter(pname, params.get(pos), attenuation ? params.get(pos + 1) : 0.0f, attenuation ? params.get(pos + 2) : 0.0f);
    }

    public static void glPointParameter(int pname, IntBuffer params) {
        final int pos = params.position();
        final boolean attenuation = pname == GL14.GL_POINT_DISTANCE_ATTENUATION;
        pointParameter(pname, params.get(pos), attenuation ? params.get(pos + 1) : 0.0f, attenuation ? params.get(pos + 2) : 0.0f);
    }

    private static void pointParameter(int pname, float v0, float v1, float v2) {
        rejectExitCapture();
        final GLContextState glCtx = ctx();
        final PointStateStack point = glCtx.pointState;
        switch (pname) {
            case GL14.GL_POINT_SIZE_MIN -> {
                if (!(v0 < 0.0f) && point.getMinSize() != v0) mod(point).setMinSize(v0);
            }
            case GL14.GL_POINT_SIZE_MAX -> {
                if (!(v0 < 0.0f) && point.getMaxSize() != v0) mod(point).setMaxSize(v0);
            }
            case GL14.GL_POINT_FADE_THRESHOLD_SIZE -> {
                if (!(v0 < 0.0f) && point.getFadeThreshold() != v0) {
                    mod(point).setFadeThreshold(v0);
                    RENDER_BACKEND.pointParameterf(pname, v0);
                }
            }
            case GL14.GL_POINT_DISTANCE_ATTENUATION -> {
                if (point.getAttenuationConstant() == v0 && point.getAttenuationLinear() == v1 && point.getAttenuationQuadratic() == v2) return;
                mod(point);
                point.setAttenuationConstant(v0);
                point.setAttenuationLinear(v1);
                point.setAttenuationQuadratic(v2);
            }
            case GL20.GL_POINT_SPRITE_COORD_ORIGIN -> {
                final int origin = (int) v0;
                if ((origin == GL20.GL_LOWER_LEFT || origin == GL20.GL_UPPER_LEFT) && point.getSpriteCoordOrigin() != origin) {
                    mod(point).setSpriteCoordOrigin(origin);
                    RENDER_BACKEND.pointParameteri(pname, origin);
                }
            }
            default -> RENDER_BACKEND.pointParameterf(pname, v0);
        }
    }

    static void applyPointState(GLContextState glCtx) {
        final PointStateStack point = glCtx.pointState;
        RENDER_BACKEND.pointSize(point.getSize());
        RENDER_BACKEND.pointParameterf(GL14.GL_POINT_FADE_THRESHOLD_SIZE, point.getFadeThreshold());
        RENDER_BACKEND.pointParameteri(GL20.GL_POINT_SPRITE_COORD_ORIGIN, point.getSpriteCoordOrigin());
    }

    private static boolean isLegacyHint(int target) {
        return switch (target) {
            case GL11.GL_PERSPECTIVE_CORRECTION_HINT, GL11.GL_POINT_SMOOTH_HINT, GL11.GL_FOG_HINT, GL14.GL_GENERATE_MIPMAP_HINT -> true;
            default -> false;
        };
    }

    static int hintShift(int target) {
        return switch (target) {
            case GL11.GL_PERSPECTIVE_CORRECTION_HINT -> 0;
            case GL11.GL_POINT_SMOOTH_HINT -> 2;
            case GL11.GL_LINE_SMOOTH_HINT -> 4;
            case GL11.GL_POLYGON_SMOOTH_HINT -> 6;
            case GL11.GL_FOG_HINT -> 8;
            case GL13.GL_TEXTURE_COMPRESSION_HINT -> 10;
            case GL14.GL_GENERATE_MIPMAP_HINT -> 12;
            case GL20.GL_FRAGMENT_SHADER_DERIVATIVE_HINT -> 14;
            default -> -1;
        };
    }

    static int hintMode(int packed, int shift) {
        return switch (packed >>> shift & 3) {
            case 1 -> GL11.GL_FASTEST;
            case 2 -> GL11.GL_NICEST;
            default -> GL11.GL_DONT_CARE;
        };
    }

    private static final int[] DRIVER_HINTS = {
        GL11.GL_LINE_SMOOTH_HINT, GL11.GL_POLYGON_SMOOTH_HINT, GL13.GL_TEXTURE_COMPRESSION_HINT, GL20.GL_FRAGMENT_SHADER_DERIVATIVE_HINT
    };

    static void applyHintState(GLContextState glCtx) {
        final int packed = glCtx.hintState.getValue();
        for (final int target : DRIVER_HINTS) {
            RENDER_BACKEND.hint(target, hintMode(packed, hintShift(target)));
        }
    }

    public static void enableCull() {
        final RecordMode mode = DisplayListManager.getRecordMode();
        if (mode != RecordMode.NONE) {
            DisplayListManager.recordEnable(GL11.GL_CULL_FACE);
            if (mode == RecordMode.COMPILE) {
                return;
            }
        }
        enableCull(ctx());
    }

    static void enableCull(GLContextState glCtx) {
        glCtx.cullState.enable();
    }

    public static void disableCull() {
        final RecordMode mode = DisplayListManager.getRecordMode();
        if (mode != RecordMode.NONE) {
            DisplayListManager.recordDisable(GL11.GL_CULL_FACE);
            if (mode == RecordMode.COMPILE) {
                return;
            }
        }
        disableCull(ctx());
    }

    static void disableCull(GLContextState glCtx) {
        glCtx.cullState.disable();
    }

    public static void glShadeModel(int mode) {
        final GLContextState glCtx = ctx();
        glCtx.exitCapture.write(ExitStateCapture.SHADE_MODEL);
        final RecordMode recordMode = DisplayListManager.getRecordMode();
        if (recordMode != RecordMode.NONE) {
            DisplayListManager.recordShadeModel(mode);
            if (recordMode == RecordMode.COMPILE) {
                return;
            }
        }
        final boolean caching = isCachingEnabled();
        final int oldValue = glCtx.shadeModelState.getValue();
        final boolean needsUpdate = !caching || oldValue != mode;

        if (needsUpdate) {
            mod(glCtx.shadeModelState).setValue(mode);
        }
    }

    public static void glViewport(int x, int y, int width, int height) {
        final RecordMode mode = DisplayListManager.getRecordMode();
        if (mode != RecordMode.NONE) {
            DisplayListManager.recordViewport(x, y, width, height);
            if (mode == RecordMode.COMPILE) {
                return;
            }
        }
        beforeUncapturedStateChange();
        RENDER_BACKEND.viewport(x, y, width, height);
        // Only update cached state when caching is enabled
        if (isCachingEnabled()) {
            mod(ctx().viewportState).setViewPort(x, y, width, height);
        }
    }

    public static void glLineWidth(float width) {
        rejectExitCapture();
        final GLContextState glCtx = ctx();
        final RecordMode mode = DisplayListManager.getRecordMode();
        if (mode != RecordMode.NONE) {
            DisplayListManager.recordLineWidth(width);
            if (mode == RecordMode.COMPILE) {
                return;
            }
        }
        final boolean caching = isCachingEnabled();
        if (!caching || glCtx.lineState.getWidth() != width) {
            mod(glCtx.lineState).setWidth(width);
            RENDER_BACKEND.lineWidth(Math.clamp(width, lineWidthMin, lineWidthMax));
        }
    }

    public static void glCullFace(int mode) {
        final GLContextState glCtx = ctx();
        glCtx.exitCapture.write(ExitStateCapture.CULL_FACE);
        final RecordMode recordMode = DisplayListManager.getRecordMode();
        if (recordMode != RecordMode.NONE) {
            DisplayListManager.recordCullFace(mode);
            if (recordMode == RecordMode.COMPILE) {
                return;
            }
        }
        final boolean caching = isCachingEnabled();
        if (!caching || glCtx.polygonState.getCullFaceMode() != mode) {
            mod(glCtx.polygonState).setCullFaceMode(mode);
            RENDER_BACKEND.cullFace(mode);
        }
    }

    public static void glFrontFace(int mode) {
        final GLContextState glCtx = ctx();
        glCtx.exitCapture.write(ExitStateCapture.FRONT_FACE);
        final RecordMode recordMode = DisplayListManager.getRecordMode();
        if (recordMode != RecordMode.NONE) {
            DisplayListManager.recordFrontFace(mode);
            if (recordMode == RecordMode.COMPILE) {
                return;
            }
        }
        final boolean caching = isCachingEnabled();
        if (!caching || glCtx.polygonState.getFrontFace() != mode) {
            mod(glCtx.polygonState).setFrontFace(mode);
            RENDER_BACKEND.frontFace(mode);
        }
    }

    public static void glHint(int target, int hint) {
        rejectExitCapture();
        if (hint != GL11.GL_NICEST && hint != GL11.GL_FASTEST && hint != GL11.GL_DONT_CARE) {
            return;
        }
        final RecordMode mode = DisplayListManager.getRecordMode();
        if (mode != RecordMode.NONE) {
            DisplayListManager.recordHint(target, hint);
            if (mode == RecordMode.COMPILE) {
                return;
            }
        }
        final int shift = hintShift(target);
        if (shift >= 0) {
            final IntegerStateStack hints = ctx().hintState;
            final int code = hint == GL11.GL_FASTEST ? 1 : hint == GL11.GL_NICEST ? 2 : 0;
            final int packed = hints.getValue() & ~(3 << shift) | code << shift;
            if (packed != hints.getValue()) mod(hints).setValue(packed);
        }
        if (!isLegacyHint(target)) {
            RENDER_BACKEND.hint(target, hint);
        }
    }

    public static void glLineStipple(int factor, short pattern) {
        rejectExitCapture();
        final GLContextState glCtx = ctx();
        final RecordMode mode = DisplayListManager.getRecordMode();
        if (mode != RecordMode.NONE) {
            DisplayListManager.recordLineStipple(factor, pattern);
            if (mode == RecordMode.COMPILE) {
                return;
            }
        }
        mod(glCtx.lineState);
        glCtx.lineState.setStippleFactor(factor);
        glCtx.lineState.setStipplePattern(pattern);
    }

    public static void glPointSize(float size) {
        rejectExitCapture();
        final GLContextState glCtx = ctx();
        final RecordMode mode = DisplayListManager.getRecordMode();
        if (mode != RecordMode.NONE) {
            DisplayListManager.recordPointSize(size);
            if (mode == RecordMode.COMPILE) {
                return;
            }
        }
        final boolean caching = isCachingEnabled();
        if (!caching || glCtx.pointState.getSize() != size) {
            mod(glCtx.pointState).setSize(size);
            RENDER_BACKEND.pointSize(size);
        }
    }

    public static void glPolygonMode(int face, int polygonMode) {
        rejectExitCapture();
        final GLContextState glCtx = ctx();
        final RecordMode mode = DisplayListManager.getRecordMode();
        if (mode != RecordMode.NONE) {
            DisplayListManager.recordPolygonMode(face, polygonMode);
            if (mode == RecordMode.COMPILE) {
                return;
            }
        }
        // Track front/back separately in cache (Mesa compat semantics), but always issue GL_FRONT_AND_BACK to the driver (core profile constraint).
        final boolean caching = isCachingEnabled();
        final boolean front = face != GL11.GL_BACK;
        final boolean back = face != GL11.GL_FRONT;
        final boolean needsUpdate = !caching
            || (front && glCtx.polygonState.getFrontMode() != polygonMode)
            || (back && glCtx.polygonState.getBackMode() != polygonMode);
        if (!needsUpdate) return;
        beforeUncapturedStateChange();
        if (caching) {
            mod(glCtx.polygonState);
            if (front) glCtx.polygonState.setFrontMode(polygonMode);
            if (back) glCtx.polygonState.setBackMode(polygonMode);
        }
        RENDER_BACKEND.polygonMode(GL11.GL_FRONT_AND_BACK, polygonMode);
    }

    public static void glPolygonOffset(float factor, float units) {
        final GLContextState glCtx = ctx();
        glCtx.exitCapture.write(ExitStateCapture.OFFSET_VALUES);
        final RecordMode mode = DisplayListManager.getRecordMode();
        if (mode != RecordMode.NONE) {
            DisplayListManager.recordPolygonOffset(factor, units);
            if (mode == RecordMode.COMPILE) {
                return;
            }
        }
        final boolean caching = isCachingEnabled();

        if (!caching || glCtx.polygonState.getOffsetFactor() != factor || glCtx.polygonState.getOffsetUnits() != units || glCtx.polygonState.getOffsetClamp() != 0.0f) {
            mod(glCtx.polygonState);
            glCtx.polygonState.setOffsetFactor(factor);
            glCtx.polygonState.setOffsetUnits(units);
            glCtx.polygonState.setOffsetClamp(0.0f);
            RENDER_BACKEND.polygonOffset(factor, units);
        }
    }

    public static void glPolygonOffsetClamp(float factor, float units, float clamp) {
        if (clamp == 0.0f) {
            glPolygonOffset(factor, units);
            return;
        }
        if (DisplayListManager.isRecording()) {
            throw DisplayListManager.unsupportedInList("glPolygonOffsetClamp");
        }
        final GLContextState glCtx = ctx();
        glCtx.exitCapture.write(ExitStateCapture.OFFSET_VALUES);
        final boolean caching = isCachingEnabled();
        if (!caching || glCtx.polygonState.getOffsetFactor() != factor || glCtx.polygonState.getOffsetUnits() != units || glCtx.polygonState.getOffsetClamp() != clamp) {
            mod(glCtx.polygonState);
            glCtx.polygonState.setOffsetFactor(factor);
            glCtx.polygonState.setOffsetUnits(units);
            glCtx.polygonState.setOffsetClamp(clamp);
            RENDER_BACKEND.polygonOffsetClamp(factor, units, clamp);
        }
    }

    public static void glScissor(int x, int y, int width, int height) {
        final RecordMode mode = DisplayListManager.getRecordMode();
        if (mode != RecordMode.NONE) {
            DisplayListManager.recordScissor(x, y, width, height);
            if (mode == RecordMode.COMPILE) {
                return;
            }
        }
        beforeUncapturedStateChange();
        RENDER_BACKEND.scissor(x, y, width, height);
        if (isCachingEnabled()) {
            mod(ctx().scissorState).setScissor(x, y, width, height);
        }
    }

    public static void glClipPlane(int plane, DoubleBuffer equation) {
        rejectExitCapture();
        final GLContextState glCtx = ctx();
        if (DisplayListManager.isRecording()) {
            final int pos = equation.position();
            DisplayListManager.recordClipPlane(plane, equation.get(pos), equation.get(pos + 1), equation.get(pos + 2), equation.get(pos + 3));
            return;
        }
        final int index = plane - GL11.GL_CLIP_PLANE0;
        if (index < 0 || index >= MAX_CLIP_PLANES) return;
        final int pos = equation.position();
        beforeUncapturedStateChange();
        glCtx.clipPlaneState.setPlane(index, equation.get(pos), equation.get(pos + 1), equation.get(pos + 2), equation.get(pos + 3), glCtx.modelViewMatrix);
        glCtx.clipPlaneGeneration++;
    }

    /** Returns true if any GL_CLIP_PLANE0..7 is currently enabled. */
    public static boolean anyClipPlaneEnabled() {
        return anyClipPlaneEnabled(ctx());
    }

    public static boolean anyClipPlaneEnabled(GLContextState glCtx) {
        return glCtx.clipPlaneEnabledMask != 0;
    }

    public static void glSampleCoverage(float value, boolean invert) {
        if (DisplayListManager.isRecording()) {
            throw DisplayListManager.unsupportedInList("glSampleCoverage");
        }
        beforeUncapturedStateChange();
        RENDER_BACKEND.sampleCoverage(value, invert);
    }

    public static void glPolygonStipple(ByteBuffer pattern) {
        guardUnsupportedFFP("glPolygonStipple", "glPolygonStipple is not available in GL 3.3 core profile.");
    }

    public static IntegerStateStack getShadeModelState() {
        return ctx().shadeModelState;
    }

    public static BooleanStateStack getScissorTest() {
        return ctx().scissorTest;
    }

    public static BooleanStateStack getCullState() {
        return ctx().cullState;
    }

    public static BooleanStateStack getDitherState() {
        return ctx().ditherState;
    }

    public static BooleanStateStack getLineSmoothState() {
        return ctx().lineSmoothState;
    }

    public static BooleanStateStack getLineStippleState() {
        return ctx().lineStippleState;
    }

    public static BooleanStateStack getPointSmoothState() {
        return ctx().pointSmoothState;
    }

    public static BooleanStateStack getPolygonSmoothState() {
        return ctx().polygonSmoothState;
    }

    public static BooleanStateStack getPolygonStippleState() {
        return ctx().polygonStippleState;
    }

    public static BooleanStateStack getMultisampleState() {
        return ctx().multisampleState;
    }

    public static BooleanStateStack getSampleAlphaToCoverageState() {
        return ctx().sampleAlphaToCoverageState;
    }

    public static BooleanStateStack getSampleAlphaToOneState() {
        return ctx().sampleAlphaToOneState;
    }

    public static BooleanStateStack getSampleCoverageState() {
        return ctx().sampleCoverageState;
    }

    public static BooleanStateStack getPolygonOffsetPointState() {
        return ctx().polygonOffsetPointState;
    }

    public static BooleanStateStack getPolygonOffsetLineState() {
        return ctx().polygonOffsetLineState;
    }

    public static BooleanStateStack getPolygonOffsetFillState() {
        return ctx().polygonOffsetFillState;
    }

    public static LineStateStack getLineState() {
        return ctx().lineState;
    }

    public static PointStateStack getPointState() {
        return ctx().pointState;
    }

    public static IntegerStateStack getHintState() {
        return ctx().hintState;
    }

    public static BooleanStateStack getPointSpriteState() {
        return ctx().pointSpriteState;
    }

    public static BooleanStateStack getEdgeFlagState() {
        return ctx().edgeFlagState;
    }

    public static PolygonStateStack getPolygonState() {
        return ctx().polygonState;
    }

    public static BooleanStateStack getAutoNormalState() {
        return ctx().autoNormalState;
    }

    public static BooleanStateStack getMap1Color4State() {
        return ctx().map1Color4State;
    }

    public static BooleanStateStack getMap1IndexState() {
        return ctx().map1IndexState;
    }

    public static BooleanStateStack getMap1NormalState() {
        return ctx().map1NormalState;
    }

    public static BooleanStateStack getMap1TextureCoord1State() {
        return ctx().map1TextureCoord1State;
    }

    public static BooleanStateStack getMap1TextureCoord2State() {
        return ctx().map1TextureCoord2State;
    }

    public static BooleanStateStack getMap1TextureCoord3State() {
        return ctx().map1TextureCoord3State;
    }

    public static BooleanStateStack getMap1TextureCoord4State() {
        return ctx().map1TextureCoord4State;
    }

    public static BooleanStateStack getMap1Vertex3State() {
        return ctx().map1Vertex3State;
    }

    public static BooleanStateStack getMap1Vertex4State() {
        return ctx().map1Vertex4State;
    }

    public static BooleanStateStack getMap2Color4State() {
        return ctx().map2Color4State;
    }

    public static BooleanStateStack getMap2IndexState() {
        return ctx().map2IndexState;
    }

    public static BooleanStateStack getMap2NormalState() {
        return ctx().map2NormalState;
    }

    public static BooleanStateStack getMap2TextureCoord1State() {
        return ctx().map2TextureCoord1State;
    }

    public static BooleanStateStack getMap2TextureCoord2State() {
        return ctx().map2TextureCoord2State;
    }

    public static BooleanStateStack getMap2TextureCoord3State() {
        return ctx().map2TextureCoord3State;
    }

    public static BooleanStateStack getMap2TextureCoord4State() {
        return ctx().map2TextureCoord4State;
    }

    public static BooleanStateStack getMap2Vertex3State() {
        return ctx().map2Vertex3State;
    }

    public static BooleanStateStack getMap2Vertex4State() {
        return ctx().map2Vertex4State;
    }

    public static BooleanStateStack[] getClipPlaneStates() {
        return ctx().clipPlaneStates;
    }

    public static ClipPlaneState getClipPlaneState() {
        return ctx().clipPlaneState;
    }

    public static ViewPortStateStack getViewportState() {
        return ctx().viewportState;
    }

    public static ScissorStateStack getScissorState() {
        return ctx().scissorState;
    }
}
