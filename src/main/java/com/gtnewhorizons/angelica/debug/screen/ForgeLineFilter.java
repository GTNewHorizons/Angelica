package com.gtnewhorizons.angelica.debug.screen;

import com.gtnewhorizons.angelica.api.debug.DebugGroupHandle;
import com.gtnewhorizons.angelica.config.CompatConfig;

import java.util.ArrayList;

public final class ForgeLineFilter {

    public interface Sink {

        void add(int group, String source, int start, String name);
    }

    private final DebugRegistry registry;
    private final DebugEntryDef[] gates;
    private final DebugGroup[] others;
    private DebugLineRules rules;
    private String[] rulesConfig;

    ForgeLineFilter(DebugRegistry registry) {
        this.registry = registry;
        gates = new DebugEntryDef[] { registry.entryById(DebugGroup.OTHER_LEFT.id), registry.entryById(DebugGroup.OTHER_RIGHT.id) };
        others = new DebugGroup[] { registry.groupById(DebugGroup.OTHER_LEFT.id), registry.groupById(DebugGroup.OTHER_RIGHT.id) };
    }

    public void sample(ArrayList<String> left, ArrayList<String> right, Sink sink) {
        final String[] config = CompatConfig.debugLineRules;
        if (rules == null || config != rulesConfig) {
            rules = DebugLineRules.compile(registry, config);
            rulesConfig = config;
        }
        sampleSide(DebugGroupHandle.LEFT, left, sink);
        sampleSide(DebugGroupHandle.RIGHT, right, sink);
    }

    private void sampleSide(int side, ArrayList<String> list, Sink sink) {
        final DebugEntryDef gate = gates[side];
        final DebugGroup other = others[side];
        if (gate == null || other == null) return;
        final boolean otherEnabled = gate.isEnabled();
        final int size = list.size();
        int nullBudget = 0;
        for (int i = 0; i < size; i++) {
            if (ForgeDebugPlaceholders.isPlaceholder(list.get(i))) {
                nullBudget = ForgeDebugPlaceholders.nullsAdded(side);
                break;
            }
        }
        boolean otherEmitted = false;
        boolean separatorPending = false;
        for (int i = 0; i < size; i++) {
            final String s = list.get(i);
            if (s == null && nullBudget > 0) {
                nullBudget--;
                continue;
            }
            if (s == null || DebugLineRules.isBlank(s)) {
                separatorPending |= otherEmitted;
                continue;
            }
            if (ForgeDebugPlaceholders.isPlaceholder(s)) continue;
            final DebugLineRules.Rule rule = rules.match(side, s);
            final DebugGroup group = rule == null ? other : rule.group();
            if (group == null) continue;
            if (group == other) {
                if (!otherEnabled) continue;
                if (separatorPending) sink.add(other.index(), null, 0, null);
                separatorPending = false;
                otherEmitted = true;
            }
            if (rule == null) {
                sink.add(group.index(), s, 0, null);
            } else {
                sink.add(group.index(), s, rule.payloadStart(s), rule.name());
            }
        }
    }
}
