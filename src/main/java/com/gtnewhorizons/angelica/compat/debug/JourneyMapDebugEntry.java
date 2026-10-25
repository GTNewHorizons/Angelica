package com.gtnewhorizons.angelica.compat.debug;

import com.gtnewhorizons.angelica.api.debug.DebugContext;
import com.gtnewhorizons.angelica.api.debug.DebugEntryHandle;
import com.gtnewhorizons.angelica.api.debug.DebugFactLines;
import com.gtnewhorizons.angelica.api.debug.DebugGroupHandle;
import com.gtnewhorizons.angelica.api.debug.DebugScreenApi;
import com.gtnewhorizons.angelica.api.debug.DebugScreenDisplayer;
import com.gtnewhorizons.angelica.api.debug.DebugScreenEntry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.I18n;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public final class JourneyMapDebugEntry implements DebugScreenEntry {

    static final String GROUP_ID = "journeymap:journeymap";
    static final String ENTRY_ID = "journeymap:debug";

    private static final String MAPPING_DISABLED_KEY = "jm.common.enable_mapping_false_text";
    private static final long REPORT_INTERVAL_NS = TimeUnit.SECONDS.toNanos(3);

    private static DebugEntryHandle handle;

    private final DebugGroupHandle group;
    private final DebugFactLines facts = new DebugFactLines();
    private MethodHandle stats;
    private MethodHandle mappingEnabled;
    private MethodHandle timerReport;
    private List<String> report = Collections.emptyList();
    private long reportAt;
    private boolean reported;

    private JourneyMapDebugEntry(DebugGroupHandle group) {
        this.group = group;
    }

    static void register() {
        handle = DebugCompat.register(GROUP_ID, ENTRY_ID, "JourneyMap", 0x55FFFF, entry -> new JourneyMapDebugEntry(DebugScreenApi.group(GROUP_ID)));
    }

    public static boolean active() {
        return DebugCompat.active(handle);
    }

    @Override
    public void display(DebugScreenDisplayer displayer, DebugContext context) {
        if (context.isSampleDue()) sample(context.nanoTime());
        facts.emit(displayer, group);
    }

    @SuppressWarnings("unchecked")
    private void sample(long now) {
        try {
            if (stats == null) resolve();
            final boolean mapping = ((AtomicBoolean) mappingEnabled.invokeExact()).get();
            final boolean profiler = Minecraft.getMinecraft().gameSettings.showDebugProfilerChart;
            if (profiler && (!reported || now - reportAt > REPORT_INTERVAL_NS)) {
                report = (List<String>) timerReport.invokeExact("", "");
                reportAt = now;
                reported = true;
            }
            writeFacts(facts, mapping ? (String[]) stats.invokeExact() : null, mapping ? null : I18n.format(MAPPING_DISABLED_KEY), profiler ? report : null);
        } catch (RuntimeException | Error e) {
            throw e;
        } catch (Throwable t) {
            throw new IllegalStateException("JourneyMap debug stats are unavailable", t);
        }
    }

    private void resolve() throws ReflectiveOperationException {
        final MethodHandles.Lookup lookup = MethodHandles.publicLookup();
        final Class<?> properties = Class.forName("journeymap.client.properties.CoreProperties");
        final MethodHandle coreProperties = lookup.findStatic(Class.forName("journeymap.client.JourneymapClient"), "getCoreProperties", MethodType.methodType(properties));
        mappingEnabled = MethodHandles.filterReturnValue(coreProperties, lookup.findGetter(properties, "mappingEnabled", AtomicBoolean.class));
        timerReport = lookup.findStatic(Class.forName("journeymap.client.log.StatTimer"), "getReportByTotalTime", MethodType.methodType(List.class, String.class, String.class));
        stats = lookup.findStatic(Class.forName("journeymap.client.task.multi.MapPlayerTask"), "getDebugStats", MethodType.methodType(String[].class));
    }

    static void writeFacts(DebugFactLines facts, String[] stats, String mappingDisabled, List<String> report) {
        facts.begin();
        if (mappingDisabled != null) facts.line(mappingDisabled);
        if (stats != null) {
            for (int i = 0; i < stats.length; i++) {
                final String line = stats[i];
                if (line != null) facts.split(line);
            }
        }
        if (report != null) {
            for (int i = 0, n = report.size(); i < n; i++) {
                final String line = report.get(i);
                if (line != null) facts.line(line);
            }
        }
        facts.end();
    }
}
