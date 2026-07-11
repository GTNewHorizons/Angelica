package com.gtnewhorizons.angelica.profiling;

import com.gtnewhorizons.angelica.glsm.profiling.Tracy;
import it.unimi.dsi.fastutil.objects.Reference2IntMap;
import it.unimi.dsi.fastutil.objects.Reference2IntMaps;
import it.unimi.dsi.fastutil.objects.Reference2IntOpenHashMap;
import it.unimi.dsi.fastutil.objects.Reference2ObjectOpenHashMap;
import net.minecraft.client.Minecraft;

public final class BailClassCounts {
    public static final BailClassCounts MATERIAL = new BailClassCounts("tesr.bailCls.mat.", "BailMat");
    public static final BailClassCounts TEMPLATE = new BailClassCounts("tesr.bailCls.tmpl.", "BailTmpl");

    private final String plotPrefix;
    private final String label;
    private final Reference2IntOpenHashMap<Class<?>> counts = new Reference2IntOpenHashMap<>();
    private final Reference2ObjectOpenHashMap<Class<?>, String> plotNames = new Reference2ObjectOpenHashMap<>();
    private String debugLine = "";

    private BailClassCounts(String plotPrefix, String label) {
        this.plotPrefix = plotPrefix;
        this.label = label;
    }

    public void add(Class<?> cls) {
        counts.addTo(cls == null ? Object.class : cls, 1);
    }

    public void flushFrame() {
        if (counts.isEmpty()) {
            debugLine = "";
            return;
        }
        Class<?> a = null, b = null, c = null;
        int an = 0, bn = 0, cn = 0;
        final var it = Reference2IntMaps.fastIterator(counts);
        while (it.hasNext()) {
            final Reference2IntMap.Entry<Class<?>> entry = it.next();
            final int v = entry.getIntValue();
            Tracy.plotInt(plotName(entry.getKey()), v);
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
        if (!Minecraft.getMinecraft().gameSettings.showDebugInfo) {
            debugLine = "";
        } else {
            final StringBuilder sb = new StringBuilder(label).append(": ");
            appendEntry(sb, a, an);
            if (b != null) appendEntry(sb.append(", "), b, bn);
            if (c != null) appendEntry(sb.append(", "), c, cn);
            debugLine = sb.toString();
        }
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

    private void appendEntry(StringBuilder sb, Class<?> cls, int count) {
        sb.append(cls.getSimpleName()).append(" x").append(count);
    }

    public String debugLine() {
        return debugLine;
    }
}
