package com.gtnewhorizons.angelica.glsm;

import com.gtnewhorizon.gtnhlib.client.renderer.vertex.VertexFormatElement.Usage;
import com.gtnewhorizons.angelica.glsm.DisplayListManager.RecordMode;
import com.gtnewhorizons.angelica.glsm.ffp.InstancedAttribs;
import com.gtnewhorizons.angelica.glsm.ffp.ShaderManager;
import com.gtnewhorizons.angelica.glsm.hooks.GLSMHooks;
import com.gtnewhorizons.angelica.glsm.hooks.GlintColorHandler;
import com.gtnewhorizons.angelica.glsm.hooks.ImmediateExtendedAttribHandler;
import com.gtnewhorizons.angelica.glsm.recording.ImmediateModeRecorder;
import com.gtnewhorizons.angelica.glsm.stacks.BooleanStateStack;
import com.gtnewhorizons.angelica.glsm.stacks.Color4Stack;
import com.gtnewhorizons.angelica.glsm.streaming.TessellatorEntityIds;
import org.lwjgl.opengl.GL13;

import static com.gtnewhorizons.angelica.glsm.GLStateManager.beforeUncapturedStateChange;
import static com.gtnewhorizons.angelica.glsm.GLStateManager.ctx;
import static com.gtnewhorizons.angelica.glsm.GLStateManager.isCachingEnabled;
import static com.gtnewhorizons.angelica.glsm.GLStateManager.isMainThread;
import static com.gtnewhorizons.angelica.glsm.GLStateManager.mod;
import static com.gtnewhorizons.angelica.glsm.GLStateManager.rejectExitCapture;
import static com.gtnewhorizons.angelica.glsm.backend.BackendManager.RENDER_BACKEND;

public final class GLSMCurrentAttribs {

    private GLSMCurrentAttribs() {}

    public static void setOverlayColor(float r, float g, float b, float a) {
        final GLContextState glCtx = ctx();
        glCtx.exitCapture.write(ExitStateCapture.OVERLAY_COLOR);
        if (r == glCtx.overlayR && g == glCtx.overlayG && b == glCtx.overlayB && a == glCtx.overlayA) return;
        glCtx.overlayR = r; glCtx.overlayG = g; glCtx.overlayB = b; glCtx.overlayA = a;
        glCtx.fragmentGeneration++;
    }

    public static void setShaderColor(float r, float g, float b, float a) {
        final GLContextState glCtx = ctx();
        glCtx.exitCapture.write(ExitStateCapture.SHADER_COLOR);
        if (r == glCtx.shaderColorR && g == glCtx.shaderColorG && b == glCtx.shaderColorB && a == glCtx.shaderColorA) return;
        glCtx.shaderColorR = r; glCtx.shaderColorG = g; glCtx.shaderColorB = b; glCtx.shaderColorA = a;
        if (GLSMHooks.SHADER_COLOR_CHANGE.hasListeners()) {
            GLSMHooks.shaderColorChangeEvent.red = r;
            GLSMHooks.shaderColorChangeEvent.green = g;
            GLSMHooks.shaderColorChangeEvent.blue = b;
            GLSMHooks.shaderColorChangeEvent.alpha = a;
            GLSMHooks.SHADER_COLOR_CHANGE.post(GLSMHooks.shaderColorChangeEvent);
        }
    }


    public static boolean consumeUnit23TexCoordSetDuringDraw(GLContextState glCtx) {
        boolean v = glCtx.unit23TexCoordSetDuringDraw;
        glCtx.unit23TexCoordSetDuringDraw = false;
        return v;
    }

    public static void forceAttribDefaultsDirty() {
        final GLContextState glCtx = ctx();
        glCtx.dirtyNormalAttrib = true;
        glCtx.dirtyTexCoordAttrib = true;
    }

