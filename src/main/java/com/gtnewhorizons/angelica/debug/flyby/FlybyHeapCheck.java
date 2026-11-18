package com.gtnewhorizons.angelica.debug.flyby;

import com.gtnewhorizons.angelica.config.SystemProperties;
import com.gtnewhorizons.angelica.debug.profiling.AsprofRecorder;
import com.gtnewhorizons.angelica.rendering.celeritas.AngelicaRenderSectionManager;
import com.gtnewhorizons.angelica.rendering.celeritas.DeferredMeshScheduler;
import com.gtnewhorizons.angelica.rendering.celeritas.world.WorldSlice;
import com.gtnewhorizons.angelica.rendering.celeritas.world.cloned.ClonedChunkSection;
import com.sun.management.HotSpotDiagnosticMXBean;
import it.unimi.dsi.fastutil.objects.Object2LongMap;
import it.unimi.dsi.fastutil.objects.Object2LongOpenHashMap;
import net.minecraft.client.entity.EntityClientPlayerMP;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.world.chunk.Chunk;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.embeddedt.embeddium.impl.render.chunk.RenderSection;
import org.embeddedt.embeddium.impl.render.chunk.compile.ChunkBuildBuffers;

import javax.management.ObjectName;
import java.io.File;
import java.lang.management.ManagementFactory;
import java.util.ArrayList;
import java.util.List;

final class FlybyHeapCheck {
    private static final Logger LOGGER = LogManager.getLogger("Angelica/Flyby");
    private static final int TOP_GROWERS = 10;
    private static final int MIN_REPORTS_FOR_GROWTH = 3;

    private final List<Object2LongOpenHashMap<String>> history = new ArrayList<>();
    private final Object2LongOpenHashMap<String> maxRetained = new Object2LongOpenHashMap<>();
    private String skipReason;

    private static Class<?>[] mustBeZero() {
        return new Class<?>[] {AngelicaRenderSectionManager.class, WorldSlice.class, ClonedChunkSection.class, DeferredMeshScheduler.class, RenderSection.class, ChunkBuildBuffers.class, WorldClient.class};
    }

    private static Class<?>[] watched() {
        return new Class<?>[] {Chunk.class, EntityClientPlayerMP.class};
    }

    void report(String label) {
        final Object2LongOpenHashMap<String> counts;
        try {
            // Objects with a pending finalize() (AE2 part monitors) count as live and keep their world until finalized.
            System.gc();
            System.runFinalization();
            counts = liveHistogram();
        } catch (Exception e) {
            LOGGER.warn("Flyby heap check: histogram failed", e);
            skip("histogram failed: " + e);
            return;
        }
        final StringBuilder sb = new StringBuilder("Flyby heap ").append(label).append(':');
        for (Class<?> type : mustBeZero()) {
            final long count = counts.getLong(type.getName());
            if (count > maxRetained.getLong(type.getName())) maxRetained.put(type.getName(), count);
            sb.append(' ').append(type.getSimpleName()).append('=').append(count);
        }
        sb.append(" | watched");
        for (Class<?> type : watched()) {
            sb.append(' ').append(type.getSimpleName()).append('=').append(counts.getLong(type.getName()));
        }
        LOGGER.info(sb);
        history.add(counts);
    }

    void skip(String reason) {
        if (skipReason == null) skipReason = reason;
    }

    void finish() {
        if (skipReason != null) {
            LOGGER.warn("Flyby heap check: SKIPPED ({})", skipReason);
            return;
        }
        if (history.size() >= MIN_REPORTS_FOR_GROWTH) logGrewEveryVisit();
        final StringBuilder retained = new StringBuilder();
        for (Class<?> type : mustBeZero()) {
            final long max = maxRetained.getLong(type.getName());
            if (max > 0) retained.append(' ').append(type.getSimpleName()).append('=').append(max);
        }
        if (retained.length() == 0) {
            LOGGER.info("Flyby heap check: PASS");
            return;
        }
        LOGGER.warn("Flyby heap check: FAIL{}", retained);
        dump();
    }

    private void logGrewEveryVisit() {
        final Object2LongOpenHashMap<String> first = history.get(0);
        final Object2LongOpenHashMap<String> last = history.get(history.size() - 1);
        final List<Object2LongMap.Entry<String>> grew = new ArrayList<>();
        for (Object2LongMap.Entry<String> e : last.object2LongEntrySet()) {
            if (roseAtEveryReport(e.getKey())) grew.add(e);
        }
        grew.sort((a, b) -> Long.compare(b.getLongValue() - first.getLong(b.getKey()), a.getLongValue() - first.getLong(a.getKey())));
        for (int i = 0; i < Math.min(TOP_GROWERS, grew.size()); i++) {
            final Object2LongMap.Entry<String> e = grew.get(i);
            LOGGER.info("Flyby heap: grew at every visit: {} {} -> {}", e.getKey(), first.getLong(e.getKey()), e.getLongValue());
        }
    }

    private boolean roseAtEveryReport(String className) {
        for (int i = 1; i < history.size(); i++) {
            if (history.get(i).getLong(className) <= history.get(i - 1).getLong(className)) return false;
        }
        return true;
    }

    private static void dump() {
        final File file = new File(SystemProperties.PROFILE_DIR, AsprofRecorder.fileName("leak", "hprof", System.currentTimeMillis())).getAbsoluteFile();
        try {
            file.getParentFile().mkdirs();
            ManagementFactory.getPlatformMXBean(HotSpotDiagnosticMXBean.class).dumpHeap(file.getPath(), true);
            LOGGER.info("Flyby heap: dumped {}", file.getPath());
        } catch (Exception e) {
            LOGGER.warn("Flyby heap dump to {} failed", file.getPath(), e);
        }
    }

    private static Object2LongOpenHashMap<String> liveHistogram() throws Exception {
        final String histogram = (String) ManagementFactory.getPlatformMBeanServer().invoke(new ObjectName("com.sun.management:type=DiagnosticCommand"), "gcClassHistogram", new Object[] {null}, new String[] {String[].class.getName()});
        final Object2LongOpenHashMap<String> counts = new Object2LongOpenHashMap<>();
        for (String line : histogram.split("\n")) {
            final String[] cols = line.trim().split("\\s+");
            if (cols.length >= 4 && cols[0].endsWith(":")) counts.put(cols[3], Long.parseLong(cols[1]));
        }
        return counts;
    }
}
