package com.gtnewhorizons.angelica.glsm.ffp;

import com.gtnewhorizon.gtnhlib.client.renderer.vertex.VertexFlags;
import com.gtnewhorizon.gtnhlib.client.renderer.vertex.VertexFormat;
import com.gtnewhorizons.angelica.config.SystemProperties;
import com.gtnewhorizons.angelica.glsm.CompatUniformManager;
import com.gtnewhorizons.angelica.glsm.GLContextState;
import com.gtnewhorizons.angelica.glsm.GLStateManager;
import com.gtnewhorizons.angelica.glsm.hooks.DeferredBlendHandler;
import com.gtnewhorizons.angelica.glsm.hooks.GLSMHooks;
import com.gtnewhorizons.angelica.glsm.QuadConverter;
import com.gtnewhorizons.angelica.glsm.profiling.Tracy;
import com.gtnewhorizons.angelica.glsm.stacks.StackIdAllocator;
import com.gtnewhorizons.angelica.glsm.stacks.Vec3fStack;
import com.gtnewhorizons.angelica.glsm.stacks.Vec4fStack;
import com.gtnewhorizons.angelica.glsm.streaming.TessellatorStreamingDrawer;
import lombok.Getter;
import org.joml.Vector3f;
import org.joml.Vector4f;

import java.util.Arrays;

import static com.gtnewhorizons.angelica.glsm.backend.BackendManager.RENDER_BACKEND;

/**
 * FFP shader manager
 */
public final class ShaderManager {

    private static final ShaderCache cache = new ShaderCache();
    private final Uniforms uniforms = new Uniforms();

    @Getter
    private boolean active;
    private Program currentProgram = null;
    private int lastBoundProgramId;
    private long currentVertexKeyPacked = Long.MIN_VALUE;
    private final long[] currentFKScratch = new long[FragmentKey.MAX_UNITS];
    private final long[] currentFKPacked = new long[FragmentKey.MAX_UNITS];
    private int currentFKLen = 0;

    public final Vector3f currentNormal = new Vector3f(0.0f, 0.0f, 1.0f);
    public final Vector4f[] currentTexCoords = {
        new Vector4f(0.0f, 0.0f, 0.0f, 1.0f),
        new Vector4f(0.0f, 0.0f, 0.0f, 1.0f),
        new Vector4f(0.0f, 0.0f, 0.0f, 1.0f),
        new Vector4f(0.0f, 0.0f, 0.0f, 1.0f),
    };
    public final Vec3fStack normalStack = new Vec3fStack(currentNormal, StackIdAllocator.nextId());
    public final Vec4fStack texCoordStack = new Vec4fStack(currentTexCoords[0], StackIdAllocator.nextId());
    public int normalGeneration;
    public int texCoordGeneration;

    private int preDrawCalls;
    private int lastFramePreDrawCalls;

    public static long variantSwitches;

    public static Vector3f getCurrentNormal() { return GLStateManager.ctx().ffp.currentNormal; }
    public static Vector4f getCurrentTexCoord() { return GLStateManager.ctx().ffp.currentTexCoords[0]; }
    public static Vec3fStack getNormalStack() { return GLStateManager.ctx().ffp.normalStack; }
    public static Vec4fStack getTexCoordStack() { return GLStateManager.ctx().ffp.texCoordStack; }
    @Getter private static boolean enabled = false;

    static {
        cache.setDumpDir(SystemProperties.shaderDumpDir("ffp"));
        VertexFormat.registerSetupBufferStateOverride((format, offset) -> {
            VAOManager.setCurrentVertexFlags(format.getVertexFlags());
            return false;
        });
    }

    public ShaderManager() {
        active = enabled;
        lastBoundProgramId = enabled ? 0 : -1;
    }

    public static ShaderManager getInstance() {
        return GLStateManager.ctx().ffp;
    }

    public static void enable() {
        warmUp();
        enabled = true;
        final GLContextState glCtx = GLStateManager.ctx();
        if (glCtx.activeProgram == 0) glCtx.ffp.activate();
        GLStateManager.LOGGER.info("FFP shader emulation enabled");
    }

    // Force loading of classes before the SplashThread kicks off
    // Ensure it's GL free
    private static void warmUp() {
        try {
            final long[] fkScratch = new long[FragmentKey.MAX_UNITS];
            final int fkLen = FragmentKey.packFromState(fkScratch);
            final int fragMask = FragmentKey.unitMaskFromPacked(fkScratch, fkLen);
            final long vkPacked = VertexKey.packFromState(true, true, true, true, fragMask);
            final VertexKey vk = VertexKey.fromPacked(vkPacked);
            VertexShaderGenerator.generate(vk);
            VertexShaderGenerator.generate(VertexKey.fromPacked(VertexKey.withInstancing(vkPacked, Instancing.CUBE)));
            VertexShaderGenerator.generate(VertexKey.fromPacked(VertexKey.withInstancing(vkPacked, Instancing.PARTICLE)));
            FragmentShaderGenerator.generate(FragmentKey.fromPacked(fkScratch, fkLen));
            GeometryShaderGenerator.generate(vk);
            final Class<?>[] touched = { Program.class, ShaderCache.class, TessellatorStreamingDrawer.class, QuadConverter.class };
            for (Class<?> c : touched) {
                c.getName();
            }
        } catch (Throwable t) {
            GLStateManager.LOGGER.warn("FFP warmup failed; draw-path classes will resolve lazily", t);
        }
    }

    public static void disable() {
        enabled = false;
    }

    public void activate() {
        active = true;
        lastBoundProgramId = GLStateManager.getActiveProgram();
    }

