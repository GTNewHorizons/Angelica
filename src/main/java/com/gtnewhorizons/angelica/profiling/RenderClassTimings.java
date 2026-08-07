package com.gtnewhorizons.angelica.profiling;

import com.gtnewhorizons.angelica.glsm.profiling.Tracy;
import it.unimi.dsi.fastutil.objects.Reference2IntOpenHashMap;
import it.unimi.dsi.fastutil.objects.Reference2LongMap;
import it.unimi.dsi.fastutil.objects.Reference2LongMaps;
import it.unimi.dsi.fastutil.objects.Reference2LongOpenHashMap;
import it.unimi.dsi.fastutil.objects.Reference2ObjectOpenHashMap;
import net.minecraft.client.Minecraft;

public final class RenderClassTimings {
    public static final RenderClassTimings ENTITY = new RenderClassTimings("entity.cls.", "Entity");
    public static final RenderClassTimings SHADOW_ENTITY = new RenderClassTimings("shadow.entity.cls.", "ShadowEntity");
    public static final RenderClassTimings TESR = new RenderClassTimings("tesr.cls.", "TESR");

    private static final long PLOT_THRESHOLD_NS = 20_000L;

    private final String plotPrefix;
    private final String label;
    private final Reference2LongOpenHashMap<Class<?>> nanos = new Reference2LongOpenHashMap<>();
    private final Reference2IntOpenHashMap<Class<?>> counts = new Reference2IntOpenHashMap<>();
    private final Reference2ObjectOpenHashMap<Class<?>, String> plotNames = new Reference2ObjectOpenHashMap<>();
    private String debugLine = "";

    private RenderClassTimings(String plotPrefix, String label) {
        this.plotPrefix = plotPrefix;
        this.label = label;
    }

    public void add(Class<?> cls, long ns) {
        nanos.addTo(cls, ns);
        counts.addTo(cls, 1);
    }

    public void flushFrame(boolean plot) {
        if (nanos.isEmpty()) {
            debugLine = "";
            return;
        }
        final boolean debugScreen = Minecraft.getMinecraft().gameSettings.showDebugInfo;
        if (!plot && !debugScreen) {
            debugLine = "";
            nanos.clear();
            counts.clear();
            return;
        }
        Class<?> a = null, b = null, c = null;
        long an = 0, bn = 0, cn = 0;
        final var it = Reference2LongMaps.fastIterator(nanos);
        while (it.hasNext()) {
            final Reference2LongMap.Entry<Class<?>> entry = it.next();
            final long v = entry.getLongValue();
            if (plot && v >= PLOT_THRESHOLD_NS) {
                Tracy.plotInt(plotName(entry.getKey()), v);
            }
            if (v > an) {
                c = b; cn = bn;
                b = a; bn = an;
                a = entry.getKey(); an = v;
            } else if (v > bn) {
                c = b; cn = bn;
                b = entry.getKey(); bn = v;
            } else if (v > cn) {
                c = entry.getKey(); cn = v;
            }
        }
        if (a == null || !debugScreen) {
            debugLine = "";
        } else {
            final StringBuilder sb = new StringBuilder(label).append(": ");
            appendEntry(sb, a, an);
            if (b != null) appendEntry(sb.append(", "), b, bn);
            if (c != null) appendEntry(sb.append(", "), c, cn);
            debugLine = sb.toString();
        }
        nanos.clear();
        counts.clear();
    }

    private String plotName(Class<?> cls) {
        String name = plotNames.get(cls);
        if (name == null) {
            name = plotPrefix + cls.getSimpleName();
            plotNames.put(cls, name);
        }
        return name;
    }

    private void appendEntry(StringBuilder sb, Class<?> cls, long ns) {
        sb.append(cls.getSimpleName()).append(' ').append(ns / 1000).append("us x").append(counts.getInt(cls));
    }

    public String debugLine() {
        return debugLine;
    }
}
