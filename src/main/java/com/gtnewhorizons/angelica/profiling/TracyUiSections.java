package com.gtnewhorizons.angelica.profiling;

import com.gtnewhorizons.angelica.glsm.profiling.Tracy;
import net.minecraft.client.gui.GuiMainMenu;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiIngameMenu;

import java.util.HashMap;
import java.util.Map;

public final class TracyUiSections {

    private static final Map<Class<?>, String> LABELS = new HashMap<>();
    private static Class<?> current = TracyUiSections.class;
    private static long section;

    private TracyUiSections() {}

    public static void poll(GuiScreen screen) {
        if (!Tracy.ENABLED) return;
        final Class<?> screenClass = screen == null ? null : screen.getClass();
        if (screenClass == current) return;
        current = screenClass;
        Tracy.sectionLeave(section);
        section = Tracy.sectionEnter(Tracy.SECTION_UI, label(screenClass));
    }

    private static String label(Class<?> screenClass) {
        if (screenClass == null) return "in world";
        String label = LABELS.get(screenClass);
        if (label == null) {
            if (screenClass == GuiMainMenu.class) label = "main menu";
            else if (screenClass == GuiIngameMenu.class) label = "pause";
            else label = screenClass.getSimpleName();
            LABELS.put(screenClass, label);
        }
        return label;
    }
}
