package com.gtnewhorizons.angelica.debug.screen.entries;

import com.gtnewhorizons.angelica.api.debug.DebugContext;
import com.gtnewhorizons.angelica.api.debug.DebugScreenDisplayer;
import com.gtnewhorizons.angelica.api.debug.DebugScreenEntry;
import com.gtnewhorizons.angelica.debug.screen.DebugGroup;
import com.gtnewhorizons.angelica.debug.screen.ForgeDebugPlaceholders;
import cpw.mods.fml.common.Loader;

import java.util.List;

public final class ForgeBrandingsEntry implements DebugScreenEntry {

    private static final String[] PREFIXES = { "MCP v", "FML v", "Minecraft Forge " };
    private static final String[] NAMES = { "MCP", "FML", "Forge" };

    private String[] names = new String[0];
    private String[] values = new String[0];
    private int modsLoaded;
    private int modsActive;
    private boolean resolved;

    @Override
    public void display(DebugScreenDisplayer displayer, DebugContext context) {
        if (!resolved) resolve();
        for (int i = 0; i < names.length; i++) {
            if (names[i] != null) displayer.fact(DebugGroup.FORGE, names[i]).value(values[i]);
        }
        displayer.fact(DebugGroup.FORGE, "Mods").value(modsLoaded).text(" loaded, ").value(modsActive).text(" active");
        for (int i = 0; i < names.length; i++) {
            if (names[i] == null) displayer.line(DebugGroup.FORGE).text(values[i]);
        }
    }

    private void resolve() {
        resolved = true;
        final List<String> brandings = ForgeDebugPlaceholders.brandings();
        final int count = Math.max(0, brandings.size() - 1);
        names = new String[count];
        values = new String[count];
        for (int i = 0; i < count; i++) {
            final String line = brandings.get(i);
            final int kind = prefixIndex(line);
            if (kind >= 0) {
                names[i] = NAMES[kind];
                values[i] = line.substring(PREFIXES[kind].length());
            } else {
                values[i] = line;
            }
        }
        modsLoaded = Loader.instance().getModList().size();
        modsActive = Loader.instance().getActiveModList().size();
    }

    static int prefixIndex(String line) {
        for (int i = 0; i < PREFIXES.length; i++) {
            if (line.startsWith(PREFIXES[i])) return i;
        }
        return -1;
    }
}
