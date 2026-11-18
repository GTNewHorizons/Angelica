package com.gtnewhorizons.angelica.debug.screen;

import com.gtnewhorizons.angelica.api.debug.DebugFact;
import com.gtnewhorizons.angelica.api.debug.DebugGroupHandle;
import com.gtnewhorizons.angelica.api.debug.DebugScreenDisplayer;

public final class GroupedDebugDisplayer implements DebugScreenDisplayer {

    private final DebugGroupContents[] groups;
    private final DebugFactImpl fact = new DebugFactImpl();

    public GroupedDebugDisplayer(DebugRegistry registry) {
        groups = new DebugGroupContents[registry.groupCount()];
        for (int i = 0; i < groups.length; i++) {
            groups[i] = new DebugGroupContents(registry.groupAt(i));
        }
    }

    public DebugGroupContents contents(int index) {
        return groups[index];
    }

    public void reset() {
        for (int i = 0; i < groups.length; i++) {
            groups[i].reset();
        }
    }

    @Override
    public DebugFact line(DebugGroupHandle group) {
        return fact.into(groups[DebugGroup.of(group).index].addLine().text);
    }

    @Override
    public DebugFact fact(DebugGroupHandle group, String name) {
        return fact.into(groups[DebugGroup.of(group).index].addFact(DebugGroup.requireName(name)).text);
    }
}
