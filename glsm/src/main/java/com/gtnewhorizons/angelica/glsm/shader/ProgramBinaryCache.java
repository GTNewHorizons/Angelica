package com.gtnewhorizons.angelica.glsm.shader;

import com.gtnewhorizon.gtnhlib.bytebuf.MemoryUtilities;
import com.gtnewhorizons.angelica.glsm.GLStateManager;
import com.gtnewhorizons.angelica.glsm.RenderSystem;
import com.gtnewhorizons.angelica.glsm.ffp.ShaderManager;
import com.gtnewhorizons.angelica.glsm.threading.AngelicaWorkers;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL41;

import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

import static com.gtnewhorizons.angelica.glsm.backend.BackendManager.RENDER_BACKEND;

/**
 * Saves linked GL programs to disk and loads them back with glProgramBinary, skipping compile and link.
 */
public final class ProgramBinaryCache {
    private static final Logger LOGGER = LogManager.getLogger("ProgramBinaryCache");
    static final String LAYER = "gl-program";
    static final String COMPILED_SETTINGS_LAYER = "gl-program-settings";

    private static volatile boolean enabled;
    private static String driverId;
    private static Set<String> keysInUse;
    private static final AtomicBoolean rejectWarned = new AtomicBoolean();

    private ProgramBinaryCache() {}

    public static void setEnabled(boolean value) {
        enabled = value;
    }

    public static boolean isEnabled() {
        return enabled && RenderSystem.supportsProgramBinary() && ShaderDiskCache.isEnabled();
    }

    public static Key key() {
        return key(driverId(), ShaderManager.isEnabled());
    }

    static Key key(String driver, boolean ffpEnabled) {
        return new Key(ShaderDiskCache.key(LAYER).str(driver).b(ffpEnabled));
    }

    public static void markRetrievable(int program) {
        GLStateManager.glProgramParameteri(program, GL41.GL_PROGRAM_BINARY_RETRIEVABLE_HINT, GL11.GL_TRUE);
    }

    public static int load(Key key) {
        final ShaderDiskCache.Key diskKey = key.diskKey;
        final ShaderDiskCache.Blob blob = ShaderDiskCache.getBlob(diskKey);
        if (blob == null) return 0;
        final int format;
        try {
            format = Integer.parseInt(blob.tag());
        } catch (NumberFormatException e) {
            ShaderDiskCache.remove(diskKey);
            return 0;
        }
        final int program = GLStateManager.glCreateProgram();
        final ByteBuffer data = MemoryUtilities.memAlloc(blob.data().length);
        try {
            data.put(blob.data()).flip();
            GLStateManager.glProgramBinary(program, format, data);
        } finally {
            MemoryUtilities.memFree(data);
        }
        if (GLStateManager.glGetProgrami(program, GL20.GL_LINK_STATUS) != GL11.GL_TRUE) {
            GLStateManager.glDeleteProgram(program);
            ShaderDiskCache.remove(diskKey);
            if (rejectWarned.compareAndSet(false, true)) LOGGER.error("Driver rejected a saved shader program!! Recompiling rejected programs from source");
            return 0;
        }
        keep(diskKey);
        return program;
    }

    public static void save(Key key, int program) {
        final int length = GLStateManager.glGetProgrami(program, GL41.GL_PROGRAM_BINARY_LENGTH);
        if (length <= 0) return;
        final ByteBuffer data = MemoryUtilities.memAlloc(length);
        final IntBuffer written = MemoryUtilities.memAllocInt(1);
        final IntBuffer format = MemoryUtilities.memAllocInt(1);
        try {
            GLStateManager.glGetProgramBinary(program, written, format, data);
            if (written.get(0) <= 0) return;
            data.limit(written.get(0));
            final byte[] binary = ShaderCacheIO.toHeap(data);
            final String formatTag = Integer.toString(format.get(0));
            final ShaderDiskCache.Key diskKey = key.diskKey;
            keep(diskKey);
            AngelicaWorkers.run(() -> ShaderDiskCache.putBlob(diskKey, formatTag, binary));
        } finally {
            MemoryUtilities.memFree(data);
            MemoryUtilities.memFree(written);
            MemoryUtilities.memFree(format);
        }
    }

    public static void beginFullCompile() {
        keysInUse = new HashSet<>();
    }

    public static void finishFullCompile() {
        final Set<String> used = keysInUse;
        keysInUse = null;
        if (used != null) AngelicaWorkers.run(() -> ShaderDiskCache.retainLayer(LAYER, used));
    }

    public static void deleteAll() {
        ShaderDiskCache.retainLayer(COMPILED_SETTINGS_LAYER, null);
        AngelicaWorkers.run(() -> ShaderDiskCache.retainLayer(LAYER, null));
    }

    public static boolean wasCompiledFor(String settings) {
        return ShaderDiskCache.getString(compiledSettingsKey(settings)) != null;
    }

    public static void rememberCompiledFor(String settings) {
        final ShaderDiskCache.Key key = compiledSettingsKey(settings);
        ShaderDiskCache.retainLayer(COMPILED_SETTINGS_LAYER, Collections.singleton(key.hex()));
        ShaderDiskCache.putString(key, "");
    }

    private static ShaderDiskCache.Key compiledSettingsKey(String settings) {
        return ShaderDiskCache.key(COMPILED_SETTINGS_LAYER).str(driverId()).b(ShaderManager.isEnabled()).str(settings);
    }

    private static void keep(ShaderDiskCache.Key diskKey) {
        final String hex = diskKey.hex();
        final Set<String> used = keysInUse;
        if (used != null) used.add(hex);
    }

    private static String driverId() {
        String id = driverId;
        if (id == null) {
            id = RENDER_BACKEND.getString(GL11.GL_VENDOR) + '|' + RENDER_BACKEND.getString(GL11.GL_RENDERER) + '|'
                + RENDER_BACKEND.getString(GL11.GL_VERSION) + '|' + RENDER_BACKEND.getString(GL20.GL_SHADING_LANGUAGE_VERSION) + '|'
                + RenderSystem.isGLES() + '|' + RENDER_BACKEND.getMinGLSLVersion();
            driverId = id;
        }
        return id;
    }

    public static final class Key {
        private final ShaderDiskCache.Key diskKey;

        private Key(ShaderDiskCache.Key diskKey) {
            this.diskKey = diskKey;
        }

        public Key stage(int shaderType, String source) {
            diskKey.str("stage").i(shaderType).str(source);
            return this;
        }

        public Key attribute(String name, int index) {
            diskKey.str("attribute").str(name).i(index);
            return this;
        }
    }
}
