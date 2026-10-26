package com.gtnewhorizons.angelica.glsm;

import com.gtnewhorizons.angelica.glsm.DisplayListManager.RecordMode;
import com.gtnewhorizons.angelica.glsm.profiling.Tracy;
import com.gtnewhorizons.angelica.glsm.hooks.GLSMHooks;
import com.gtnewhorizons.angelica.glsm.recording.CommandRecorder;
import com.gtnewhorizons.angelica.glsm.recording.commands.TexImage2DCmd;
import com.gtnewhorizons.angelica.glsm.recording.commands.TexSubImage2DCmd;
import com.gtnewhorizons.angelica.glsm.stacks.IntegerStateStack;
import com.gtnewhorizons.angelica.glsm.stacks.TextureBindingStack;
import com.gtnewhorizons.angelica.glsm.states.PixelStoreState;
import com.gtnewhorizons.angelica.glsm.states.SamplerUnitArray;
import com.gtnewhorizons.angelica.glsm.states.ImageUnitBinding;
import com.gtnewhorizons.angelica.glsm.states.TextureUnitArray;
import com.gtnewhorizons.angelica.glsm.texture.TextureInfo;
import com.gtnewhorizons.angelica.glsm.texture.TextureInfoCache;
import com.gtnewhorizons.angelica.glsm.texture.TextureStaging;
import org.lwjgl.opengl.EXTTextureFilterAnisotropic;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL14;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL30;
import org.lwjgl.opengl.GL31;
import org.lwjgl.opengl.GL32;
import org.lwjgl.opengl.GL33;
import org.lwjgl.opengl.GL40;
import java.nio.Buffer;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.DoubleBuffer;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;

import static com.gtnewhorizons.angelica.glsm.GLStateManager.LOGGER;
import static com.gtnewhorizons.angelica.glsm.GLStateManager.MAX_TEXTURE_COORDS;
import static com.gtnewhorizons.angelica.glsm.GLStateManager.MAX_TEXTURE_UNITS;
import static com.gtnewhorizons.angelica.glsm.GLStateManager.acquireDrawLock;
import static com.gtnewhorizons.angelica.glsm.GLStateManager.beforeUncapturedStateChange;
import static com.gtnewhorizons.angelica.glsm.GLStateManager.ctx;
import static com.gtnewhorizons.angelica.glsm.GLStateManager.isCachingEnabled;
import static com.gtnewhorizons.angelica.glsm.GLStateManager.isRecordingDisplayList;
import static com.gtnewhorizons.angelica.glsm.GLStateManager.mod;
import static com.gtnewhorizons.angelica.glsm.GLStateManager.recordExitTextureBind;
import static com.gtnewhorizons.angelica.glsm.GLStateManager.rejectExitCapture;
import static com.gtnewhorizons.angelica.glsm.GLStateManager.releaseDrawLock;
import static com.gtnewhorizons.angelica.glsm.GLStateManager.requireContext;
import static com.gtnewhorizons.angelica.glsm.GLStateManager.warnOnce;
import static com.gtnewhorizons.angelica.glsm.GLStateManager.warnOnceTrace;
import static com.gtnewhorizons.angelica.glsm.backend.BackendManager.RENDER_BACKEND;

public final class GLSMTextures {

    private GLSMTextures() {}

    public static long texBindMisses;


    public static int getMaxBoundTextureUnit() {
        return ctx().maxBoundTextureUnit;
    }

    public static void trackMaxBoundTextureUnit(int unit) {
        final GLContextState glCtx = ctx();
        if (unit > glCtx.maxBoundTextureUnit) glCtx.maxBoundTextureUnit = unit;
    }
    private static final Tracy.ZoneId Z_TEX_DELETE = Tracy.zoneId("texDelete", Tracy.COLOR_FFP);

    /**
     * Get the active texture unit for server-side state operations. If caching is enabled, returns cached value. If caching is disabled (SharedDrawable),
     * queries actual GL state.
     */
    public static int getActiveTextureUnitForServerState() {
        if (isCachingEnabled()) {
            return getActiveTextureUnit();
        }
        // Query actual GL state for SharedDrawable context
        return RENDER_BACKEND.getInteger(GL13.GL_ACTIVE_TEXTURE) - GL13.GL_TEXTURE0;
    }

    /**
     * Get the texture bound to the current texture unit for server-side state operations.
     * If caching is enabled, returns cached value.
     * If caching is disabled (SharedDrawable), queries actual GL state.
     */
    public static int getBoundTextureForServerState() {
        if (isCachingEnabled()) {
            return getBoundTexture();
        }
        // Query actual GL state for SharedDrawable context
        return RENDER_BACKEND.getInteger(GL11.GL_TEXTURE_BINDING_2D);
    }

    private static TextureInfo getBoundTextureInfo() {
        final GLContextState glCtx = ctx();
        if (isCachingEnabled()) {
            return glCtx.textures.getTextureUnitBindings(glCtx.activeTextureUnit.getValue()).getOrResolveInfo();
        }
        return TextureInfoCache.INSTANCE.getInfo(getBoundTextureForServerState());
    }

    /**
     * Get the texture bound to a specific texture unit for server-side state operations.
     * If caching is enabled, returns cached value.
     * If caching is disabled (SharedDrawable), returns -1 to force operations to proceed
     */
    public static int getBoundTextureForServerState(int unit) {
        if (isCachingEnabled()) {
            return getBoundTexture(unit);
        }
        return -1;
    }

    public static int setTextureSupersamplingSamples(int samples) {
        if (samples < 1 || samples > 16 || (samples & (samples - 1)) != 0) {
            throw new IllegalArgumentException("Texture supersampling requires 1, 2, 4, 8, or 16 samples");
        }
        final GLContextState glCtx = ctx();
        final int previous = glCtx.textureSupersamplingSamples;
        if (previous != samples) {
            beforeUncapturedStateChange();
            glCtx.textureSupersamplingSamples = samples;
        }
        return previous;
    }

    public static void markTextureAsAtlas(int texture, int gutter) {
        if (texture <= 0) return;
        final TextureInfo info = TextureInfoCache.INSTANCE.getInfo(texture);
        if (info.getAtlasGutter() != gutter) {
            beforeUncapturedStateChange();
            info.setAtlasGutter(gutter);
        }
    }

    public static void glActiveTexture(int texture) {
        final GLContextState glCtx = ctx();
        glCtx.exitCapture.write(ExitStateCapture.ACTIVE_UNIT);
        final RecordMode mode = DisplayListManager.getRecordMode();
        if (mode != RecordMode.NONE) {
            DisplayListManager.recordActiveTexture(texture);
            if (mode == RecordMode.COMPILE) {
                return;
            }
        }
        final int newTexture = texture - GL13.GL_TEXTURE0;
        if (!isValidTextureUnit(newTexture, texture, "glActiveTexture")) {
            return;
        }
        final boolean caching = isCachingEnabled();
        if (!caching || glCtx.activeTextureUnit.getValue() != newTexture) {
            mod(glCtx.activeTextureUnit).setValue(newTexture);
            RENDER_BACKEND.activeTexture(texture);
        }
    }

    private static boolean isValidTextureUnit(int unit, int rawEnum, String function) {
        return isValidTextureUnit(unit, MAX_TEXTURE_UNITS, rawEnum, function);
    }

    private static boolean isValidTextureUnit(int unit, int limit, int rawEnum, String function) {
        if (unit >= 0 && unit < limit) {
            return true;
        }
        warnOnceTrace(function, "{}({}) is not a valid texture unit enum (expected GL_TEXTURE0..GL_TEXTURE{}); ignoring", function, rawEnum, limit - 1);
        return false;
    }

    public static void glActiveTextureARB(int texture) {
        glActiveTexture(texture);
    }