    public void invalidateProgram() {
        lastBoundProgramId = -1;
        currentProgram = null;
        currentVertexKeyPacked = Long.MIN_VALUE;
        uniforms.invalidateBinding();
    }

    public void deactivate() {
        active = false;
        currentProgram = null;
        lastBoundProgramId = -1;
        currentVertexKeyPacked = Long.MIN_VALUE;
        currentFKLen = 0;
        GLStateManager.forceAttribDefaultsDirty();
    }

    public void preDraw(GLContextState glCtx) {
        GLSMHooks.resolvePendingProgram();
        final DeferredBlendHandler bh = GLSMHooks.blendHandler;
        if (bh != null) bh.flushDeferredBlend();

        // Handle FFP & Iris uniforms
        final int currentProgramId = glCtx.activeProgram;
        if (currentProgramId != 0) {
            if (!CompatUniformManager.refreshCompatUniforms(currentProgramId, glCtx)) {
                return; // Don't emulate FFP on non-iris core shaders
            }
        }

        final int vertexFlags = glCtx.vaos.getVertexFlags();
        final boolean hasColor = (vertexFlags & VertexFlags.COLOR_BIT) != 0;
        final boolean hasNormal =   (vertexFlags & VertexFlags.NORMAL_BIT) != 0;
        final boolean hasTexCoord = (vertexFlags & VertexFlags.TEXTURE_BIT) != 0;
        final boolean hasLightmap = (vertexFlags & VertexFlags.BRIGHTNESS_BIT) != 0;
        GLStateManager.flushDeferredVertexAttribs(glCtx, hasColor, hasNormal, hasTexCoord, hasLightmap);

        if (!active) return;

        preDrawCalls++;
        final int fkLen = FragmentKey.packFromState(currentFKScratch, glCtx);
        final int fragMask = FragmentKey.unitMaskFromPacked(currentFKScratch, fkLen);
        final long vkPacked = VertexKey.packFromState(hasColor, hasNormal, hasTexCoord, hasLightmap, fragMask, glCtx);

        if (vkPacked != currentVertexKeyPacked || !Arrays.equals(currentFKScratch, 0, fkLen, currentFKPacked, 0, currentFKLen)) {
            commitVariant(vkPacked, fkLen);
        }

        uploadUniforms(glCtx);
    }

    private void commitVariant(long vkPacked, int fkLen) {
        currentVertexKeyPacked = vkPacked;
        System.arraycopy(currentFKScratch, 0, currentFKPacked, 0, fkLen);
        currentFKLen = fkLen;
        currentProgram = cache.getOrCreate(vkPacked, currentFKPacked, currentFKLen);
        final int programId = currentProgram.getProgramId();
        if (programId != lastBoundProgramId) {
            if (Tracy.ENABLED) variantSwitches++;
            RENDER_BACKEND.useProgram(programId);
            lastBoundProgramId = programId;
        }
    }

    private void uploadUniforms(GLContextState glCtx) {
        if (currentProgram != null) {
            uniforms.upload(glCtx);
        }
    }

    public static void endFrame() {
        final ShaderManager sm = GLStateManager.ctx().ffp;
        sm.lastFramePreDrawCalls = sm.preDrawCalls;
        sm.preDrawCalls = 0;
        sm.uniforms.endFrame();
        FfpExtendedAttribs.endFrame();
    }

    public void setNormal(float x, float y, float z) {
        normalStack.beforeModify();
        currentNormal.set(x, y, z);
        normalGeneration++;
    }

    public void setTexCoord(float s, float t, float r, float q) {
        texCoordStack.beforeModify();
        currentTexCoords[0].set(s, t, r, q);
        texCoordGeneration++;
    }

    public void setTexCoord(int unit, float s, float t, float r, float q) {
        if (unit == 0) texCoordStack.beforeModify();
        currentTexCoords[unit].set(s, t, r, q);
        texCoordGeneration++;
    }

    public String getDebugInfo() {
        return String.format("FFP: %d programs (%d vert, %d frag variants) | UBO: %d/%d draws wrote (mat %d, light %d, frag %d, misc %d)",
            cache.getProgramCount(), cache.getVertexVariantCount(), cache.getFragmentVariantCount(),
            uniforms.lastFrameBlockWrites, lastFramePreDrawCalls,
            uniforms.lastFrameStagedMatrices, uniforms.lastFrameStagedLighting,
            uniforms.lastFrameStagedFragment, uniforms.lastFrameStagedMisc);
    }

    public int statLastFramePreDrawCalls() { return lastFramePreDrawCalls; }
    public int statLastFrameBlockWrites() { return uniforms.lastFrameBlockWrites; }
    public int statLastFrameBlockSkips() { return uniforms.lastFrameBlockSkips; }
    public int statLastFrameStagedMatrices() { return uniforms.lastFrameStagedMatrices; }
    public int statLastFrameStagedLighting() { return uniforms.lastFrameStagedLighting; }
    public int statLastFrameStagedFragment() { return uniforms.lastFrameStagedFragment; }
    public int statLastFrameStagedColor() { return uniforms.lastFrameStagedColor; }
    public int statLastFrameStagedNormal() { return uniforms.lastFrameStagedNormal; }
    public int statLastFrameStagedTexCoord() { return uniforms.lastFrameStagedTexCoord; }
    public int statLastFrameStagedLightmap() { return uniforms.lastFrameStagedLightmap; }
    public int statLastFrameStagedTexGen() { return uniforms.lastFrameStagedTexGen; }
    public int statLastFrameStagedClipPlanes() { return uniforms.lastFrameStagedClipPlanes; }
    public int statLastFrameStagedMisc() { return uniforms.lastFrameStagedMisc; }
    public int statProgramCount() { return cache.getProgramCount(); }
}
