package com.gtnewhorizons.angelica.glsm.shader;

import com.gtnewhorizon.gtnhlib.bytebuf.MemoryUtilities;
import com.gtnewhorizons.angelica.glsm.GLStateManager;
import com.gtnewhorizons.angelica.glsm.RenderSystem;
import com.gtnewhorizons.angelica.glsm.ffp.ShaderManager;
import com.gtnewhorizons.angelica.glsm.threading.AngelicaWorkers;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL41;

import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

import static com.gtnewhorizons.angelica.glsm.backend.BackendManager.RENDER_BACKEND;

/**
 * Saves linked GL programs to disk and loads them back with glProgramBinary, skipping compile and link.
 */
public final class ProgramBinaryCache {
    private static final Logger LOGGER = LogManager.getLogger("ProgramBinaryCache");
    static final String LAYER = "gl-program";
    private static final int PACKS_KEPT = 3;

    private static volatile boolean enabled;
    private static volatile String packLayer;
    private static String driverId;
    private static String fullCompileLayer;
    private static Set<String> keysInUse;
    private static List<CompletableFuture<Boolean>> fullCompileWrites;
    private static Object fullCompileGeneration;
    private static final AtomicBoolean rejectWarned = new AtomicBoolean();
    private static final Object WRITE_LOCK = new Object();
    private static final Map<String, Object> compileGenerations = new HashMap<>();
    private static int deleteCount;

    private ProgramBinaryCache() {}

    public static void setEnabled(boolean value) {
        enabled = value;
    }

    public static boolean isEnabled() {
        return enabled && packLayer != null && RenderSystem.supportsProgramBinary() && ShaderDiskCache.isEnabled();
    }

    public static void usePack(String packName) {
        if (packName == null) {
            packLayer = null;
            return;
        }
        final String layer = LAYER + '/' + folderName(packName);
        packLayer = layer;
        if (!enabled) return;
        ShaderDiskCache.touchLayer(layer);
        AngelicaWorkers.run(() -> ShaderDiskCache.keepNewestSubLayers(LAYER, PACKS_KEPT));
    }

    public static Key key() {
        return new Key(ShaderDiskCache.key(packLayer).str(driverId()).b(ShaderManager.isEnabled()));
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
        if (!saveBinary(key, program) && fullCompileWrites != null) {
            fullCompileWrites.add(CompletableFuture.completedFuture(false));
        }
    }

    private static boolean saveBinary(Key key, int program) {
        final int length = GLStateManager.glGetProgrami(program, GL41.GL_PROGRAM_BINARY_LENGTH);
        if (length <= 0) return false;
        final ByteBuffer data = MemoryUtilities.memAlloc(length);
        final IntBuffer written = MemoryUtilities.memAllocInt(1);
        final IntBuffer format = MemoryUtilities.memAllocInt(1);
        try {
            GLStateManager.glGetProgramBinary(program, written, format, data);
            if (written.get(0) <= 0) return false;
            data.limit(written.get(0));
            final byte[] binary = ShaderCacheIO.toHeap(data);
            final String formatTag = Integer.toString(format.get(0));
            final ShaderDiskCache.Key diskKey = key.diskKey;
            keep(diskKey);
            final int deletesBefore;
            synchronized (WRITE_LOCK) {
                deletesBefore = deleteCount;
            }
            final CompletableFuture<Boolean> write = AngelicaWorkers.submit(() -> {
                synchronized (WRITE_LOCK) {
                    return deletesBefore == deleteCount && ShaderDiskCache.putBlob(diskKey, formatTag, binary);
                }
            });
            final List<CompletableFuture<Boolean>> writes = fullCompileWrites;
            if (writes != null) writes.add(write);
            return true;
        } finally {
            MemoryUtilities.memFree(data);
            MemoryUtilities.memFree(written);
            MemoryUtilities.memFree(format);
        }
    }

    public static boolean isFullCompileRunning() {
        return keysInUse != null;
    }

    public static boolean saveRetained(Key key, int program) {
        if (ShaderDiskCache.getBlob(key.diskKey) != null) {
            keep(key.diskKey);
            return true;
        }
        return saveBinary(key, program);
    }

    public static void beginFullCompile() {
        fullCompileLayer = packLayer;
        keysInUse = new HashSet<>();
        fullCompileWrites = new ArrayList<>();
        synchronized (WRITE_LOCK) {
            fullCompileGeneration = new Object();
            if (fullCompileLayer != null) compileGenerations.put(fullCompileLayer, fullCompileGeneration);
        }
    }
    public static void finishFullCompile(@Nullable String completedSettings) {
        final String layer = fullCompileLayer;
        final Set<String> used = keysInUse;
        final List<CompletableFuture<Boolean>> writes = fullCompileWrites;
        final Object generation = fullCompileGeneration;
        fullCompileLayer = null;
        keysInUse = null;
        fullCompileWrites = null;
        fullCompileGeneration = null;
        if (layer == null || used == null || writes == null) return;

        final ShaderDiskCache.Key marker = completedSettings != null ? compiledSettingsKey(layer, completedSettings) : null;
        final String markerHex = marker != null ? marker.hex() : null;
        CompletableFuture.allOf(writes.toArray(new CompletableFuture<?>[0])).whenComplete((ignored, failure) -> {
            synchronized (WRITE_LOCK) {
                if (compileGenerations.get(layer) != generation) return;
                if (marker == null || failure != null) return;
                for (CompletableFuture<Boolean> write : writes) {
                    if (!write.join()) return;
                }
                ShaderDiskCache.retainLayer(layer, used);
                ShaderDiskCache.retainLayer(layer + "/settings", Collections.singleton(markerHex));
                ShaderDiskCache.putString(marker, "");
            }
        });
    }

    public static void deleteAll() {
        synchronized (WRITE_LOCK) {
            deleteCount++;
            compileGenerations.clear();
            ShaderDiskCache.deleteLayer(LAYER);
        }
    }

    public static boolean wasCompiledFor(String settings) {
        final String layer = packLayer;
        return layer != null && ShaderDiskCache.getString(compiledSettingsKey(layer, settings)) != null;
    }

    private static ShaderDiskCache.Key compiledSettingsKey(String layer, String settings) {
        return ShaderDiskCache.key(layer + "/settings").str(driverId()).b(ShaderManager.isEnabled()).str(settings);
    }

    private static String folderName(String packName) {
        final StringBuilder name = new StringBuilder(Math.min(packName.length(), 64));
        for (int i = 0; i < packName.length() && name.length() < 64; i++) {
            final char c = packName.charAt(i);
            name.append(c < 128 && (Character.isLetterOrDigit(c) || c == '.' || c == '-' || c == '_') ? c : '_');
        }
        return name.toString();
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