    public static void glClientActiveTexture(int texture) {
        final int unit = texture - GL13.GL_TEXTURE0;
        if (!isValidTextureUnit(unit, MAX_TEXTURE_COORDS, texture, "glClientActiveTexture")) {
            return;
        }
        final GLContextState glCtx = ctx();
        glCtx.exitCapture.write(ExitStateCapture.CLIENT_ACTIVE_UNIT);
        glCtx.clientActiveTextureUnit = unit;
    }

    public static int getClientActiveTextureUnit() {
        return ctx().clientActiveTextureUnit;
    }

    private static int getBoundTexture() {
        return getBoundTexture(ctx().activeTextureUnit.getValue());
    }

    private static int getBoundTexture(int unit) {
        return ctx().textures.getTextureUnitBindings(unit).getBinding();
    }

    public static void glBindTexture(int target, int texture) {
        if (TextureInfoCache.isProxyTarget(target)) return;
        final GLContextState glCtx = ctx();
        recordExitTextureBind(glCtx, target, glCtx.activeTextureUnit.getValue());
        final RecordMode mode = DisplayListManager.getRecordMode();
        if (mode != RecordMode.NONE) {
            DisplayListManager.recordBindTexture(target, texture);
            if (mode == RecordMode.COMPILE) {
                return;
            }
        }
        final long claim = GLSMTextureLifetime.claimForBind(texture, target);
        if (claim == GLSMTextureLifetime.BIND_REJECTED) return;

        if (!isCachingEnabled()) {
            RENDER_BACKEND.bindTexture(target, texture);
            GLSMTextureLifetime.finishRescue(target, texture, claim);
            return;
        }

        final int activeUnit = glCtx.activeTextureUnit.getValue();
        final TextureBindingStack textureUnit = glCtx.textures.getTextureUnitBindings(activeUnit);
        final int cachedBinding = textureUnit.getBinding();

        if (cachedBinding != texture || (texture == 0 && textureUnit.getTarget() != target)) {
            if (activeUnit != 0 || target != GL11.GL_TEXTURE_2D) beforeUncapturedStateChange();
            if (Tracy.ENABLED) texBindMisses++;
            RENDER_BACKEND.bindTexture(target, texture);
            mod(textureUnit).setBinding(texture, target);
            if (texture != 0) {
                if (activeUnit > glCtx.maxBoundTextureUnit) glCtx.maxBoundTextureUnit = activeUnit;
            }
            if (!glCtx.lockBindCallback && target == GL11.GL_TEXTURE_2D && glCtx.activeTextureUnit.getValue() == 0) {
                glCtx.lockBindCallback = true;
                if (GLSMHooks.TEXTURE_BIND.hasListeners()) {
                    GLSMHooks.textureBindEvent.textureId = texture;
                    GLSMHooks.TEXTURE_BIND.post(GLSMHooks.textureBindEvent);
                }
                RenderSystem.bindTextureToUnit(0, texture);
                glCtx.lockBindCallback = false;
            }
        }
        GLSMTextureLifetime.finishRescue(target, texture, claim);
    }

    private static void bindTextureUnitByName(int unit, int texture) {
        if (GLSMTextureLifetime.isDeferredDelete(texture)) return;
        final CommandRecorder paused = DisplayListManager.pauseRecording();
        final int saved = getActiveTextureUnit();
        try {
            glActiveTexture(GL13.GL_TEXTURE0 + unit);
            if (texture == 0) {
                // Read before the 2D unbind, which overwrites the unit's cached target.
                final int target = ctx().textures.getTextureUnitBindings(unit).getTarget();
                glBindTexture(GL11.GL_TEXTURE_2D, 0);
                if (!GLSMTextureLifetime.is2D(target)) glBindTexture(target, 0);
            } else {
                glBindTexture(GLSMTextureLifetime.targetOf(texture), texture);
            }
        } finally {
            glActiveTexture(GL13.GL_TEXTURE0 + saved);
            if (paused != null) DisplayListManager.resumeRecording(paused);
        }
    }

    public static void glBindTextures(int first, int count, IntBuffer textures) {
        if (first < 0 || !isValidTextureUnit(first + count - 1, GL13.GL_TEXTURE0 + first + count - 1, "glBindTextures")) return;
        if (textures != null) requireRemaining(textures, count);
        for (int i = 0; i < count; i++) bindTextureUnitByName(first + i, textures == null ? 0 : textures.get(textures.position() + i));
    }

    public static void glBindTextures(int first, IntBuffer textures) {
        if (textures != null) glBindTextures(first, textures.remaining(), textures);
    }

    public static void glBindTextures(int first, int[] textures) {
        if (textures != null) glBindTextures(first, textures.length, IntBuffer.wrap(textures));
    }

    public static void glBindTextureUnit(int unit, int texture) {
        if (isValidTextureUnit(unit, GL13.GL_TEXTURE0 + unit, "glBindTextureUnit")) bindTextureUnitByName(unit, texture);
    }

    public static void glBindMultiTextureEXT(int texunit, int target, int texture) {
        if (DisplayListManager.isRecording()) throw DisplayListManager.unsupportedInList("glBindMultiTextureEXT");
        if (!isValidTextureUnit(texunit - GL13.GL_TEXTURE0, texunit, "glBindMultiTextureEXT")) return;
        final int saved = getActiveTextureUnit();
        glActiveTexture(texunit);
        glBindTexture(target, texture);
        glActiveTexture(GL13.GL_TEXTURE0 + saved);
    }

    private static int changeFormatIfDeprecated(int internalformat) {
        internalformat = GLESFormatRemap.promoteAlphaFormat(internalformat);
        if (RenderSystem.isGLES()) {
            internalformat = GLESFormatRemap.remapInternalFormat(internalformat);
        }
        return internalformat;
    }

    public static final class GLESTexImageRemap {
        private final GLESFormatRemap.Result r;
        private GLESTexImageRemap(GLESFormatRemap.Result r) { this.r = r; }
        public int internalFormat() { return r.internalFormat(); }
        public int format() { return r.format(); }
        public int type() { return r.type(); }
    }

    public static GLESTexImageRemap remapTexImageForGLES(int internalformat, int format, int type) {
        return new GLESTexImageRemap(GLESFormatRemap.apply(internalformat, format, type, RenderSystem.isGLES()));
    }

    static {
        if (ByteOrder.nativeOrder() != ByteOrder.LITTLE_ENDIAN) {
            LOGGER.warn("GLSM GLES pixel-type remap assumes little-endian host; big-endian detected - BGRA uploads may be byte-swapped");
        }
    }

    static int remapPixelTypeForGLES(int format, int type) {
        return RenderSystem.isGLES() ? GLESFormatRemap.remapPixelType(format, type) : type;
    }

    private static int remapTypeForRemappedInternalFormat(int internalformat, int type) {
        if (!RenderSystem.isGLES()) return type;
        if (!GLESFormatRemap.isGenericPixelType(type)) return type;
        return GLESFormatRemap.typeForInternalFormatES32(internalformat, type);
    }

    public static void glTexImage2D(int target, int level, int internalformat, int width, int height, int border, int format, int type, IntBuffer pixels) {
        rejectExitCapture();
        internalformat = changeFormatIfDeprecated(internalformat);
        type = remapPixelTypeForGLES(format, type);
        type = remapTypeForRemappedInternalFormat(internalformat, type);
        final RecordMode mode = DisplayListManager.getRecordMode();
        if (mode != RecordMode.NONE) {
            DisplayListManager.recordComplexCommand(TexImage2DCmd.fromIntBuffer(target, level, internalformat, width, height, border, format, type, pixels, ctx().pixelUnpackState));
            if (mode == RecordMode.COMPILE) {
                return;
            }
        }
        TextureInfoCache.INSTANCE.onTexImage2D(target, level, internalformat, width, height, border, format, type, pixels);
        GLSMBuffers.suspendPixelUnpackBuffer();
        RENDER_BACKEND.texImage2D(target, level, internalformat, width, height, border, format, type, pixels);
        GLSMBuffers.restorePixelUnpackBuffer();
        if (mode == RecordMode.NONE && RenderSystem.isLTW()) LTWWorkaround.onTexImage2D(target, level, format, pixels != null);
        maybeGenerateMipmap(target, level);
    }

