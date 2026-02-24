package com.gtnewhorizons.angelica.glsm.ffp;

import com.gtnewhorizon.gtnhlib.client.renderer.stacks.IStateStack;
import com.gtnewhorizon.gtnhlib.client.renderer.vertex.VertexFlags;
import com.gtnewhorizon.gtnhlib.client.renderer.vertex.VertexFormat;
import com.gtnewhorizons.angelica.glsm.CompatUniformManager;
import com.gtnewhorizons.angelica.glsm.GLStateManager;
import com.gtnewhorizons.angelica.glsm.stacks.Vec3fStack;
import com.gtnewhorizons.angelica.glsm.stacks.Vec4fStack;
import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;
import lombok.Getter;
import net.coderbot.iris.gl.blending.BlendModeStorage;
import net.minecraft.launchwrapper.Launch;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.joml.Vector3f;
import org.joml.Vector4f;
import org.lwjgl.opengl.GL20;

import java.nio.file.Paths;

/**
 * FFP shader manager
 */
public class ShaderManager {
    private static final Logger LOGGER = LogManager.getLogger("FFPShaderManager");

    private static final class Holder { static final ShaderManager INSTANCE = new ShaderManager(); }

    private final ShaderCache cache = new ShaderCache();
    private final Uniforms uniforms = new Uniforms();
    private final Int2IntOpenHashMap vaoVertexFlags = new Int2IntOpenHashMap();

    @Getter
    private boolean active = false;
    private Program currentProgram = null;
    private long currentVertexKeyPacked = Long.MIN_VALUE;
    private long currentFragmentKeyPacked = Long.MIN_VALUE;

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
        boolean isDev = false;
        try {
            final Object deobfEnv = Launch.blackboard != null ? Launch.blackboard.get("fml.deobfuscatedEnvironment") : null;
            isDev = Boolean.TRUE.equals(deobfEnv);
        } catch (Exception ignored) {}
        if (isDev || Boolean.parseBoolean(System.getProperty("angelica.ffp.dumpShaders", "false"))) {
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
            final int boundVao = GLStateManager.getBoundVAO();
            if (boundVao != 0) {
                vaoVertexFlags.put(boundVao, currentVertexFlags);
            }
            return false;
        });

        LOGGER.info("FFP shader emulation enabled");
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
        currentFragmentKeyPacked = Long.MIN_VALUE;
    }

    public void preDraw(boolean hasColor, boolean hasNormal, boolean hasTexCoord, boolean hasLightmap) {
        GLStateManager.flushDeferredVertexAttribs();
        BlendModeStorage.flushDeferredBlend();

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
        final long fkPacked = FragmentKey.packFromState();

        if (vkPacked != currentVertexKeyPacked || fkPacked != currentFragmentKeyPacked) {
            currentVertexKeyPacked = vkPacked;
            currentFragmentKeyPacked = fkPacked;

            currentProgram = cache.getOrCreate(vkPacked, fkPacked);
            GL20.glUseProgram(currentProgram.getProgramId());
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
        currentVertexKeyPacked = VertexKey.packFromState(hasColor, hasNormal, hasTexCoord, hasLightmap);
        currentFragmentKeyPacked = FragmentKey.packFromState();

        currentProgram = cache.getOrCreate(currentVertexKeyPacked, currentFragmentKeyPacked);
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
        TessellatorStreamingDrawer.destroy();
        com.gtnewhorizons.angelica.glsm.QuadConverter.destroy();
        active = false;
        currentProgram = null;
    }

    public String getDebugInfo() {
        return String.format("FFP: %d programs (%d vert, %d frag variants)",
            cache.getProgramCount(), cache.getVertexVariantCount(), cache.getFragmentVariantCount());
    }
}
