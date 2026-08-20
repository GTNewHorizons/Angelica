package com.gtnewhorizons.angelica.tracy;

import com.gtnewhorizon.gtnhlib.reflect.Fields;
import com.gtnewhorizon.gtnhlib.reflect.Fields.LookupType;
import com.gtnewhorizons.angelica.config.SystemProperties;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.condition.EnabledIf;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@EnabledIf("nativePresent")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class TracyBindingSmokeTest {

    @TempDir
    static Path extractDir;

    private static TracyClientBackend backend;

    static boolean nativePresent() {
        for (String platform : new String[] {"windows-x64", "linux-x64", "macos-x64", "macos-arm64"}) {
            for (String lib : new String[] {"TracyClient.dll", "libTracyClient.so", "libTracyClient.dylib"}) {
                if (TracyBindingSmokeTest.class.getResource("/natives/tracy/" + TracyTags.TRACY_VERSION + "/" + platform + "/" + lib) != null) {
                    return true;
                }
            }
        }
        return false;
    }

    private static synchronized TracyClientBackend backend() throws Exception {
        if (backend == null) {
            Fields.ofClass(SystemProperties.class).getField(LookupType.DECLARED, "TRACY_DIR", String.class).setValue(null, extractDir.toAbsolutePath().toString());
            final TracyClientBackend created = new TracyClientBackend();
            final AtomicReference<Boolean> ok = new AtomicReference<>(Boolean.FALSE);
            final Thread init = new Thread(() -> ok.set(created.init()), "Tracy-Init-Test");
            init.setDaemon(true);
            init.start();
            init.join(15_000);
            assertTrue(ok.get(), "backend init failed");
            backend = created;
        }
        return backend;
    }

    @Test
    @Order(0)
    void nativeIsBuiltWithOnDemand() throws Exception {
        assertEquals(16, backend().zoneCtxSize(), "ctx size is 16 only with TRACY_ON_DEMAND");
    }

    @Test
    @Order(1)
    void zeroHandlesAreInertWhileDisconnected() throws Exception {
        final TracyClientBackend b = backend();
        assertEquals(0L, b.beginZone(b.internSrcLoc("disconnected", 0)), "zone must be inactive with no profiler attached");
        b.endZone(0L);
        b.zoneText(0L, "text");
        b.zoneValue(0L, 1L);
        assertEquals(0L, b.sectionEnter(1, "disconnected section"));
        b.sectionLeave(0L);
    }

    @Test
    @Order(2)
    void internAndSingleZone() throws Exception {
        final TracyClientBackend b = backend();
        final long srcLoc = b.internSrcLoc("smoke", 0);
        assertNotEquals(0, srcLoc);
        assertEquals(srcLoc, b.internSrcLoc("smoke", 0));

        final long ctx = b.beginZone(srcLoc);
        b.zoneText(ctx, "hello from java");
        b.zoneValue(ctx, 1234567890123L);
        b.endZone(ctx);
    }

    @Test
    @Order(3)
    void hammerZonesAcrossThreads() throws Exception {
        final TracyClientBackend b = backend();
        final int threads = 4;
        final int iterations = 10_000;
        final CountDownLatch start = new CountDownLatch(1);
        final List<Thread> workers = new ArrayList<>();
        final AtomicReference<Throwable> failure = new AtomicReference<>();
        for (int t = 0; t < threads; t++) {
            final int ti = t;
            final Thread worker = new Thread(() -> {
                try {
                    start.await();
                    b.setCurrentThreadName("smoke-worker-" + ti);
                    final long outer = b.internSrcLoc("outer-" + ti, 0);
                    final long inner = b.internSrcLoc("inner-" + ti, 0);
                    for (int i = 0; i < iterations; i++) {
                        final long outerCtx = b.beginZone(outer);
                        final long innerCtx = b.beginZone(inner);
                        if ((i & 1023) == 0) b.zoneText(innerCtx, "iter " + i);
                        b.endZone(innerCtx);
                        b.endZone(outerCtx);
                    }
                } catch (Throwable e) {
                    failure.set(e);
                }
            }, "smoke-worker-" + ti);
            worker.start();
            workers.add(worker);
        }
        start.countDown();
        for (Thread worker : workers) worker.join(60_000);
        if (failure.get() != null) throw new AssertionError(failure.get());
    }

    @Test
    @Order(4)
    void plotsMessagesAndFrameMarks() throws Exception {
        final TracyClientBackend b = backend();
        final long plotName = b.internPlotName("smokePlot", 0);
        assertNotEquals(0, plotName);
        final long frameName = b.internFrameName("smoke tick");
        assertNotEquals(0, frameName);
        for (int i = 0; i < 100; i++) {
            b.plotInt(plotName, i);
            b.plot(plotName, i * 0.5);
            b.frameMark();
            b.frameMark(frameName);
        }
        b.message("smoke test message");
        b.isConnected();
    }

    @Test
    @Order(5)
    void dynamicSrcLocOverflowPath() throws Exception {
        final TracyClientBackend b = backend();
        final long dynamic = b.dynamicSrcLoc();
        assertNotEquals(0, dynamic);
        final long ctx = b.beginZone(dynamic);
        b.zoneText(ctx, "overflow-name");
        b.endZone(ctx);
    }

    @Test
    @Order(6)
    void gpuEmitStructMarshalling() throws Exception {
        final TracyClientBackend b = backend();
        final long srcLoc = b.internSrcLoc("gpu-smoke", 0);
        b.prepGpuCifs();
        b.emitGpuNewContext(0);
        b.emitGpuContextName("SelfTest");
        b.emitGpuZoneBegin(srcLoc, 0);
        b.emitGpuZoneEnd(1);
        b.emitGpuTime(1000, 0);
        b.emitGpuTime(2000, 1);
        b.emitGpuTimeSync(3000);
    }

    @Test
    @Order(7)
    void shutdownFlushes() throws Exception {
        backend().shutdown();
    }
}
