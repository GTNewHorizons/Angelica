package com.gtnewhorizons.angelica.glsm.profiling;

import com.gtnewhorizons.angelica.config.SystemProperties;
import com.gtnewhorizons.angelica.glsm.CaptureGate;
import com.gtnewhorizons.angelica.glsm.GLStateManager;
import com.sun.management.ThreadMXBean;
import lombok.Setter;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.util.Iterator;
import java.util.List;
import java.util.ServiceConfigurationError;
import java.util.ServiceLoader;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

@SuppressWarnings("ForLoopReplaceableByForEach")
public final class Tracy {
    private static final Logger LOGGER = LogManager.getLogger("Tracy");

    public static final boolean ENABLED;
    public static final boolean FINE_ZONES;
    private static final TracyBackend BACKEND;
    private static volatile boolean SHUTTING_DOWN;

    private static final ConcurrentHashMap<String, Long> CLIENT_SRC_LOCS = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<String, Long> SERVER_SRC_LOCS = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<String, Long> PLAIN_SRC_LOCS = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<String, Long> PLOT_NAMES = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<String, Long> FRAME_NAMES = new ConcurrentHashMap<>();
    private static final Set<String> WARNED_LEAKS = ConcurrentHashMap.newKeySet();

    public static final int COLOR_CLIENT = 0x3A6EA5; // blue
    public static final int COLOR_SERVER = 0xB5651D; // brown
    public static final int COLOR_WORKER = 0x3F7F3F; // green
    public static final int COLOR_SWAP = 0xAA3333; // red
    public static final int COLOR_TERRAIN = 0x2E8B8B; // teal
    public static final int COLOR_IRIS = 0x9E862E; // gold
    public static final int COLOR_FFP = 0x7A4E9E; // purple

    private static final ThreadLocal<ZoneStack> STACK = new ThreadLocal<>() {
        @Override
        protected ZoneStack initialValue() {
            BACKEND.setCurrentThreadName(Thread.currentThread().getName());
            return new ZoneStack();
        }
    };

    static {
        TracyBackend backend = null;
        if (SystemProperties.TRACY) {
            backend = loadAndInit();
        }
        BACKEND = backend;
        ENABLED = backend != null;
        FINE_ZONES = ENABLED && SystemProperties.TRACY_FINE_ZONES;
        if (ENABLED) {
            Runtime.getRuntime().addShutdownHook(new Thread(Tracy::shutdown, "Tracy-Shutdown"));
            LOGGER.info("Tracy profiling enabled");
        }
    }

    private Tracy() {}

