package com.gtnewhorizons.angelica.glsm.ffp;

import com.gtnewhorizon.gtnhlib.client.renderer.vertex.VertexFlags;
import com.gtnewhorizon.gtnhlib.client.renderer.vertex.VertexFormat;
import com.gtnewhorizons.angelica.glsm.CompatUniformManager;
import com.gtnewhorizons.angelica.glsm.GLStateManager;
import com.gtnewhorizons.angelica.glsm.hooks.DeferredBlendHandler;
import com.gtnewhorizons.angelica.glsm.hooks.GLSMHooks;
import com.gtnewhorizons.angelica.glsm.stacks.Vec3fStack;
import com.gtnewhorizons.angelica.glsm.stacks.Vec4fStack;
import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;
import com.gtnewhorizons.angelica.glsm.hooks.GLSMInitConfig;
import lombok.Getter;
import org.joml.Vector3f;
import org.joml.Vector4f;
import org.lwjgl.opengl.GL20;

import java.nio.file.Paths;
import java.util.Arrays;

/**
 * FFP shader manager
 */
public class ShaderManager {

    private static final class Holder {

        static final ShaderManager INSTANCE = new ShaderManager();
    }

    private final ShaderCache cache = new ShaderCache();
    private final Uniforms uniforms = new Uniforms();
    private final Int2IntOpenHashMap vaoVertexFlags = new Int2IntOpenHashMap();

    @Getter
    private boolean active = false;
    private Program currentProgram = null;
    private long currentVertexKeyPacked = Long.MIN_VALUE;
    private final long[] currentFKScratch = new long[FragmentKey.MAX_UNITS];
    private final long[] currentFKPacked = new long[FragmentKey.MAX_UNITS];
    private int currentFKLen = 0;

    @Getter private static final Vector3f currentNormal = new Vector3f(0.0f, 0.0f, 1.0f);
    @Getter private static final Vector4f currentTexCoord = new Vector4f(0.0f, 0.0f, 0.0f, 1.0f);
    @Getter private static final Vec3fStack normalStack = new Vec3fStack(currentNormal);
    @Getter private static final Vec4fStack texCoordStack = new Vec4fStack(currentTexCoord);
    @Getter private static int normalGeneration;
    @Getter private static int texCoordGeneration;
    @Getter private boolean enabled = false;
    private int currentVertexFlags = VertexFlags.TEXTURE_BIT | VertexFlags.COLOR_BIT | VertexFlags.NORMAL_BIT;

    private ShaderManager() {
        vaoVertexFlags.defaultReturnValue(-1);
        if (Boolean.parseBoolean(System.getProperty("angelica.dumpShaders", "false"))) {
            cache.setDumpDir(Paths.get("ffp_shaders"));
        }
    }

    public static ShaderManager getInstance() {
        return Holder.INSTANCE;
    }

    public void enable() {
        enabled = true;

        VertexFormat.registerSetupBufferStateOverride((format, offset) -> {
            currentVertexFlags = format.getVertexFlags();
            vaoVertexFlags.put(GLStateManager.getBoundVAO(), currentVertexFlags);
            return false;
        });

        GLStateManager.LOGGER.info("FFP shader emulation enabled");
    }

    public void disable() {
        enabled = false;
    }

    public void activate() {
        active = true;
        updateVariant(true, true, true, true);
        uploadUniforms();
    }

    public void deactivate() {
        active = false;
        currentProgram = null;
        currentVertexKeyPacked = Long.MIN_VALUE;
        currentFKLen = 0;
    }

    public void preDraw(boolean hasColor, boolean hasNormal, boolean hasTexCoord, boolean hasLightmap) {
        GLStateManager.flushDeferredVertexAttribs();
        final DeferredBlendHandler bh = GLSMHooks.blendHandler;
        if (bh != null) bh.flushDeferredBlend();

        if (!active) {
            if (enabled) {
                final int currentProgramId = GLStateManager.getActiveProgram();
                if (currentProgramId != 0) {
                    CompatUniformManager.onUseProgram(currentProgramId);
                    return;
                }
                active = true;
            } else {
                return;
            }
        }

        final long vkPacked = VertexKey.packFromState(hasColor, hasNormal, hasTexCoord, hasLightmap);
        final int fkLen = FragmentKey.packFromState(currentFKScratch);

        if (vkPacked != currentVertexKeyPacked || !Arrays.equals(currentFKScratch, 0, fkLen, currentFKPacked, 0, currentFKLen)) {
            commitVariant(vkPacked, fkLen);
        }

        uploadUniforms();
    }

    public void preDraw(int vertexFlags) {
        currentVertexFlags = vertexFlags;
        preDraw(
            (vertexFlags & VertexFlags.COLOR_BIT) != 0,
            (vertexFlags & VertexFlags.NORMAL_BIT) != 0,
            (vertexFlags & VertexFlags.TEXTURE_BIT) != 0,
            (vertexFlags & VertexFlags.BRIGHTNESS_BIT) != 0
        );
    }

    public void preDraw() {
        preDraw(currentVertexFlags);
    }

    private void updateVariant(boolean hasColor, boolean hasNormal, boolean hasTexCoord, boolean hasLightmap) {
        final long vkPacked = VertexKey.packFromState(hasColor, hasNormal, hasTexCoord, hasLightmap);
        final int fkLen = FragmentKey.packFromState(currentFKScratch);
        commitVariant(vkPacked, fkLen);
    }

    private void commitVariant(long vkPacked, int fkLen) {
        currentVertexKeyPacked = vkPacked;
        System.arraycopy(currentFKScratch, 0, currentFKPacked, 0, fkLen);
        currentFKLen = fkLen;
        currentProgram = cache.getOrCreate(vkPacked, currentFKPacked, currentFKLen);
        GL20.glUseProgram(currentProgram.getProgramId());
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
        currentTexCoord.set(s, t, r, q);
        texCoordGeneration++;
    }

    public static void bumpNormalGeneration() { normalGeneration++; }
    public static void bumpTexCoordGeneration() { texCoordGeneration++; }

    public void enableClientVertexFlag(int flag) {currentVertexFlags |= flag;}
    public void disableClientVertexFlag(int flag) {currentVertexFlags &= ~flag;}
    public int getCurrentVertexFlags() {return currentVertexFlags;}
    public void setCurrentVertexFlags(int flags) {currentVertexFlags = flags;}

    public void onBindVertexArray(int vaoId) {
        final int flags = vaoVertexFlags.get(vaoId);
        if (flags != -1) {
            currentVertexFlags = flags;
        }
    }

    public void onDeleteVertexArray(int vaoId) {
        vaoVertexFlags.remove(vaoId);
    }

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