    // has* bits mark attribs the VBO already supplies; FFP supplies the rest via u_Current* uniforms.
    // Skip the backend vertexAttrib call in either case
    public static void flushDeferredVertexAttribs(GLContextState glCtx, boolean hasColor, boolean hasNormal, boolean hasTexCoord, boolean hasLightmap) {
        final ShaderManager sm = glCtx.ffp;
        final boolean ffpWillHandle = sm.isActive() || (sm.isEnabled() && glCtx.activeProgram == 0);
        if (!hasColor && glCtx.dirtyColorAttrib && !ffpWillHandle) {
            RENDER_BACKEND.vertexAttrib4f(Usage.COLOR.getAttributeLocation(), glCtx.color.getRed(), glCtx.color.getGreen(), glCtx.color.getBlue(), glCtx.color.getAlpha());
            glCtx.dirtyColorAttrib = false;
        }
        if (!hasNormal && glCtx.dirtyNormalAttrib && !ffpWillHandle) {
            final var n = sm.currentNormal;
            RENDER_BACKEND.vertexAttrib3f(Usage.NORMAL.getAttributeLocation(), n.x, n.y, n.z);
            glCtx.dirtyNormalAttrib = false;
        }
        if (!hasTexCoord && glCtx.dirtyTexCoordAttrib && !ffpWillHandle) {
            final var tc = sm.currentTexCoords[0];
            RENDER_BACKEND.vertexAttrib4f(Usage.PRIMARY_UV.getAttributeLocation(), tc.x, tc.y, tc.z, tc.w);
            glCtx.dirtyTexCoordAttrib = false;
        }
        if (!hasLightmap && glCtx.dirtyLightmapAttrib && !ffpWillHandle) {
            RENDER_BACKEND.vertexAttrib4f(Usage.SECONDARY_UV.getAttributeLocation(), glCtx.lastBrightnessX, glCtx.lastBrightnessY, 0.0f, 1.0f);
            glCtx.dirtyLightmapAttrib = false;
        }
    }

    public static void setEntityIds(int entity, int blockEntity, int item) {
        rejectExitCapture();
        if (!isMainThread()) return;
        final GLContextState glCtx = ctx();
        final long ids = InstancedAttribs.packEntityInfo(entity, blockEntity, item);
        if (ids == glCtx.entityIds) return;
        TessellatorEntityIds.onIdsChanged(glCtx.entityIds, ids);
        glCtx.entityIds = ids;
        glCtx.dirtyEntityIdsAttrib = true;
    }

    public static void flushEntityIds(GLContextState glCtx) {
        if (!glCtx.dirtyEntityIdsAttrib || glCtx.activeProgram == 0) return;
        final ImmediateExtendedAttribHandler handler = GLSMHooks.immediateExtendedHandler;
        if (handler == null || !handler.wantsEntityIds()) return;
        final long ids = glCtx.entityIds;
        RENDER_BACKEND.vertexAttrib4f(ImmediateExtendedAttribHandler.LOC_ENTITY_IDS,
            InstancedAttribs.normalizedId(ids, 0), InstancedAttribs.normalizedId(ids, 16), InstancedAttribs.normalizedId(ids, 32), 0.0f);
        glCtx.dirtyEntityIdsAttrib = false;
    }

    public static void glNormal3b(byte nx, byte ny, byte nz) {
        final float fnx = b2f(nx), fny = b2f(ny), fnz = b2f(nz);
        final GLContextState glCtx = ctx();
        glCtx.ffp.setNormal(fnx, fny, fnz);
        if (DisplayListManager.isRecording() || ImmediateModeRecorder.isDrawing()) {
            ImmediateModeRecorder.setNormal(fnx, fny, fnz);
            return;
        }
        glCtx.dirtyNormalAttrib = true;
    }

    public static void glNormal3d(double nx, double ny, double nz) {
        final float fnx = (float) nx, fny = (float) ny, fnz = (float) nz;
        final GLContextState glCtx = ctx();
        glCtx.ffp.setNormal(fnx, fny, fnz);
        if (DisplayListManager.isRecording() || ImmediateModeRecorder.isDrawing()) {
            ImmediateModeRecorder.setNormal(fnx, fny, fnz);
            return;
        }
        glCtx.dirtyNormalAttrib = true;
    }

