package com.gtnewhorizons.angelica.debug.screen.entries;

import com.gtnewhorizons.angelica.api.debug.DebugContext;
import com.gtnewhorizons.angelica.api.debug.DebugFactLines;
import com.gtnewhorizons.angelica.api.debug.DebugScreenDisplayer;
import com.gtnewhorizons.angelica.api.debug.DebugScreenEntry;
import com.gtnewhorizons.angelica.debug.screen.DebugGroup;
import net.coderbot.iris.Iris;
import net.coderbot.iris.pipeline.WorldRenderingPipeline;
import net.coderbot.iris.shaderpack.ShaderPack;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class AngelicaShadersEntry implements DebugScreenEntry {

    private static final String TAG = "[" + Iris.MODNAME;
    private static final String SHADERPACK = "Shaderpack";

    private final DebugFactLines facts = new DebugFactLines();
    private final List<String> scratch = new ArrayList<>();

    @Override
    public void display(DebugScreenDisplayer displayer, DebugContext context) {
        if (context.isSampleDue()) rebuild();
        facts.emit(displayer, DebugGroup.SHADERS);
    }

    private void rebuild() {
        facts.begin();
        if (Iris.getIrisConfig().areShadersEnabled()) {
            final String name = Iris.getCurrentPackName();
            if (name != null) facts.fact(SHADERPACK, Iris.isFallback() ? name + " (fallback)" : name);
            final Optional<ShaderPack> pack = Iris.getCurrentPack();
            if (pack.isPresent()) facts.split(pack.get().getProfileInfo());
        } else {
            facts.fact(SHADERPACK, "(off)");
        }
        final Optional<WorldRenderingPipeline> pipeline = Iris.getPipelineManager().getPipeline();
        if (pipeline.isPresent()) {
            scratch.clear();
            pipeline.get().addDebugText(scratch);
            for (int i = 0, n = scratch.size(); i < n; i++) {
                final String line = stripTag(scratch.get(i));
                if (!line.isEmpty()) facts.split(line);
            }
        }
        facts.end();
    }

    static String stripTag(String line) {
        if (line == null) return "";
        String rest = line;
        if (line.startsWith(TAG)) {
            final int close = line.indexOf(']');
            if (close > 0) rest = line.substring(close + 1);
        }
        int start = 0;
        while (start < rest.length() && rest.charAt(start) == ' ') start++;
        return start == 0 ? rest : rest.substring(start);
    }
}
