package com.gtnewhorizons.angelica.debug.screen;

import com.gtnewhorizons.angelica.api.debug.DebugFact;
import com.gtnewhorizons.angelica.api.debug.DebugGroupHandle;
import com.gtnewhorizons.angelica.api.debug.DebugScreenDisplayer;
import net.minecraft.client.Minecraft;

import java.util.ArrayList;
import java.util.Arrays;

public final class FlatDebugDisplayer implements DebugScreenDisplayer {

    private static final String SPACER = null;
    private static final String SEPARATOR = ": ";

    private static final class Side {

        StringBuilder[] builders = new StringBuilder[8];
        String[] strings = new String[8];
        int count;
        DebugGroupHandle lastGroup;

        Side() {
            for (int i = 0; i < builders.length; i++) builders[i] = new StringBuilder();
        }

        StringBuilder next() {
            if (count == builders.length) {
                final int n = count * 2;
                builders = Arrays.copyOf(builders, n);
                for (int i = count; i < n; i++) builders[i] = new StringBuilder();
                strings = Arrays.copyOf(strings, n);
            }
            final StringBuilder builder = builders[count++];
            builder.setLength(0);
            return builder;
        }

        void finish() {
            for (int i = 0; i < count; i++) {
                final StringBuilder builder = builders[i];
                final String current = strings[i];
                if (builder.length() == 0) {
                    strings[i] = SPACER;
                } else if (current == null || !current.contentEquals(builder)) {
                    strings[i] = builder.toString();
                }
            }
        }

        void addTo(ArrayList<String> target) {
            if (count == 0) return;
            target.ensureCapacity(target.size() + count + 1);
            if (!target.isEmpty()) target.add(SPACER);
            for (int i = 0; i < count; i++) target.add(strings[i]);
        }
    }

    private final DebugRegistry registry;
    private final DebugFactImpl fact = new DebugFactImpl();
    private final Side[] sides = { new Side(), new Side() };
    private int lastTick = Integer.MIN_VALUE;
    private long lastVersion = -1L;

    public FlatDebugDisplayer(DebugRegistry registry) {
        this.registry = registry;
    }

    void beginLines() {
        for (int s = 0; s < 2; s++) {
            sides[s].count = 0;
            sides[s].lastGroup = null;
        }
    }

    void endLines() {
        sides[0].finish();
        sides[1].finish();
    }

    public void rebuild(Minecraft mc, int tick, long version) {
        if (tick == lastTick && version == lastVersion) return;
        lastTick = tick;
        lastVersion = version;
        beginLines();
        try {
            DebugScreen.runEntries(this, mc, version, registry, false);
        } finally {
            endLines();
        }
    }

    public void addTo(ArrayList<String> left, ArrayList<String> right) {
        sides[DebugGroupHandle.LEFT].addTo(left);
        sides[DebugGroupHandle.RIGHT].addTo(right);
    }

    public int lineCount(int side) {
        return sides[side].count;
    }

    public String lineAt(int side, int i) {
        return sides[side].strings[i];
    }

    private DebugFact open(DebugGroupHandle handle) {
        final DebugGroup group = DebugGroup.of(handle);
        final Side side = sides[group.side()];
        if (side.lastGroup != group) {
            if (side.count > 0) side.next();
            side.lastGroup = group;
        }
        return fact.into(side.next());
    }

    @Override
    public DebugFact line(DebugGroupHandle group) {
        return open(group);
    }

    @Override
    public DebugFact fact(DebugGroupHandle group, String name) {
        DebugGroup.requireName(name);
        return open(group).text(name).text(SEPARATOR);
    }
}