    public static void glNormal3f(float nx, float ny, float nz) {
        final GLContextState glCtx = ctx();
        glCtx.ffp.setNormal(nx, ny, nz);
        if (DisplayListManager.isRecording() || ImmediateModeRecorder.isDrawing()) {
            ImmediateModeRecorder.setNormal(nx, ny, nz);
            return;
        }
        glCtx.dirtyNormalAttrib = true;
    }

    public static void glNormal3i(int nx, int ny, int nz) {
        final float fnx = nx / 2147483647.0f, fny = ny / 2147483647.0f, fnz = nz / 2147483647.0f;
        final GLContextState glCtx = ctx();
        glCtx.ffp.setNormal(fnx, fny, fnz);
        if (DisplayListManager.isRecording() || ImmediateModeRecorder.isDrawing()) {
            ImmediateModeRecorder.setNormal(fnx, fny, fnz);
            return;
        }
        glCtx.dirtyNormalAttrib = true;
    }

    public static void glEdgeFlag(boolean flag) {
        final RecordMode mode = DisplayListManager.getRecordMode();
        if (mode != RecordMode.NONE) {
            DisplayListManager.recordStateCommand(() -> glEdgeFlag(flag));
            if (mode == RecordMode.COMPILE) return;
        }
        ctx().edgeFlagState.setEnabled(flag);
    }

    public static void glColor4f(float red, float green, float blue, float alpha) {
        final RecordMode mode = DisplayListManager.getRecordMode();
        if (mode != RecordMode.NONE) {
            DisplayListManager.recordColor(red, green, blue, alpha);
            if (mode == RecordMode.COMPILE) {
                return;
            }
        }
        changeColor(red, green, blue, alpha);
    }

    public static void glColor4d(double red, double green, double blue, double alpha) {
        final RecordMode mode = DisplayListManager.getRecordMode();
        if (mode != RecordMode.NONE) {
            DisplayListManager.recordColor((float) red, (float) green, (float) blue, (float) alpha);
            if (mode == RecordMode.COMPILE) {
                return;
            }
        }
        changeColor((float) red, (float) green, (float) blue, (float) alpha);
    }

    public static void glColor4b(byte red, byte green, byte blue, byte alpha) {
        final RecordMode mode = DisplayListManager.getRecordMode();
        if (mode != RecordMode.NONE) {
            DisplayListManager.recordColor(b2f(red), b2f(green), b2f(blue), b2f(alpha));
            if (mode == RecordMode.COMPILE) {
                return;
            }
        }
        changeColor(b2f(red), b2f(green), b2f(blue), b2f(alpha));
    }

    public static void glColor4ub(byte red, byte green, byte blue, byte alpha) {
        final RecordMode mode = DisplayListManager.getRecordMode();
        if (mode != RecordMode.NONE) {
            DisplayListManager.recordColor(ub2f(red), ub2f(green), ub2f(blue), ub2f(alpha));
            if (mode == RecordMode.COMPILE) {
                return;
            }
        }
        changeColor(ub2f(red), ub2f(green), ub2f(blue), ub2f(alpha));
    }

    public static void glColor3f(float red, float green, float blue) {
        final RecordMode mode = DisplayListManager.getRecordMode();
        if (mode != RecordMode.NONE) {
            DisplayListManager.recordColor(red, green, blue, 1.0F);
            if (mode == RecordMode.COMPILE) {
                return;
            }
        }
        changeColor(red, green, blue, 1.0F);
    }

    public static void glColor3d(double red, double green, double blue) {
        final RecordMode mode = DisplayListManager.getRecordMode();
        if (mode != RecordMode.NONE) {
            DisplayListManager.recordColor((float) red, (float) green, (float) blue, 1.0F);
            if (mode == RecordMode.COMPILE) {
                return;
            }
        }
        changeColor((float) red, (float) green, (float) blue, 1.0F);
    }