    public static void glTexImage2D(int target, int level, int internalformat, int width, int height, int border, int format, int type, FloatBuffer pixels) {
        rejectExitCapture();
        internalformat = changeFormatIfDeprecated(internalformat);
        type = remapPixelTypeForGLES(format, type);
        type = remapTypeForRemappedInternalFormat(internalformat, type);
        final RecordMode mode = DisplayListManager.getRecordMode();
        if (mode != RecordMode.NONE) {
            DisplayListManager.recordComplexCommand(TexImage2DCmd.fromFloatBuffer(target, level, internalformat, width, height, border, format, type, pixels, ctx().pixelUnpackState));
            if (mode == RecordMode.COMPILE) {
                return;
            }
        }
        TextureInfoCache.INSTANCE.onTexImage2D(target, level, internalformat, width, height, border, format, type, pixels);
        GLSMBuffers.suspendPixelUnpackBuffer();
        RENDER_BACKEND.texImage2D(target, level, internalformat, width, height, border, format, type, pixels);
        GLSMBuffers.restorePixelUnpackBuffer();
        if (mode == RecordMode.NONE && RenderSystem.isLTW()) LTWWorkaround.onTexImage2D(target, level, format, pixels != null);
        maybeGenerateMipmap(target, level);
    }

    public static void glTexImage2D(int target, int level, int internalformat, int width, int height, int border, int format, int type, DoubleBuffer pixels) {
        rejectExitCapture();
        internalformat = changeFormatIfDeprecated(internalformat);
        type = remapPixelTypeForGLES(format, type);
        type = remapTypeForRemappedInternalFormat(internalformat, type);
        final RecordMode mode = DisplayListManager.getRecordMode();
        if (mode != RecordMode.NONE) {
            DisplayListManager.recordComplexCommand(TexImage2DCmd.fromDoubleBuffer(target, level, internalformat, width, height, border, format, type, pixels, ctx().pixelUnpackState));
            if (mode == RecordMode.COMPILE) {
                return;
            }
        }
        TextureInfoCache.INSTANCE.onTexImage2D(target, level, internalformat, width, height, border, format, type, pixels);
        GLSMBuffers.suspendPixelUnpackBuffer();
        RENDER_BACKEND.texImage2D(target, level, internalformat, width, height, border, format, type, pixels);
        GLSMBuffers.restorePixelUnpackBuffer();
        if (mode == RecordMode.NONE && RenderSystem.isLTW()) LTWWorkaround.onTexImage2D(target, level, format, pixels != null);
        maybeGenerateMipmap(target, level);
    }

    public static void glTexImage2D(int target, int level, int internalformat, int width, int height, int border, int format, int type, ByteBuffer pixels) {
        rejectExitCapture();
        internalformat = changeFormatIfDeprecated(internalformat);
        type = remapPixelTypeForGLES(format, type);
        type = remapTypeForRemappedInternalFormat(internalformat, type);
        final RecordMode mode = DisplayListManager.getRecordMode();
        if (mode != RecordMode.NONE) {
            DisplayListManager.recordComplexCommand(TexImage2DCmd.fromByteBuffer(target, level, internalformat, width, height, border, format, type, pixels, ctx().pixelUnpackState));
            if (mode == RecordMode.COMPILE) {
                return;
            }
        }
        TextureInfoCache.INSTANCE.onTexImage2D(target, level, internalformat, width, height, border, format, type, pixels != null ? pixels.asIntBuffer() : null);
        GLSMBuffers.suspendPixelUnpackBuffer();
        RENDER_BACKEND.texImage2D(target, level, internalformat, width, height, border, format, type, pixels);
        GLSMBuffers.restorePixelUnpackBuffer();
        if (mode == RecordMode.NONE && RenderSystem.isLTW()) LTWWorkaround.onTexImage2D(target, level, format, pixels != null);
        maybeGenerateMipmap(target, level);
    }

    public static void glTexImage2D(int target, int level, int internalformat, int width, int height, int border, int format, int type, long pixels_buffer_offset) {
        rejectExitCapture();
        internalformat = changeFormatIfDeprecated(internalformat);
        type = remapPixelTypeForGLES(format, type);
        type = remapTypeForRemappedInternalFormat(internalformat, type);
        if (DisplayListManager.isRecording()) {
            throw DisplayListManager.unsupportedInList("glTexImage2D with buffer offset");
        }
        TextureInfoCache.INSTANCE.onTexImage2D(target, level, internalformat, width, height, border, format, type, pixels_buffer_offset);
        RENDER_BACKEND.texImage2D(target, level, internalformat, width, height, border, format, type, pixels_buffer_offset);
        maybeGenerateMipmap(target, level);
    }

    public static void glDeleteTextures(int id) {
        rejectExitCapture();
        deleteTexture(id);
    }

    public static void glDeleteTextures(IntBuffer ids) {
        rejectExitCapture();
        for (int i = 0; i < ids.remaining(); i++) {
            deleteTexture(ids.get(ids.position() + i));
        }
    }

    public static void glDeleteTextures(int[] ids) {
        rejectExitCapture();
        for (int id : ids) deleteTexture(id);
    }

    private static void deleteTexture(int id) {
        if (Tracy.ENABLED) Tracy.beginZone(Z_TEX_DELETE);
        try {
            GLSMTextureLifetime.deferDeleteTexture(id);
            onDeleteTexture(id);
        } finally {
            if (Tracy.ENABLED) Tracy.endZone();
        }
    }

    public static int glGenTextures() {
        requireContext("glGenTextures");
        GLSMTextureLifetime.flushDeferredTextureDeletes();
        return RENDER_BACKEND.genTextures();
    }

    public static void glGenTextures(IntBuffer textures) {
        requireContext("glGenTextures");
        GLSMTextureLifetime.flushDeferredTextureDeletes();
        RENDER_BACKEND.genTextures(textures);
    }

    public static void glGenTextures(int[] textures) {
        requireContext("glGenTextures");
        GLSMTextureLifetime.flushDeferredTextureDeletes();
        for (int i = 0; i < textures.length; i++) textures[i] = RENDER_BACKEND.genTextures();
    }

    public static int glCreateTextures(int target) {
        requireContext("glCreateTextures");
        final int id = RENDER_BACKEND.createTextures(target);
        final boolean locked = acquireDrawLock();
        try {
            GLSMTextureLifetime.recordTarget(id, target);
        } finally {
            if (locked) releaseDrawLock();
        }
        return id;
    }

    public static void glCreateTextures(int target, IntBuffer textures) {
        for (int i = textures.position(); i < textures.limit(); i++) textures.put(i, glCreateTextures(target));
    }

    public static void glCreateTextures(int target, int[] textures) {
        for (int i = 0; i < textures.length; i++) textures[i] = glCreateTextures(target);
    }

    public static void enableTexture() {
        final RecordMode mode = DisplayListManager.getRecordMode();
        if (mode != RecordMode.NONE) {
            DisplayListManager.recordEnable(GL11.GL_TEXTURE_2D);
            if (mode == RecordMode.COMPILE) {
                return;
            }
        }
        enableTexture(ctx());
    }

    static void enableTexture(GLContextState glCtx) {
        glCtx.textures.getTextureUnitStates(glCtx.activeTextureUnit.getValue()).enable();
    }

    public static void disableTexture() {
        final RecordMode mode = DisplayListManager.getRecordMode();
        if (mode != RecordMode.NONE) {
            DisplayListManager.recordDisable(GL11.GL_TEXTURE_2D);
            if (mode == RecordMode.COMPILE) {
                return;
            }
        }
        disableTexture(ctx());
    }

    static void disableTexture(GLContextState glCtx) {
        glCtx.textures.getTextureUnitStates(glCtx.activeTextureUnit.getValue()).disable();
    }

