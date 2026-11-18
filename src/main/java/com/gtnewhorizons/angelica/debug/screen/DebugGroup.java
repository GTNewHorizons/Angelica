package com.gtnewhorizons.angelica.debug.screen;

import com.gtnewhorizons.angelica.api.debug.DebugGroupHandle;
import com.gtnewhorizons.angelica.api.debug.DebugGroupSpec;

public final class DebugGroup implements DebugGroupHandle {

    public static final String PRIORITY_LEFT_ID = "angelica:priority_left";

    public static final DebugGroup PRIORITY_LEFT = builtin("priority_left", null, -1, LEFT, 0);
    public static final DebugGroup RENDERING = builtin("rendering", "rendering", 0xF0B0A0, LEFT, 100);
    public static final DebugGroup POSITION = builtin("position", "position", 0xFFFFFF, LEFT, 200);
    public static final DebugGroup SHADERS = builtin("shaders", "shaders", 0xFF55FF, LEFT, 300);
    public static final DebugGroup OTHER_LEFT = builtin("other_left", "other", -1, LEFT, 800);
    public static final DebugGroup HELP = builtin("help", "help", -1, LEFT, 1000);
    public static final DebugGroup MEMORY = builtin("memory", "memory", 0xFF9B00, RIGHT, 100);
    public static final DebugGroup SYSTEM_SPECS = builtin("system_specs", "system_specs", 0xFF0000, RIGHT, 200);
    public static final DebugGroup FORGE = builtin("forge", "forge", 0x5555FF, RIGHT, 300);
    public static final DebugGroup ANGELICA = builtin("angelica", "angelica", 0x55FF55, RIGHT, 400);
    public static final DebugGroup OTHER_RIGHT = builtin("other_right", "other", -1, RIGHT, 800);
    public static final DebugGroup LOOKING_AT_BLOCK = builtin("looking_at_block", "looking_at_block", 0xCC00FF, RIGHT, 900);

    public final String id;
    public final String titleKey;
    public final int accentColor;
    public final int side;
    public final int order;
    int index = -1;

    DebugGroup(String id, String titleKey, int accentColor, int side, int order) {
        this.id = id;
        this.titleKey = titleKey;
        this.accentColor = accentColor;
        this.side = side;
        this.order = order;
    }

    private static DebugGroup builtin(String path, String title, int accent, int side, int order) {
        final DebugGroupSpec spec = new DebugGroupSpec("angelica:" + path).side(side).order(order);
        if (title != null) spec.title("angelica.debug.group." + title);
        if (accent >= 0) spec.accent(accent);
        return DebugRegistry.MAIN.registerGroup(spec);
    }

    static void bootstrap() {}

    static DebugGroup of(DebugGroupHandle handle) {
        if (handle instanceof DebugGroup group) return group;
        throw new IllegalArgumentException("Not a debug group handle from DebugScreenApi: " + handle);
    }

    static String requireName(String name) {
        if (name == null) throw new IllegalArgumentException("Debug fact name is null");
        return name;
    }

    @Override
    public String id() {
        return id;
    }

    @Override
    public int side() {
        return side;
    }

    public int index() {
        return index;
    }

    public boolean hasTitle() {
        return titleKey != null;
    }

    public boolean hasAccent() {
        return accentColor >= 0;
    }
}