    public static void glColor3b(byte red, byte green, byte blue) {
        final RecordMode mode = DisplayListManager.getRecordMode();
        if (mode != RecordMode.NONE) {
            DisplayListManager.recordColor(b2f(red), b2f(green), b2f(blue), 1.0F);
            if (mode == RecordMode.COMPILE) {
                return;
            }
        }
        changeColor(b2f(red), b2f(green), b2f(blue), 1.0F);
    }

    public static void glColor3ub(byte red, byte green, byte blue) {
        final RecordMode mode = DisplayListManager.getRecordMode();
        if (mode != RecordMode.NONE) {
            DisplayListManager.recordColor(ub2f(red), ub2f(green), ub2f(blue), 1.0F);
            if (mode == RecordMode.COMPILE) {
                return;
            }
        }
        changeColor(ub2f(red), ub2f(green), ub2f(blue), 1.0F);
    }

    private static float ub2f(byte b) {
        return (b & 0xFF) / 255.0F;
    }

    private static float b2f(byte b) {
        return ((b - Byte.MIN_VALUE) & 0xFF) / 255.0F;
    }

    private static boolean changeColor(float red, float green, float blue, float alpha) {
        final GLContextState glCtx = ctx();
        glCtx.exitCapture.write(ExitStateCapture.COLOR);
        // Helper function for glColor*
        final boolean caching = isCachingEnabled();
        if (!caching || red != glCtx.color.getRed() || green != glCtx.color.getGreen() || blue != glCtx.color.getBlue() || alpha != glCtx.color.getAlpha()) {
            mod(glCtx.color);
            glCtx.color.setRed(red);
            glCtx.color.setGreen(green);
            glCtx.color.setBlue(blue);
            glCtx.color.setAlpha(alpha);
            glCtx.colorGeneration++;
            if (ImmediateModeRecorder.isDrawing()) {
                ImmediateModeRecorder.setColor(red, green, blue, alpha);
            }
            glCtx.dirtyColorAttrib = true;
            // Only set while an enchantment-glint span is active
            final GlintColorHandler glintHandler = GLSMHooks.glintColorHandler;
            if (glintHandler != null) {
                glintHandler.onColorChanged(red, green, blue, alpha);
            }
            return true;
        }
        return false;
    }

    public static void glSecondaryColor3f(float red, float green, float blue) {
        rejectExitCapture();
        final RecordMode mode = DisplayListManager.getRecordMode();
        if (mode != RecordMode.NONE) {
            DisplayListManager.recordSecondaryColor(red, green, blue);
            if (mode == RecordMode.COMPILE) {
                return;
            }
        }
        changeSecondaryColor(red, green, blue);
    }

    public static void glSecondaryColor3d(double red, double green, double blue) {
        glSecondaryColor3f((float) red, (float) green, (float) blue);
    }

    public static void glSecondaryColor3b(byte red, byte green, byte blue) {
        glSecondaryColor3f(b2f(red), b2f(green), b2f(blue));
    }

    public static void glSecondaryColor3ub(byte red, byte green, byte blue) {
        glSecondaryColor3f(ub2f(red), ub2f(green), ub2f(blue));
    }

    /** Helper for glSecondaryColor* - the color sum is emulated by the FFP shaders, nothing is sent to the driver. */
    private static void changeSecondaryColor(float red, float green, float blue) {
        final GLContextState glCtx = ctx();
        if (!isCachingEnabled() || red != glCtx.secondaryColor.getRed() || green != glCtx.secondaryColor.getGreen() || blue != glCtx.secondaryColor.getBlue()) {
            if (glCtx.colorSumState.isEnabled()) beforeUncapturedStateChange();
            mod(glCtx.secondaryColor);
            glCtx.secondaryColor.setRed(red);
            glCtx.secondaryColor.setGreen(green);
            glCtx.secondaryColor.setBlue(blue);
            glCtx.fragmentGeneration++;
        }
    }

