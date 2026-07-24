package com.gtnewhorizons.angelica.glsm.ffp;

import com.gtnewhorizon.gtnhlib.client.renderer.vertex.VertexFlags;
import com.gtnewhorizons.angelica.glsm.CompatUniformManager;
import com.gtnewhorizons.angelica.glsm.GLStateManager;
import com.gtnewhorizons.angelica.glsm.hooks.DeferredBlendHandler;
import com.gtnewhorizons.angelica.glsm.hooks.GLSMHooks;
import com.gtnewhorizons.angelica.glsm.hooks.GLSMInitConfig;
import com.gtnewhorizons.angelica.glsm.QuadConverter;
import com.gtnewhorizons.angelica.glsm.stacks.Vec3fStack;
import com.gtnewhorizons.angelica.glsm.stacks.Vec4fStack;
import com.gtnewhorizons.angelica.glsm.streaming.TessellatorStreamingDrawer;
import lombok.Getter;
import org.joml.Vector3f;
import org.joml.Vector4f;

import java.nio.file.Paths;
import java.util.Arrays;

import static com.gtnewhorizons.angelica.glsm.backend.BackendManager.RENDER_BACKEND;

/**
 * FFP shader manager
 */
public final class ShaderManager {

    private static final class Holder {

        static final ShaderManager INSTANCE = new ShaderManager();
    }

    private final ShaderCache cache = new ShaderCache();
    private final Uniforms uniforms = new Uniforms();

    @Getter
    private boolean active = false;
    private Program currentProgram = null;
    private int lastBoundProgramId = -1;
    private long currentVertexKeyPacked = Long.MIN_VALUE;
    private final long[] currentFKScratch = new long[FragmentKey.MAX_UNITS];
    private final long[] currentFKPacked = new long[FragmentKey.MAX_UNITS];
    private int currentFKLen = 0;

    @Getter private static final Vector3f currentNormal = new Vector3f(0.0f, 0.0f, 1.0f);
    private static final Vector4f[] currentTexCoords = {
        new Vector4f(0.0f, 0.0f, 0.0f, 1.0f),
        new Vector4f(0.0f, 0.0f, 0.0f, 1.0f),
        new Vector4f(0.0f, 0.0f, 0.0f, 1.0f),
        new Vector4f(0.0f, 0.0f, 0.0f, 1.0f),
    };
    @Getter private static final Vec3fStack normalStack = new Vec3fStack(currentNormal);
    @Getter private static final Vec4fStack texCoordStack = new Vec4fStack(currentTexCoords[0]);
    @Getter private static int normalGeneration;
    @Getter private static int texCoordGeneration;

    public static Vector4f getCurrentTexCoord() { return currentTexCoords[0]; }
    public static Vector4f getCurrentTexCoord(int unit) { return currentTexCoords[unit]; }
    @Getter private boolean enabled = false;

    private ShaderManager() {
        if (Boolean.parseBoolean(System.getProperty("angelica.dumpShaders", "false"))) {
            cache.setDumpDir(Paths.get("ffp_shaders"));
        }
    }

    public static ShaderManager getInstance() {
        return Holder.INSTANCE;
    }

    public void enable() {
        warmUp();
        enabled = true;

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

    public void disable() {
        enabled = false;
    }

    public void activate() {
        active = true;
        lastBoundProgramId = GLStateManager.getActiveProgram();
    }

    public void deactivate() {
        active = false;
        currentProgram = null;
        lastBoundProgramId = -1;
        currentVertexKeyPacked = Long.MIN_VALUE;
        currentFKLen = 0;
    }

    public void preDraw() {
        final DeferredBlendHandler bh = GLSMHooks.blendHandler;
        if (bh != null) bh.flushDeferredBlend();

        // Handle FFP & Iris uniforms
        final int currentProgramId = GLStateManager.getActiveProgram();
        if (currentProgramId != 0) {
            if (!CompatUniformManager.refreshCompatUniforms(currentProgramId)) {
                return; // Don't emulate FFP on non-iris core shaders
            }
        }

        final int vertexFlags = VAOManager.getCurrentVertexFlags();
        final boolean hasColor = (vertexFlags & VertexFlags.COLOR_BIT) != 0;
        final boolean hasNormal =   (vertexFlags & VertexFlags.NORMAL_BIT) != 0;
        final boolean hasTexCoord = (vertexFlags & VertexFlags.TEXTURE_BIT) != 0;
        final boolean hasLightmap = (vertexFlags & VertexFlags.BRIGHTNESS_BIT) != 0;
        GLStateManager.flushDeferredVertexAttribs(hasColor, hasNormal, hasTexCoord, hasLightmap);

        if (!active) return;


        final int fkLen = FragmentKey.packFromState(currentFKScratch);
        final int fragMask = FragmentKey.unitMaskFromPacked(currentFKScratch, fkLen);
        final long vkPacked = VertexKey.packFromState(hasColor, hasNormal, hasTexCoord, hasLightmap, fragMask);

        if (vkPacked != currentVertexKeyPacked || !Arrays.equals(currentFKScratch, 0, fkLen, currentFKPacked, 0, currentFKLen)) {
            commitVariant(vkPacked, fkLen);
        }

        uploadUniforms();
    }

    private void commitVariant(long vkPacked, int fkLen) {
        currentVertexKeyPacked = vkPacked;
        System.arraycopy(currentFKScratch, 0, currentFKPacked, 0, fkLen);
        currentFKLen = fkLen;
        currentProgram = cache.getOrCreate(vkPacked, currentFKPacked, currentFKLen);
        final int programId = currentProgram.getProgramId();
        if (programId != lastBoundProgramId) {
            RENDER_BACKEND.useProgram(programId);
            lastBoundProgramId = programId;
        }
    }

    private void uploadUniforms() {
        if (currentProgram != null) {
            uniforms.upload(currentProgram);
        }
    }

    public static void setCurrentNormal(float x, float y, float z) {
        currentNormal.set(x, y, z);
        normalGeneration++;
    }

    public static void setCurrentTexCoord(float s, float t, float r, float q) {
        currentTexCoords[0].set(s, t, r, q);
        texCoordGeneration++;
    }

    public static void setCurrentTexCoord(int unit, float s, float t, float r, float q) {
        currentTexCoords[unit].set(s, t, r, q);
        texCoordGeneration++;
    }

    public static void bumpNormalGeneration() { normalGeneration++; }
    public static void bumpTexCoordGeneration() { texCoordGeneration++; }

    public void destroy() {
        cache.destroy();
        uniforms.destroy();
        final GLSMInitConfig config = GLStateManager.getInitConfig();
        if (config != null && config.getStreamingDrawerDestroy() != null) config.getStreamingDrawerDestroy().run();
        com.gtnewhorizons.angelica.glsm.QuadConverter.destroy();
        active = false;
        currentProgram = null;
    }

    public String getDebugInfo() {
        return String.format("FFP: %d programs (%d vert, %d frag variants)",
            cache.getProgramCount(), cache.getVertexVariantCount(), cache.getFragmentVariantCount());
    }
}