    private static void shutdown() {
        SHUTTING_DOWN = true;
        final Thread worker = new Thread(BACKEND::shutdown, "Tracy-Shutdown-Worker");
        worker.setDaemon(true);
        worker.start();
        try {
            worker.join(2000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static TracyBackend loadAndInit() {
        TracyBackend found = null;
        try {
            final Iterator<TracyBackend> it = ServiceLoader.load(TracyBackend.class, TracyBackend.class.getClassLoader()).iterator();
            while (found == null && it.hasNext()) {
                found = it.next();
            }
        } catch (ServiceConfigurationError | LinkageError e) {
            LOGGER.warn("Tracy requested (-Dangelica.tracy=true) but backend unavailable: {}", e.getMessage());
            return null;
        }
        if (found == null) {
            LOGGER.warn("Tracy requested (-Dangelica.tracy=true) but no TracyBackend service present");
            return null;
        }
        final TracyBackend backend = found;
        final CountDownLatch done = new CountDownLatch(1);
        final AtomicReference<Boolean> result = new AtomicReference<>();
        final Thread init = new Thread(new InitRunner(backend, done, result), "Tracy-Init");
        init.setDaemon(true);
        init.start();
        try {
            if (!done.await(15, TimeUnit.SECONDS) && result.compareAndSet(null, Boolean.FALSE)) {
                LOGGER.warn("Tracy requested (-Dangelica.tracy=true) but init timed out; profiling disabled");
                return null;
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            if (!result.compareAndSet(null, Boolean.FALSE) && Boolean.TRUE.equals(result.get())) {
                backend.shutdown();
            }
            return null;
        }
        if (!Boolean.TRUE.equals(result.get())) {
            LOGGER.warn("Tracy requested (-Dangelica.tracy=true) but native init failed; profiling disabled");
            return null;
        }
        return backend;
    }

    private record InitRunner(TracyBackend backend, CountDownLatch done, AtomicReference<Boolean> result) implements Runnable {
        @Override
        public void run() {
            final boolean ok = backend.init();
            if (!result.compareAndSet(null, ok) && ok) {
                backend.shutdown();
            }
            done.countDown();
        }
    }

    public static void beginSection(String name) {
        if (!ENABLED) return;
        if ("root".equals(name)) {
            unwindToRoot(STACK.get());
        }
        if (!CaptureGate.markersThisFrame) {
            skip(name);
            return;
        }
        final boolean client = Thread.currentThread() == GLStateManager.getMainThread();
        final ConcurrentHashMap<String, Long> map = client ? CLIENT_SRC_LOCS : SERVER_SRC_LOCS;
        Long srcLoc = map.get(name);
        if (srcLoc == null) {
            srcLoc = map.computeIfAbsent(name, n -> BACKEND.internSrcLoc((client ? "cl:" : "sv:") + n, client ? classifySection(n) : COLOR_SERVER));
        }
        begin(srcLoc, name);
    }

    private static int classifySection(String name) {
        if (name.startsWith("iris_")) return COLOR_IRIS;
        if (name.startsWith("draw_chunk_layer")) return COLOR_TERRAIN;
        return COLOR_CLIENT;
    }

    private static void unwindToRoot(ZoneStack stack) {
        while (stack.depth() > 0 || stack.hasSkipped()) {
            final long ctx = stack.pop();
            if (ctx == ZoneStack.EMPTY) continue;
            if (stack.poppedGpu()) {
                BACKEND.gpuEndZone();
            }
            if (ctx != 0L) {
                BACKEND.endZone(ctx);
            }
            final String name = stack.poppedName();
            if (name != null && WARNED_LEAKS.add(name)) {
                LOGGER.warn("Tracy: unbalanced profiler section '{}' left open (leaked by its caller); closed at next root", name);
                BACKEND.message("unbalanced profiler section '" + name + "' closed at root");
            }
        }
    }

    public static void beginZone(String name) {
        beginZone(name, COLOR_WORKER);
    }

    public static void beginZone(String name, int color) {
        if (!ENABLED || SHUTTING_DOWN) return;
        if (!CaptureGate.markersThisFrame) {
            skip(name);
            return;
        }
        Long srcLoc = PLAIN_SRC_LOCS.get(name);
        if (srcLoc == null) {
            srcLoc = PLAIN_SRC_LOCS.computeIfAbsent(name, n -> BACKEND.internSrcLoc(n, color));
        }
        begin(srcLoc, name);
    }

    public static final class ZoneId {
        private final long srcLoc;
        private final String name;

        private ZoneId(long srcLoc, String name) {
            this.srcLoc = srcLoc;
            this.name = name;
        }
    }

    public static ZoneId zoneId(String name) {
        return zoneId(name, COLOR_WORKER);
    }

    public static ZoneId zoneId(String name, int color) {
        if (!ENABLED) return new ZoneId(0L, name);
        Long srcLoc = PLAIN_SRC_LOCS.get(name);
        if (srcLoc == null) {
            srcLoc = PLAIN_SRC_LOCS.computeIfAbsent(name, n -> BACKEND.internSrcLoc(n, color));
        }
        return new ZoneId(srcLoc, name);
    }

    public static void beginZone(ZoneId zone) {
        if (!ENABLED || SHUTTING_DOWN) return;
        begin(zone.srcLoc, zone.name);
    }

    private static void skip(String name) {
        STACK.get().push(0L, false, name);
    }

    private static void begin(long srcLoc, String name) {
        if (SHUTTING_DOWN) return;
        final ZoneStack stack = STACK.get();
        if (srcLoc == 0 || !CaptureGate.markersThisFrame || stack.atCap()) {
            stack.push(0L, false, name);
            return;
        }
        final long ctx = BACKEND.beginZone(srcLoc);
        final boolean gpu = gpuReady && gpuZonesEnabled && Thread.currentThread() == GLStateManager.getMainThread() && BACKEND.gpuBeginZone(srcLoc);
        stack.push(ctx, gpu, name);
        if (srcLoc == BACKEND.dynamicSrcLoc()) {
            BACKEND.zoneText(ctx, name);
        }
    }

    public static void endZone() {
        if (!ENABLED || SHUTTING_DOWN) return;
        final ZoneStack stack = STACK.get();
        final long ctx = stack.pop();
        if (ctx == ZoneStack.EMPTY) return;
        if (stack.poppedGpu()) {
            BACKEND.gpuEndZone();
        }
        if (ctx != 0L) {
            BACKEND.endZone(ctx);
        }
    }

    public static void zoneText(String text) {
        if (!ENABLED || SHUTTING_DOWN) return;
        final long ctx = STACK.get().peek();
        if (ctx != ZoneStack.EMPTY && ctx != 0L) {
            BACKEND.zoneText(ctx, text);
        }
    }

    public static void zoneValue(long value) {
        if (!ENABLED || SHUTTING_DOWN) return;
        final long ctx = STACK.get().peek();
        if (ctx != ZoneStack.EMPTY && ctx != 0L) {
            BACKEND.zoneValue(ctx, value);
        }
    }

    private static boolean threadNameRefreshed;

    public static void frameMark() {
        if (!ENABLED || SHUTTING_DOWN) return;
        if (!threadNameRefreshed) {
            threadNameRefreshed = true;
            BACKEND.setCurrentThreadName(Thread.currentThread().getName());
        }
        BACKEND.frameMark();
    }

    public static void frameMark(String name) {
        if (!ENABLED || SHUTTING_DOWN) return;
        Long ptr = FRAME_NAMES.get(name);
        if (ptr == null) {
            ptr = FRAME_NAMES.computeIfAbsent(name, BACKEND::internFrameName);
        }
        if (ptr != 0) BACKEND.frameMark(ptr);
    }

    public static void plotInt(String name, long value) {
        plotInt(name, value, TracyBackend.PLOT_FORMAT_NUMBER);
    }

    public static void plotInt(String name, long value, int format) {
        if (!ENABLED || SHUTTING_DOWN || !CaptureGate.markersThisFrame) return;
        final long ptr = plotName(name, format);
        if (ptr != 0) BACKEND.plotInt(ptr, value);
    }

    public static void plot(String name, double value) {
        if (!ENABLED || SHUTTING_DOWN || !CaptureGate.markersThisFrame) return;
        final long ptr = plotName(name, TracyBackend.PLOT_FORMAT_NUMBER);
        if (ptr != 0) BACKEND.plot(ptr, value);
    }

    private static long plotName(String name, int format) {
        Long ptr = PLOT_NAMES.get(name);
        if (ptr == null) {
            ptr = PLOT_NAMES.computeIfAbsent(name, n -> BACKEND.internPlotName(n, format));
        }
        return ptr;
    }

    public static long plotHandle(String name, int format) {
        if (!ENABLED || SHUTTING_DOWN) return 0;
        return plotName(name, format);
    }

    public static long plotHandle(String name) {
        return plotHandle(name, TracyBackend.PLOT_FORMAT_NUMBER);
    }

    public static void plotInt(long handle, long value) {
        if (handle == 0 || SHUTTING_DOWN || !CaptureGate.markersThisFrame) return;
        BACKEND.plotInt(handle, value);
    }

    public static void plot(long handle, double value) {
        if (handle == 0 || SHUTTING_DOWN || !CaptureGate.markersThisFrame) return;
        BACKEND.plot(handle, value);
    }

    public static void message(String text) {
        if (!ENABLED || SHUTTING_DOWN || !CaptureGate.markersThisFrame) return;
        BACKEND.message(text);
    }

    public static boolean isConnected() {
        return ENABLED && BACKEND.isConnected();
    }

    private static boolean gpuInitAttempted;
    private static boolean gpuReady;
    @Setter private static boolean gpuZonesEnabled = true;

    public static void gpuCollect() {
        if (!ENABLED) return;
        if (!gpuInitAttempted) {
            gpuInitAttempted = true;
            gpuReady = BACKEND.gpuInit();
        }
        if (gpuReady) BACKEND.gpuCollect();
    }

    private static final class AllocBean {
        static final ThreadMXBean BEAN;
        static {
            ThreadMXBean bean = null;
            try {
                if (ManagementFactory.getThreadMXBean() instanceof ThreadMXBean sun && sun.isThreadAllocatedMemorySupported()) {
                    bean = sun;
                }
            } catch (Throwable ignored) {}
            BEAN = bean;
        }
    }

    private static final ThreadLocal<long[]> PREV_ALLOC = ThreadLocal.withInitial(() -> new long[1]);

    public static void plotAllocRate(long plotHandle) {
        if (!ENABLED || SHUTTING_DOWN || AllocBean.BEAN == null) return;
        if (!CaptureGate.markersThisFrame) {
            PREV_ALLOC.get()[0] = 0;
            return;
        }
        final long now = AllocBean.BEAN.getThreadAllocatedBytes(Thread.currentThread().threadId());
        if (now < 0) return;
        final long[] prev = PREV_ALLOC.get();
        if (prev[0] != 0) {
            plotInt(plotHandle, now - prev[0]);
        }
        prev[0] = now;
    }

    private static List<GarbageCollectorMXBean> gcBeans;
    private static boolean gcBaselineValid;
    private static long prevGcCount;
    private static long prevGcTimeMs;
    private static long gcCountHandle;
    private static long gcTimeHandle;

    public static void plotGcStats() {
        if (!ENABLED || SHUTTING_DOWN) return;
        if (!CaptureGate.markersThisFrame) {
            gcBaselineValid = false;
            return;
        }
        if (gcBeans == null) {
            gcBeans = ManagementFactory.getGarbageCollectorMXBeans();
            gcCountHandle = plotHandle("gcCount");
            gcTimeHandle = plotHandle("gcTimeMs");
        }
        long count = 0;
        long timeMs = 0;
        for (int i = 0; i < gcBeans.size(); i++) {
            final GarbageCollectorMXBean bean = gcBeans.get(i);
            final long c = bean.getCollectionCount();
            final long t = bean.getCollectionTime();
            if (c > 0) count += c;
            if (t > 0) timeMs += t;
        }
        if (gcBaselineValid) {
            plotInt(gcCountHandle, count - prevGcCount);
            plotInt(gcTimeHandle, timeMs - prevGcTimeMs);
            if (count > prevGcCount) {
                message("GC: +" + (count - prevGcCount) + " collections, +" + (timeMs - prevGcTimeMs) + " ms");
            }
        }
        gcBaselineValid = true;
        prevGcCount = count;
        prevGcTimeMs = timeMs;
    }
}