    public static void glTexCoord1f(float s) {
        if (DisplayListManager.isRecording() || ImmediateModeRecorder.isDrawing()) {
            ImmediateModeRecorder.setTexCoord(s, 0.0f);
            return;
        }
        final GLContextState glCtx = ctx();
        glCtx.ffp.setTexCoord(s, 0.0f, 0.0f, 1.0f);
        glCtx.dirtyTexCoordAttrib = true;
    }

    public static void glTexCoord1d(double s) {
        if (DisplayListManager.isRecording() || ImmediateModeRecorder.isDrawing()) {
            ImmediateModeRecorder.setTexCoord((float) s, 0.0f);
            return;
        }
        final GLContextState glCtx = ctx();
        glCtx.ffp.setTexCoord((float) s, 0.0f, 0.0f, 1.0f);
        glCtx.dirtyTexCoordAttrib = true;
    }

    public static void glTexCoord2f(float s, float t) {
        if (DisplayListManager.isRecording() || ImmediateModeRecorder.isDrawing()) {
            ImmediateModeRecorder.setTexCoord(s, t);
            return;
        }
        final GLContextState glCtx = ctx();
        glCtx.ffp.setTexCoord(s, t, 0.0f, 1.0f);
        glCtx.dirtyTexCoordAttrib = true;
    }

    public static void glTexCoord2d(double s, double t) {
        if (DisplayListManager.isRecording() || ImmediateModeRecorder.isDrawing()) {
            ImmediateModeRecorder.setTexCoord((float) s, (float) t);
            return;
        }
        final GLContextState glCtx = ctx();
        glCtx.ffp.setTexCoord((float) s, (float) t, 0.0f, 1.0f);
        glCtx.dirtyTexCoordAttrib = true;
    }

    public static void glTexCoord3f(float s, float t, float r) {
        if (DisplayListManager.isRecording() || ImmediateModeRecorder.isDrawing()) {
            ImmediateModeRecorder.setTexCoord(s, t);  // Only track s,t for 2D textures
            return;
        }
        final GLContextState glCtx = ctx();
        glCtx.ffp.setTexCoord(s, t, r, 1.0f);
        glCtx.dirtyTexCoordAttrib = true;
    }

    public static void glTexCoord3d(double s, double t, double r) {
        if (DisplayListManager.isRecording() || ImmediateModeRecorder.isDrawing()) {
            ImmediateModeRecorder.setTexCoord((float) s, (float) t);
            return;
        }
        final GLContextState glCtx = ctx();
        glCtx.ffp.setTexCoord((float) s, (float) t, (float) r, 1.0f);
        glCtx.dirtyTexCoordAttrib = true;
    }

    public static void glTexCoord4f(float s, float t, float r, float q) {
        if (DisplayListManager.isRecording() || ImmediateModeRecorder.isDrawing()) {
            ImmediateModeRecorder.setTexCoord(s, t);
            return;
        }
        final GLContextState glCtx = ctx();
        glCtx.ffp.setTexCoord(s, t, r, q);
        glCtx.dirtyTexCoordAttrib = true;
    }

    public static void glTexCoord4d(double s, double t, double r, double q) {
        if (DisplayListManager.isRecording() || ImmediateModeRecorder.isDrawing()) {
            ImmediateModeRecorder.setTexCoord((float) s, (float) t);
            return;
        }
        final GLContextState glCtx = ctx();
        glCtx.ffp.setTexCoord((float) s, (float) t, (float) r, (float) q);
        glCtx.dirtyTexCoordAttrib = true;
    }

    public static void glVertexAttrib2f(int index, float v0, float v1) {
        glVertexAttrib4f(index, v0, v1, 0.0f, 1.0f);
    }

    public static void glVertexAttrib2s(int index, short v0, short v1) {
        glVertexAttrib4f(index, v0, v1, 0.0f, 1.0f);
    }

    public static void glVertexAttrib3f(int index, float v0, float v1, float v2) {
        glVertexAttrib4f(index, v0, v1, v2, 1.0f);
    }

