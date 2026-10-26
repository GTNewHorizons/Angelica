package com.gtnewhorizons.angelica.debug.screen.entries;

import com.gtnewhorizons.angelica.api.debug.DebugContext;
import com.gtnewhorizons.angelica.api.debug.DebugScreenDisplayer;
import com.gtnewhorizons.angelica.api.debug.DebugScreenEntry;
import com.gtnewhorizons.angelica.debug.screen.DebugGroup;
import net.minecraft.client.Minecraft;
import net.minecraft.util.StatCollector;
import net.minecraftforge.common.MinecraftForge;

public final class HeaderEntries {

    private static final String GAME_VERSION = "Minecraft " + MinecraftForge.MC_VERSION;

    private HeaderEntries() {}

    public static void gameVersion(DebugScreenDisplayer displayer, DebugContext context) {
        displayer.line(DebugGroup.PRIORITY_LEFT).text(GAME_VERSION);
    }

    public static void fps(DebugScreenDisplayer displayer, DebugContext context) {
        displayer.line(DebugGroup.PRIORITY_LEFT).text(Minecraft.getMinecraft().debug);
    }

    public static final class Help implements DebugScreenEntry {

        private static final String[] KEYS = {
            "angelica.debug.help.options",
            "angelica.debug.help.hitboxes",
            "angelica.debug.help.chunk_borders",
            "angelica.debug.help.verbose",
            "angelica.debug.help.graphs"
        };

        private String[] translated;
        private long translatedAt;

        @Override
        public void display(DebugScreenDisplayer displayer, DebugContext context) {
            String[] lines = translated;
            final long translationUpdate = StatCollector.getLastTranslationUpdateTimeInMilliseconds();
            if (lines == null || translationUpdate != translatedAt) {
                translatedAt = translationUpdate;
                lines = new String[KEYS.length];
                for (int i = 0; i < KEYS.length; i++) {
                    lines[i] = StatCollector.translateToLocal(KEYS[i]);
                }
                translated = lines;
            }
            for (int i = 0; i < lines.length; i++) {
                displayer.line(DebugGroup.HELP).text(lines[i]);
            }
        }
    }
}
