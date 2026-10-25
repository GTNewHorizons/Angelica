package com.gtnewhorizons.angelica.debug.screen;

import com.gtnewhorizons.angelica.api.debug.DebugFact;
import com.gtnewhorizons.angelica.api.debug.DebugFactLines;
import com.gtnewhorizons.angelica.api.debug.DebugGroupHandle;
import com.gtnewhorizons.angelica.api.debug.DebugScreenDisplayer;

import java.util.ArrayList;
import java.util.List;

public final class RecordingDisplayer implements DebugScreenDisplayer {

    public static final DebugGroupHandle GROUP = new DebugGroupHandle() {

        @Override
        public String id() {
            return "test:group";
        }

        @Override
        public int side() {
            return LEFT;
        }
    };

    public final List<String> names = new ArrayList<>();
    public final List<StringBuilder> texts = new ArrayList<>();
    private final DebugFactImpl fact = new DebugFactImpl();

    @Override
    public DebugFact line(DebugGroupHandle group) {
        return fact(group, null);
    }

    @Override
    public DebugFact fact(DebugGroupHandle group, String name) {
        final StringBuilder text = new StringBuilder();
        names.add(name);
        texts.add(text);
        return fact.intoPlain(text);
    }

    public static RecordingDisplayer of(DebugFactLines facts) {
        final RecordingDisplayer displayer = new RecordingDisplayer();
        facts.emit(displayer, GROUP);
        return displayer;
    }

    public String text(int index) {
        return texts.get(index).toString();
    }
}