    public static void setFilter(boolean bilinear, boolean mipmap) {
        final int i, j;
        if (bilinear) {
            i = mipmap ? GL11.GL_LINEAR_MIPMAP_LINEAR : GL11.GL_LINEAR;
            j = GL11.GL_LINEAR;
        } else {
            i = mipmap ? GL11.GL_LINEAR_MIPMAP_LINEAR : GL11.GL_NEAREST;
            j = GL11.GL_NEAREST;
        }
        glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, i);
        glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, j);
    }

    private static void onDeleteTexture(int id) {
        final GLContextState glCtx = ctx();
        TextureInfoCache.INSTANCE.onDeleteTexture(id);
        if (GLSMHooks.TEXTURE_DELETE.hasListeners()) {
            GLSMHooks.textureDeleteEvent.textureId = id;
            GLSMHooks.TEXTURE_DELETE.post(GLSMHooks.textureDeleteEvent);
        }

        // Always delete
        clearTextureBindings(glCtx, id);
    }

    static void clearTextureBindings(GLContextState glCtx, int id) {
        for (int i = 0; i <= glCtx.maxBoundTextureUnit; i++) {
            if (glCtx.textures.getTextureUnitBindings(i).getBinding() == id) {
                mod(glCtx.textures.getTextureUnitBindings(i)).setBinding(0);
            }
        }

        for (int i = 0; i <= glCtx.maxBoundImageUnit; i++) {
            final ImageUnitBinding image = glCtx.imageUnits.get(i);
            if (image != null && image.getTexture() == id) {
                image.reset();
            }
        }
    }

    public static int getActiveTextureUnit() {
        return ctx().activeTextureUnit.getValue();
    }

    private static boolean handleRemovedTexParam(int target, int pname, int value) {
        if (pname == GL14.GL_GENERATE_MIPMAP) {
            if (target == GL11.GL_TEXTURE_2D) {
                final TextureInfo info = getBoundTextureInfo();
                if (info != null) info.setGenerateMipmap(value != 0);
            }
            return true;
        }
        return false;
    }

    private static void maybeGenerateMipmap(int target, int level) {
        if (target != GL11.GL_TEXTURE_2D) return;
        final TextureInfo info = getBoundTextureInfo();
        if (info != null && info.isGenerateMipmap() && level == info.getBaseLevel() && level < info.getMaxLevel()) {
            RENDER_BACKEND.generateMipmap(target);
        }
    }

    public static boolean updateTexParameteriCache(int target, int texture, int pname, int param) {
        rejectExitCapture();
        if (target != GL11.GL_TEXTURE_2D) {
            return true;
        }
        return updateTexParameteriCache(TextureInfoCache.INSTANCE.getInfo(texture), pname, param);
    }

    static boolean updateTexParameteriCache(TextureInfo info, int pname, int param) {
        if (info == null) {
            return true;
        }
        switch (pname) {
            case GL11.GL_TEXTURE_MIN_FILTER -> {
                if (info.getMinFilter() == param && isCachingEnabled()) return false;
                info.setMinFilter(param);
            }
            case GL11.GL_TEXTURE_MAG_FILTER -> {
                if (info.getMagFilter() == param && isCachingEnabled()) return false;
                info.setMagFilter(param);
            }
            case GL11.GL_TEXTURE_WRAP_S -> {
                if (info.getWrapS() == param && isCachingEnabled()) return false;
                info.setWrapS(param);
            }
            case GL11.GL_TEXTURE_WRAP_T -> {
                if (info.getWrapT() == param && isCachingEnabled()) return false;
                info.setWrapT(param);
            }
            case GL12.GL_TEXTURE_BASE_LEVEL -> {
                if (info.getBaseLevel() == param && isCachingEnabled()) return false;
                info.setBaseLevel(param);
            }
            case GL12.GL_TEXTURE_MAX_LEVEL -> {
                if (info.getMaxLevel() == param && isCachingEnabled()) return false;
                info.setMaxLevel(param);
            }
            case GL12.GL_TEXTURE_MIN_LOD -> {
                if (info.getMinLod() == param && isCachingEnabled()) return false;
                info.setMinLod(param);
            }
            case GL12.GL_TEXTURE_MAX_LOD -> {
                if (info.getMaxLod() == param && isCachingEnabled()) return false;
                info.setMaxLod(param);
            }
        }
        return true;
    }

    public static void glTexParameter(int target, int pname, IntBuffer params) {
        rejectExitCapture();
        if (handleRemovedTexParam(target, pname, params.remaining() >= 1 ? params.get(params.position()) : 0)) return;
        if (params.remaining() >= 1) {
            final int val = params.get(params.position());
            final int remapped = remapTexClamp(pname, val);
            if (remapped != val) params.put(params.position(), remapped);
            if (target == GL11.GL_TEXTURE_2D) {
                updateTexParameteriCache(getBoundTextureInfo(), pname, remapped);
            }
        }
        RENDER_BACKEND.texParameteriv(target, pname, params);
    }

    public static void glTexParameter(int target, int pname, FloatBuffer params) {
        rejectExitCapture();
        if (handleRemovedTexParam(target, pname, params.remaining() >= 1 ? (int) params.get(params.position()) : 0)) return;
        if (params.remaining() >= 1) {
            final float val = params.get(params.position());
            final float remapped = remapTexClamp(pname, val);
            if (remapped != val) params.put(params.position(), remapped);
            if (target == GL11.GL_TEXTURE_2D) {
                updateTexParameterfCache(getBoundTextureInfo(), pname, remapped);
            }
        }
        RENDER_BACKEND.texParameterfv(target, pname, params);
    }

    public static void glTexParameteri(int target, int pname, int param) {
        rejectExitCapture();
        if (handleRemovedTexParam(target, pname, param)) return;
        param = remapTexClamp(pname, param);
        final RecordMode mode = DisplayListManager.getRecordMode();
        if (mode != RecordMode.NONE) {
            DisplayListManager.recordTexParameteri(target, pname, param);
            if (mode == RecordMode.COMPILE) {
                return;
            }
        }
        if (getActiveTextureUnit() != 0) beforeUncapturedStateChange();
        if (target != GL11.GL_TEXTURE_2D) {
            RENDER_BACKEND.texParameteri(target, pname, param);
            return;
        }
        if (!updateTexParameteriCache(getBoundTextureInfo(), pname, param)) return;

        RENDER_BACKEND.texParameteri(target, pname, param);
    }

    public static boolean updateTexParameterfCache(int target, int texture, int pname, float param) {
        rejectExitCapture();
        if (target != GL11.GL_TEXTURE_2D) {
            return true;
        }
        return updateTexParameterfCache(TextureInfoCache.INSTANCE.getInfo(texture), pname, param);
    }

    static boolean updateTexParameterfCache(TextureInfo info, int pname, float param) {
        if (info == null) {
            return true;
        }
        switch (pname) {
            case GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_TEXTURE_WRAP_S, GL11.GL_TEXTURE_WRAP_T -> {
                return updateTexParameteriCache(info, pname, (int) param);
            }
            case EXTTextureFilterAnisotropic.GL_TEXTURE_MAX_ANISOTROPY_EXT -> {
                if (info.getMaxAnisotropy() == param && isCachingEnabled()) return false;
                info.setMaxAnisotropy(param);
            }
            case GL14.GL_TEXTURE_LOD_BIAS -> {
                if (info.getLodBias() == param && isCachingEnabled()) return false;
                info.setLodBias(param);
            }
        }
        return true;
    }

    public static void glTexParameterf(int target, int pname, float param) {
        rejectExitCapture();
        if (handleRemovedTexParam(target, pname, param > 0 ? (int) (param + 0.5f) : (int) (param - 0.5f))) return;
        param = remapTexClamp(pname, param);
        final RecordMode mode = DisplayListManager.getRecordMode();
        if (mode != RecordMode.NONE) {
            DisplayListManager.recordTexParameterf(target, pname, param);
            if (mode == RecordMode.COMPILE) {
                return;
            }
        }
        if (getActiveTextureUnit() != 0) beforeUncapturedStateChange();
        if (target != GL11.GL_TEXTURE_2D) {
            RENDER_BACKEND.texParameterf(target, pname, param);
            return;
        }
        if (!updateTexParameterfCache(getBoundTextureInfo(), pname, param)) return;

        RENDER_BACKEND.texParameterf(target, pname, param);
    }

    public static final long TEX_PARAM_MISS = Long.MIN_VALUE;

    public static long lookupTexParameteri(TextureInfo info, int pname) {
        if (info == null) {
            if (isRecordingDisplayList()) {
                throw new IllegalStateException(
                    "glGetTexParameteri called during display list recording with no cached TextureInfo. " +
                        "Cannot query OpenGL state during compilation!");
            }
            return TEX_PARAM_MISS;
        }
        return switch (pname) {
            case GL11.GL_TEXTURE_MIN_FILTER -> info.getMinFilter();
            case GL11.GL_TEXTURE_MAG_FILTER -> info.getMagFilter();
            case GL11.GL_TEXTURE_WRAP_S -> info.getWrapS();
            case GL11.GL_TEXTURE_WRAP_T -> info.getWrapT();
            case GL12.GL_TEXTURE_BASE_LEVEL -> info.getBaseLevel();
            case GL12.GL_TEXTURE_MAX_LEVEL -> info.getMaxLevel();
            case GL12.GL_TEXTURE_MIN_LOD -> info.getMinLod();
            case GL12.GL_TEXTURE_MAX_LOD -> info.getMaxLod();
            case GL14.GL_GENERATE_MIPMAP -> info.isGenerateMipmap() ? GL11.GL_TRUE : GL11.GL_FALSE;
            default -> {
                if (isRecordingDisplayList()) {
                    throw new IllegalStateException(String.format(
                        "glGetTexParameteri called during display list recording with uncached pname 0x%s. " +
                            "Cannot query OpenGL state during compilation!", Integer.toHexString(pname)));
                }
                yield TEX_PARAM_MISS;
            }
        };
    }

    public static int glGetTexParameteri(int target, int pname) {
        rejectExitCapture();
        if (target != GL11.GL_TEXTURE_2D || !isCachingEnabled()) {
            return RENDER_BACKEND.getTexParameteri(target, pname);
        }
        final long v = lookupTexParameteri(getBoundTextureInfo(), pname);
        return v != TEX_PARAM_MISS ? (int) v : RENDER_BACKEND.getTexParameteri(target, pname);
    }

    public static float glGetTexParameterf(int target, int pname) {
        rejectExitCapture();
        if (target != GL11.GL_TEXTURE_2D || !isCachingEnabled()) {
            return RENDER_BACKEND.getTexParameterf(target, pname);
        }
        final TextureInfo info = getBoundTextureInfo();
        if(info == null) {
            if (isRecordingDisplayList()) {
                throw new IllegalStateException(
                    "glGetTexParameterf called during display list recording with no cached TextureInfo. " +
                    "Cannot query OpenGL state during compilation!");
            }
            return RENDER_BACKEND.getTexParameterf(target, pname);
        }

        return switch (pname) {
            case EXTTextureFilterAnisotropic.GL_TEXTURE_MAX_ANISOTROPY_EXT -> info.getMaxAnisotropy();
            case GL14.GL_TEXTURE_LOD_BIAS -> info.getLodBias();
            default -> {
                if (isRecordingDisplayList()) {
                    throw new IllegalStateException(String.format(
                        "glGetTexParameterf called during display list recording with uncached pname 0x%s. " +
                        "Cannot query OpenGL state during compilation!", Integer.toHexString(pname)));
                }
                yield RENDER_BACKEND.getTexParameterf(target, pname);
            }
        };
    }

    private static int mipLevelSize(int base, int level) {
        if (base <= 0 || level < 0 || level >= Integer.SIZE - 1) return 1;
        return Math.max(base >> level, 1);
    }

    public static int glGetTexLevelParameteri(int target, int level, int pname) {
        rejectExitCapture();
        if (target != GL11.GL_TEXTURE_2D || !isCachingEnabled()) {
            return RENDER_BACKEND.getTexLevelParameteri(target, level, pname);
        }
        final TextureInfo info = TextureInfoCache.INSTANCE.getInfo(getBoundTextureForServerState());
        if (info == null) {
            if (isRecordingDisplayList()) {
                throw new IllegalStateException(String.format(
                    "glGetTexLevelParameteri called during display list recording with no cached TextureInfo for texture %d. " +
                    "Cannot query OpenGL state during compilation!", getBoundTextureForServerState()));
            }
            return RENDER_BACKEND.getTexLevelParameteri(target, level, pname);
        }
        return switch (pname) {
            case GL11.GL_TEXTURE_WIDTH -> mipLevelSize(info.getWidth(), level);
            case GL11.GL_TEXTURE_HEIGHT -> mipLevelSize(info.getHeight(), level);
            case GL11.GL_TEXTURE_INTERNAL_FORMAT -> {
                if (info.needsInternalFormatResolve() && isRecordingDisplayList()) {
                    throw new IllegalStateException(String.format("glGetTexLevelParameteri(GL_TEXTURE_INTERNAL_FORMAT) needs to resolve a generic compressed format for texture %d during display list recording", getBoundTextureForServerState()));
                }
                yield info.getResolvedInternalFormat();
            }
            default -> {
                if (isRecordingDisplayList()) {
                    throw new IllegalStateException(String.format(
                        "glGetTexLevelParameteri called during display list recording with uncached pname 0x%s for texture %d. " +
                        "Cannot query OpenGL state during compilation!", Integer.toHexString(pname), getBoundTextureForServerState()));
                }
                yield RENDER_BACKEND.getTexLevelParameteri(target, level, pname);
            }
        };
    }

    public static void glGetTexImage(int target, int level, int format, int type, ByteBuffer pixels) {
        rejectExitCapture();
        if (RenderSystem.isGLES()) {
            getTexImageViaFboReadPixels(target, level, format, type, pixels);
            return;
        }
        GLSMBuffers.suspendPixelPackBuffer();
        RENDER_BACKEND.getTexImage(target, level, format, type, pixels);
        GLSMBuffers.restorePixelPackBuffer();
    }
    public static void glGetTexImage(int target, int level, int format, int type, IntBuffer pixels) {
        rejectExitCapture();
        if (RenderSystem.isGLES()) {
            getTexImageViaFboReadPixels(target, level, format, type, pixels);
            return;
        }
        GLSMBuffers.suspendPixelPackBuffer();
        RENDER_BACKEND.getTexImage(target, level, format, type, pixels);
        GLSMBuffers.restorePixelPackBuffer();
    }

    private static void getTexImageViaFboReadPixels(int target, int level, int format, int type, Buffer pixels) {
        if (target != GL11.GL_TEXTURE_2D) {
            LOGGER.warn("glGetTexImage ES: unsupported target 0x{}", Integer.toHexString(target));
            return;
        }
        final int texId = getBoundTextureForServerState();
        if (texId == 0) return;
        final int width = RENDER_BACKEND.getTexLevelParameteri(target, level, GL11.GL_TEXTURE_WIDTH);
        final int height = RENDER_BACKEND.getTexLevelParameteri(target, level, GL11.GL_TEXTURE_HEIGHT);
        if (width <= 0 || height <= 0) return;

        final int prevReadFb = RENDER_BACKEND.getInteger(GL30.GL_READ_FRAMEBUFFER_BINDING);
        final int fbo = RENDER_BACKEND.genFramebuffers();
        try {
            RENDER_BACKEND.bindFramebuffer(GL30.GL_READ_FRAMEBUFFER, fbo);
            RENDER_BACKEND.framebufferTexture2D(GL30.GL_READ_FRAMEBUFFER, GL30.GL_COLOR_ATTACHMENT0, target, texId, level);
            final int status = RENDER_BACKEND.checkFramebufferStatus(GL30.GL_READ_FRAMEBUFFER);
            if (status != GL30.GL_FRAMEBUFFER_COMPLETE) {
                LOGGER.warn("glGetTexImage ES: FBO incomplete (0x{})", Integer.toHexString(status));
                return;
            }
            final int esType = remapPixelTypeForGLES(format, type);
            final boolean bgra = format == GL12.GL_BGRA;
            final int esFormat = bgra ? GL11.GL_RGBA : format;
            GLSMBuffers.suspendPixelPackBuffer();
            try {
                if (pixels instanceof ByteBuffer bb) {
                    RENDER_BACKEND.readPixels(0, 0, width, height, esFormat, esType, bb);
                } else if (pixels instanceof IntBuffer ib) {
                    RENDER_BACKEND.readPixels(0, 0, width, height, esFormat, esType, ib);
                }
                if (bgra) swapRedBlueInPlace(pixels);
            } finally {
                GLSMBuffers.restorePixelPackBuffer();
            }
        } finally {
            RENDER_BACKEND.bindFramebuffer(GL30.GL_READ_FRAMEBUFFER, prevReadFb);
            RENDER_BACKEND.deleteFramebuffers(fbo);
        }
    }

    private static void swapRedBlueInPlace(Buffer pixels) {
        if (pixels instanceof ByteBuffer bb) {
            final int p = bb.position();
            final int lim = bb.limit();
            for (int i = p; i + 3 < lim; i += 4) {
                final byte r = bb.get(i);
                bb.put(i, bb.get(i + 2));
                bb.put(i + 2, r);
            }
        } else if (pixels instanceof IntBuffer ib) {
            final int p = ib.position();
            final int lim = ib.limit();
            for (int i = p; i < lim; i++) {
                final int w = ib.get(i);
                ib.put(i, (w & 0xFF00FF00) | ((w & 0xFF) << 16) | ((w >> 16) & 0xFF));
            }
        }
    }
    public static void glTexStorage2D(int target, int levels, int internalFormat, int width, int height) {
        rejectExitCapture();
        RENDER_BACKEND.texStorage2D(target, levels, internalFormat, width, height);
        TextureInfoCache.INSTANCE.onTexStorage2D(target, internalFormat, width, height);
    }
    public static void glClearTexImage(int texture, int level, int format, int type, ByteBuffer data) {
        rejectExitCapture();
        RENDER_BACKEND.clearTexImage(texture, level, format, type);
        if (level == 0) TextureInfoCache.INSTANCE.onContentsChanged(texture);
    }

    public static int glGenSamplers() {
        requireContext("glGenSamplers");
        return RENDER_BACKEND.genSamplers();
    }

    public static void glGenSamplers(IntBuffer samplers) {
        requireContext("glGenSamplers");
        for (int i = samplers.position(); i < samplers.limit(); i++) samplers.put(i, RENDER_BACKEND.genSamplers());
    }

    public static void glGenSamplers(int[] samplers) {
        requireContext("glGenSamplers");
        for (int i = 0; i < samplers.length; i++) samplers[i] = RENDER_BACKEND.genSamplers();
    }

    public static void glDeleteSamplers(int sampler) {
        rejectExitCapture();
        if (sampler == 0) return;
        ctx().samplerUnits.unbindEverywhere(sampler);
        RENDER_BACKEND.deleteSamplers(sampler);
    }

    public static void glDeleteSamplers(IntBuffer samplers) {
        for (int i = samplers.position(); i < samplers.limit(); i++) glDeleteSamplers(samplers.get(i));
    }

    public static void glDeleteSamplers(int[] samplers) {
        for (int sampler : samplers) glDeleteSamplers(sampler);
    }

    public static void glBindSampler(int unit, int sampler) {
        bindSampler(ctx().samplerUnits, unit, sampler);
    }

    private static void bindSampler(SamplerUnitArray units, int unit, int sampler) {
        rejectExitCapture();
        if (isCachingEnabled() && unit >= 0 && unit < units.size() && units.get(unit) == sampler) return;
        beforeUncapturedStateChange();
        if (!units.set(unit, sampler)) {
            warnOnce("samplerUnitRange", "glBindSampler unit {} exceeds GL_MAX_COMBINED_TEXTURE_IMAGE_UNITS {}", unit, units.size());
        }
        RENDER_BACKEND.bindSampler(unit, sampler);
    }

    public static void unbindAllSamplers() {
        final SamplerUnitArray units = ctx().samplerUnits;
        if (units.getHighestBoundUnit() < 0) return;
        beforeUncapturedStateChange();
        for (int unit = units.getHighestBoundUnit(); unit >= 0; unit = units.getHighestBoundUnit()) {
            units.set(unit, 0);
            RENDER_BACKEND.bindSampler(unit, 0);
        }
    }

    public static void glBindSamplers(int first, int count, IntBuffer samplers) {
        if (samplers == null) {
            for (int i = 0; i < count; i++) glBindSampler(first + i, 0);
            return;
        }
        requireRemaining(samplers, count);
        final int base = samplers.position();
        for (int i = 0; i < count; i++) glBindSampler(first + i, samplers.get(base + i));
    }

    private static void requireRemaining(IntBuffer buffer, int count) {
        if (buffer.remaining() < count) throw new IllegalArgumentException("Number of remaining buffer elements is " + buffer.remaining() + ", must be at least " + count);
    }

    public static void glBindSamplers(int first, IntBuffer samplers) {
        if (samplers != null) glBindSamplers(first, samplers.remaining(), samplers);
    }

    public static void glBindSamplers(int first, int[] samplers) {
        if (samplers == null) return;
        for (int i = 0; i < samplers.length; i++) glBindSampler(first + i, samplers[i]);
    }

    public static int getSamplerBinding(int unit) { return ctx().samplerUnits.get(unit); }
    public static int getSamplerUnitCount() { return ctx().samplerUnits.size(); }
    public static void glSamplerParameteri(int sampler, int pname, int param) { rejectExitCapture(); RENDER_BACKEND.samplerParameteri(sampler, pname, param); }
    public static void glSamplerParameterf(int sampler, int pname, float param) { rejectExitCapture(); RENDER_BACKEND.samplerParameterf(sampler, pname, param); }

    public static void glTexImage1D(int target, int level, int internalformat, int width, int border, int format, int type, ByteBuffer pixels) {
        rejectExitCapture();
        internalformat = changeFormatIfDeprecated(internalformat);
        type = remapPixelTypeForGLES(format, type);
        type = remapTypeForRemappedInternalFormat(internalformat, type);
        if (DisplayListManager.isRecording()) {
            throw DisplayListManager.unsupportedInList("glTexImage1D");
        }
        GLSMBuffers.suspendPixelUnpackBuffer();
        RENDER_BACKEND.texImage1D(target, level, internalformat, width, border, format, type, pixels);
        GLSMBuffers.restorePixelUnpackBuffer();
    }

    public static void glTexImage3D(int target, int level, int internalformat, int width, int height, int depth, int border, int format, int type, ByteBuffer pixels) {
        rejectExitCapture();
        internalformat = changeFormatIfDeprecated(internalformat);
        type = remapPixelTypeForGLES(format, type);
        type = remapTypeForRemappedInternalFormat(internalformat, type);
        if (DisplayListManager.isRecording()) {
            throw DisplayListManager.unsupportedInList("glTexImage3D");
        }
        GLSMBuffers.suspendPixelUnpackBuffer();
        RENDER_BACKEND.texImage3D(target, level, internalformat, width, height, depth, border, format, type, pixels);
        GLSMBuffers.restorePixelUnpackBuffer();
    }

    public static void glTexImage3D(int target, int level, int internalformat, int width, int height, int depth, int border, int format, int type, IntBuffer pixels) {
        rejectExitCapture();
        internalformat = changeFormatIfDeprecated(internalformat);
        type = remapPixelTypeForGLES(format, type);
        type = remapTypeForRemappedInternalFormat(internalformat, type);
        if (DisplayListManager.isRecording()) {
            throw DisplayListManager.unsupportedInList("glTexImage3D");
        }
        GLSMBuffers.suspendPixelUnpackBuffer();
        RENDER_BACKEND.texImage3D(target, level, internalformat, width, height, depth, border, format, type, pixels);
        GLSMBuffers.restorePixelUnpackBuffer();
    }

    public static void glTexSubImage1D(int target, int level, int xoffset, int width, int format, int type, ByteBuffer pixels) {
        rejectExitCapture();
        if (DisplayListManager.isRecording()) {
            throw DisplayListManager.unsupportedInList("glTexSubImage1D");
        }
        GLSMBuffers.suspendPixelUnpackBuffer();
        RENDER_BACKEND.texSubImage1D(target, level, xoffset, width, format, type, pixels);
        GLSMBuffers.restorePixelUnpackBuffer();
    }

    public static void glTexSubImage2D(int target, int level, int xoffset, int yoffset, int width, int height, int format, int type, ByteBuffer pixels) {
        rejectExitCapture();
        type = remapPixelTypeForGLES(format, type);
        final RecordMode mode = DisplayListManager.getRecordMode();
        if (mode != RecordMode.NONE) {
            DisplayListManager.recordComplexCommand(TexSubImage2DCmd.fromByteBuffer(target, level, xoffset, yoffset, width, height, format, type, pixels, ctx().pixelUnpackState));
            if (mode == RecordMode.COMPILE) {
                return;
            }
        }
        GLSMBuffers.suspendPixelUnpackBuffer();
        RENDER_BACKEND.texSubImage2D(target, level, xoffset, yoffset, width, height, format, type, pixels);
        GLSMBuffers.restorePixelUnpackBuffer();
        TextureInfoCache.INSTANCE.onTexSubImage2D(target, level);
        maybeGenerateMipmap(target, level);
    }

    public static void glTexSubImage2D(int target, int level, int xoffset, int yoffset, int width, int height, int format, int type, IntBuffer pixels) {
        rejectExitCapture();
        type = remapPixelTypeForGLES(format, type);
        final RecordMode mode = DisplayListManager.getRecordMode();
        if (mode != RecordMode.NONE) {
            DisplayListManager.recordComplexCommand(TexSubImage2DCmd.fromIntBuffer(target, level, xoffset, yoffset, width, height, format, type, pixels, ctx().pixelUnpackState));
            if (mode == RecordMode.COMPILE) {
                return;
            }
        }
        GLSMBuffers.suspendPixelUnpackBuffer();
        RENDER_BACKEND.texSubImage2D(target, level, xoffset, yoffset, width, height, format, type, pixels);
        GLSMBuffers.restorePixelUnpackBuffer();
        TextureInfoCache.INSTANCE.onTexSubImage2D(target, level);
        maybeGenerateMipmap(target, level);
    }

    public static void glTexSubImage2D(int target, int level, int xoffset, int yoffset, int width, int height, int format, int type, long pixels_buffer_offset) {
        rejectExitCapture();
        type = remapPixelTypeForGLES(format, type);
        if (DisplayListManager.isRecording()) {
            throw DisplayListManager.unsupportedInList("glTexSubImage2D with buffer offset");
        }
        RENDER_BACKEND.texSubImage2D(target, level, xoffset, yoffset, width, height, format, type, pixels_buffer_offset);
        TextureInfoCache.INSTANCE.onTexSubImage2D(target, level);
        maybeGenerateMipmap(target, level);
    }

    public static TextureStaging beginTextureStaging(int level, int x, int y, int width, int height) {
        if (DisplayListManager.isRecording()) {
            throw DisplayListManager.unsupportedInList("beginTextureStaging");
        }
        if (!ctx().pixelUnpackState.isDefault()) {
            return null;
        }
        return RENDER_BACKEND.beginTextureStaging(level, x, y, width, height);
    }

    public static boolean commitTextureStaging(TextureStaging staging) {
        if (DisplayListManager.isRecording()) {
            throw DisplayListManager.unsupportedInList("commitTextureStaging");
        }
        GLSMBuffers.suspendPixelUnpackBuffer();
        final boolean committed;
        try {
            committed = RENDER_BACKEND.commitTextureStaging(staging);
        } finally {
            GLSMBuffers.restorePixelUnpackBuffer();
        }
        if (committed) {
            TextureInfoCache.INSTANCE.onTexSubImage2D(GL11.GL_TEXTURE_2D, staging.level());
            maybeGenerateMipmap(GL11.GL_TEXTURE_2D, staging.level());
        }
        return committed;
    }

    public static void abandonTextureStaging(TextureStaging staging) {
        RENDER_BACKEND.abandonTextureStaging(staging);
    }

    public static void trimTextureStaging() {
        RENDER_BACKEND.trimTextureStaging();
    }

    public static void glTexSubImage3D(int target, int level, int xoffset, int yoffset, int zoffset, int width, int height, int depth, int format, int type, ByteBuffer pixels) {
        rejectExitCapture();
        if (DisplayListManager.isRecording()) {
            throw DisplayListManager.unsupportedInList("glTexSubImage3D");
        }
        GLSMBuffers.suspendPixelUnpackBuffer();
        RENDER_BACKEND.texSubImage3D(target, level, xoffset, yoffset, zoffset, width, height, depth, format, type, pixels);
        GLSMBuffers.restorePixelUnpackBuffer();
    }

    public static void glCopyTexImage1D(int target, int level, int internalFormat, int x, int y, int width, int border) {
        rejectExitCapture();
        if (DisplayListManager.isRecording()) {
            throw DisplayListManager.unsupportedInList("glCopyTexImage1D");
        }
        RENDER_BACKEND.copyTexImage1D(target, level, internalFormat, x, y, width, border);
    }

    public static void glCopyTexImage2D(int target, int level, int internalFormat, int x, int y, int width, int height, int border) {
        rejectExitCapture();
        internalFormat = changeFormatIfDeprecated(internalFormat);
        if (DisplayListManager.isRecording()) {
            throw DisplayListManager.unsupportedInList("glCopyTexImage2D");
        }
        RENDER_BACKEND.copyTexImage2D(target, level, internalFormat, x, y, width, height, border);
        TextureInfoCache.INSTANCE.onCopyTexImage2D(target, level, internalFormat, width, height);
        maybeGenerateMipmap(target, level);
    }

    public static void glCopyTexSubImage1D(int target, int level, int xoffset, int x, int y, int width) {
        rejectExitCapture();
        if (DisplayListManager.isRecording()) {
            throw DisplayListManager.unsupportedInList("glCopyTexSubImage1D");
        }
        RENDER_BACKEND.copyTexSubImage1D(target, level, xoffset, x, y, width);
    }

    public static void glCopyTexSubImage2D(int target, int level, int xoffset, int yoffset, int x, int y, int width, int height) {
        rejectExitCapture();
        if (DisplayListManager.isRecording()) {
            throw DisplayListManager.unsupportedInList("glCopyTexSubImage2D");
        }
        RENDER_BACKEND.copyTexSubImage2D(target, level, xoffset, yoffset, x, y, width, height);
        TextureInfoCache.INSTANCE.onTexSubImage2D(target, level);
        maybeGenerateMipmap(target, level);
    }

    public static void glCopyTexSubImage3D(int target, int level, int xoffset, int yoffset, int zoffset, int x, int y, int width, int height) {
        rejectExitCapture();
        if (DisplayListManager.isRecording()) {
            throw DisplayListManager.unsupportedInList("glCopyTexSubImage3D");
        }
        RENDER_BACKEND.copyTexSubImage3D(target, level, xoffset, yoffset, zoffset, x, y, width, height);
    }

    // glPixelStore* isn't compiled into display lists, it executes immediately
    public static void glPixelStorei(int pname, int param) {
        rejectExitCapture();
        final GLContextState glCtx = ctx();
        if (isCachingEnabled()) {
            if (PixelStoreState.isPack(pname)) {
                glCtx.pixelPackState = glCtx.pixelPackState.with(pname, param);
            } else {
                glCtx.pixelUnpackState = glCtx.pixelUnpackState.with(pname, param);
            }
        }
        RENDER_BACKEND.pixelStorei(pname, param);
    }

    public static void glPixelStoref(int pname, float param) {
        glPixelStorei(pname, param < 0 ? -Math.round(-param) : Math.round(param));
    }

    public static void forcePixelUnpackState(PixelStoreState target) {
        PixelStoreState.applyDiff(ctx().pixelUnpackState, target, false);
    }

    public static void restorePixelUnpackState(PixelStoreState applied) {
        PixelStoreState.applyDiff(applied, ctx().pixelUnpackState, false);
    }
    public static boolean glIsTexture(int texture) { rejectExitCapture(); return !GLSMTextureLifetime.isDeferredDelete(texture) && RENDER_BACKEND.isTexture(texture); }
    public static boolean glIsSampler(int sampler) { rejectExitCapture(); return RENDER_BACKEND.isSampler(sampler); }

    public static void glGenerateMipmap(int target) {
        rejectExitCapture();
        RENDER_BACKEND.generateMipmap(target);
    }

    public static void setActiveTexture(int textureUnit) {
        glActiveTexture(textureUnit);
    }

    /**
     * In core profile, GL_CLAMP (0x2900) is removed. Remap to GL_CLAMP_TO_EDGE for wrap modes. Semantically equivalent when texture border is not used (border
     * textures are also removed in core).
     */
    public static int remapTexClamp(int pname, int param) {
        if (param == GL11.GL_CLAMP && (pname == GL11.GL_TEXTURE_WRAP_S || pname == GL11.GL_TEXTURE_WRAP_T || pname == GL12.GL_TEXTURE_WRAP_R)) {
            return GL12.GL_CLAMP_TO_EDGE;
        }
        return param;
    }

    public static float remapTexClamp(int pname, float param) {
        if ((int) param == GL11.GL_CLAMP && (pname == GL11.GL_TEXTURE_WRAP_S || pname == GL11.GL_TEXTURE_WRAP_T || pname == GL12.GL_TEXTURE_WRAP_R)) {
            return (float) GL12.GL_CLAMP_TO_EDGE;
        }
        return param;
    }

    public static void remapTexClampBuffer(int pname, IntBuffer params) {
        if (params.remaining() >= 1) {
            final int val = params.get(params.position());
            final int remapped = remapTexClamp(pname, val);
            if (remapped != val) params.put(params.position(), remapped);
        }
    }

    public static void glBindImageTexture(int unit, int texture, int level, boolean layered, int layer, int access, int format) {
        rejectExitCapture();
        if (GLSMTextureLifetime.isDeferredDelete(texture)) return;
        final GLContextState glCtx = ctx();
        final ImageUnitBinding binding = glCtx.imageUnits.get(unit);
        if (binding == null) {
            warnOnce("imageUnitRange", "glBindImageTexture unit {} exceeds GL_MAX_IMAGE_UNITS {}", unit, glCtx.imageUnits.size());
        } else {
            binding.setBinding(texture, level, texture != 0 && layered, texture != 0 ? layer : 0, access, format);
            if (texture != 0 && unit > glCtx.maxBoundImageUnit) glCtx.maxBoundImageUnit = unit;
        }
        if (access != GL15.GL_READ_ONLY) TextureInfoCache.INSTANCE.onWrittenOutsideUploads(texture);
        RENDER_BACKEND.bindImageTexture(unit, texture, level, layered, layer, access, format);
    }

    public static void glBindImageTextures(int first, int count, IntBuffer textures) {
        if (first < 0 || first + count > ctx().imageUnits.size()) return;
        if (textures == null) {
            for (int i = 0; i < count; i++) bindImageUnitByName(first + i, 0);
            return;
        }
        requireRemaining(textures, count);
        for (int i = 0; i < count; i++) bindImageUnitByName(first + i, textures.get(textures.position() + i));
    }

    public static void glBindImageTextures(int first, IntBuffer textures) {
        if (textures != null) glBindImageTextures(first, textures.remaining(), textures);
    }

    public static void glBindImageTextures(int first, int[] textures) {
        if (textures != null) glBindImageTextures(first, textures.length, IntBuffer.wrap(textures));
    }

    private static void bindImageUnitByName(int unit, int texture) {
        if (texture == 0) {
            glBindImageTexture(unit, 0, 0, false, 0, GL15.GL_READ_ONLY, GL30.GL_R8);
            return;
        }
        if (GLSMTextureLifetime.isDeferredDelete(texture)) return;
        final int format = TextureInfoCache.INSTANCE.getInfo(texture).getInternalFormat();
        if (!isImageFormat(format)) return;
        final boolean layered = switch (GLSMTextureLifetime.targetOf(texture)) {
            case GL30.GL_TEXTURE_1D_ARRAY, GL30.GL_TEXTURE_2D_ARRAY, GL12.GL_TEXTURE_3D, GL13.GL_TEXTURE_CUBE_MAP, GL40.GL_TEXTURE_CUBE_MAP_ARRAY, GL32.GL_TEXTURE_2D_MULTISAMPLE_ARRAY -> true;
            default -> false;
        };
        glBindImageTexture(unit, texture, 0, layered, 0, GL15.GL_READ_WRITE, format);
    }

    private static boolean isImageFormat(int format) {
        return switch (format) {
            case GL30.GL_RGBA32F, GL30.GL_RGBA16F, GL30.GL_RG32F, GL30.GL_RG16F, GL30.GL_R11F_G11F_B10F, GL30.GL_R32F, GL30.GL_R16F,
                 GL30.GL_RGBA32UI, GL30.GL_RGBA16UI, GL33.GL_RGB10_A2UI, GL30.GL_RGBA8UI, GL30.GL_RG32UI, GL30.GL_RG16UI, GL30.GL_RG8UI, GL30.GL_R32UI, GL30.GL_R16UI, GL30.GL_R8UI,
                 GL30.GL_RGBA32I, GL30.GL_RGBA16I, GL30.GL_RGBA8I, GL30.GL_RG32I, GL30.GL_RG16I, GL30.GL_RG8I, GL30.GL_R32I, GL30.GL_R16I, GL30.GL_R8I,
                 GL11.GL_RGBA16, GL11.GL_RGB10_A2, GL11.GL_RGBA8, GL30.GL_RG16, GL30.GL_RG8, GL30.GL_R16, GL30.GL_R8,
                 GL31.GL_RGBA16_SNORM, GL31.GL_RGBA8_SNORM, GL31.GL_RG16_SNORM, GL31.GL_RG8_SNORM, GL31.GL_R16_SNORM, GL31.GL_R8_SNORM -> true;
            default -> false;
        };
    }

    public static ImageUnitBinding getImageUnitBinding(int unit) {
        return ctx().imageUnits.get(unit);
    }
    public static void glCopyImageSubData(int srcName, int srcTarget, int srcLevel, int srcX, int srcY, int srcZ,
                                           int dstName, int dstTarget, int dstLevel, int dstX, int dstY, int dstZ,
                                           int srcWidth, int srcHeight, int srcDepth) {
        rejectExitCapture();
        RENDER_BACKEND.copyImageSubData(srcName, srcTarget, srcLevel, srcX, srcY, srcZ,
            dstName, dstTarget, dstLevel, dstX, dstY, dstZ, srcWidth, srcHeight, srcDepth);
        if (dstTarget != GL30.GL_RENDERBUFFER && dstLevel == 0) TextureInfoCache.INSTANCE.onContentsChanged(dstName);
    }
    public static void glGetTexImage(int target, int level, int format, int type, long pixels) {
        rejectExitCapture();
        RENDER_BACKEND.getTexImage(target, level, format, type, pixels);
    }

    public static IntegerStateStack getActiveTextureUnitStack() {
        return ctx().activeTextureUnit;
    }

    public static TextureUnitArray getTextures() {
        return ctx().textures;
    }
}
