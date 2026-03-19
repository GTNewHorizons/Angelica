package net.minecraft.launchwrapper;

import java.util.HashMap;
import java.util.Map;

/**
 * Stub for tests - provides blackboard without loading real launchwrapper.
 */
public class Launch {
    public static Map<String, Object> blackboard = new HashMap<>();

    static {
        blackboard.put("lwjgl3ify:rfb-booted", Boolean.FALSE);
    }
}