    public static void glVertexAttrib4f(int index, float v0, float v1, float v2, float v3) {
        final RecordMode mode = DisplayListManager.getRecordMode();
        if (mode != RecordMode.NONE) {
            DisplayListManager.recordVertexAttrib(index, v0, v1, v2, v3);
            if (mode == RecordMode.COMPILE) return;
        }
        beforeUncapturedStateChange();
        RENDER_BACKEND.vertexAttrib4f(index, v0, v1, v2, v3);
    }

    public static void glMultiTexCoord2f(int target, float s, float t) {
        final GLContextState glCtx = ctx();
        if (target == GL13.GL_TEXTURE0) {
            if (DisplayListManager.isRecording() || ImmediateModeRecorder.isDrawing()) {
                ImmediateModeRecorder.setTexCoord(s, t);
                return;
            }
            glCtx.ffp.setTexCoord(s, t, 0.0f, 1.0f);
            glCtx.dirtyTexCoordAttrib = true;
        } else if (target == GL13.GL_TEXTURE1) {
            if (ImmediateModeRecorder.isDrawing()) {
                ImmediateModeRecorder.setLightmapCoord(s, t);
                return;
            }
            setLightmapTextureCoords(target, s, t);
        } else {
            final int unit = target - GL13.GL_TEXTURE0;
            if (unit >= 2 && unit < 4) {
                if (DisplayListManager.isRecording() || ImmediateModeRecorder.isDrawing()) {
                    glCtx.unit23TexCoordSetDuringDraw = true;
                }
                beforeUncapturedStateChange();
                glCtx.ffp.setTexCoord(unit, s, t, 0.0f, 1.0f);
            } else {
                glCtx.exitCapture.reject();
            }
        }
    }

    public static void glMultiTexCoord2d(int target, double s, double t) {
        glMultiTexCoord2f(target, (float) s, (float) t);
    }

    public static void glMultiTexCoord2s(int target, short s, short t) {
        glMultiTexCoord2f(target, s, t);
    }

    public static void setLightmapTextureCoords(int unit, float x, float y) {
        if (unit != GL13.GL_TEXTURE1) {
            rejectExitCapture();
            return;
        }
        final GLContextState glCtx = ctx();
        glCtx.exitCapture.write(ExitStateCapture.LIGHTMAP);
        final RecordMode mode = DisplayListManager.getRecordMode();
        if (mode != RecordMode.NONE) {
            DisplayListManager.recordStateCommand(() -> setLightmapTextureCoords(unit, x, y));
            if (mode == RecordMode.COMPILE) return;
        }
        glCtx.lastBrightnessX = x;
        glCtx.lastBrightnessY = y;
        glCtx.dirtyLightmapAttrib = true;
        if (GLSMHooks.LIGHTMAP_COORDS.hasListeners()) {
            GLSMHooks.lightmapCoordsEvent.x = x;
            GLSMHooks.lightmapCoordsEvent.y = y;
            GLSMHooks.LIGHTMAP_COORDS.post(GLSMHooks.lightmapCoordsEvent);
        }
    }

    public static float getOverlayR() {
        return ctx().overlayR;
    }

    public static float getOverlayG() {
        return ctx().overlayG;
    }

    public static float getOverlayB() {
        return ctx().overlayB;
    }

    public static float getOverlayA() {
        return ctx().overlayA;
    }

    public static float getShaderColorR() {
        return ctx().shaderColorR;
    }

    public static float getShaderColorG() {
        return ctx().shaderColorG;
    }

    public static float getShaderColorB() {
        return ctx().shaderColorB;
    }

    public static float getShaderColorA() {
        return ctx().shaderColorA;
    }

    public static Color4Stack getColor() {
        return ctx().color;
    }

    public static Color4Stack getSecondaryColor() {
        return ctx().secondaryColor;
    }

    public static BooleanStateStack getColorSumState() {
        return ctx().colorSumState;
    }
}
