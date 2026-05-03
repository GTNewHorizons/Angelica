package net.coderbot.iris.shaderpack.option.menu;

import java.util.Collections;
import java.util.Set;
import java.util.WeakHashMap;

public final class ProfileElementTracker {
    private static final Set<Object> SECOND_PROFILE_ELEMENTS =
        Collections.newSetFromMap(new WeakHashMap<>());

    private ProfileElementTracker() {
    }

    public static <T> T markSecondProfileSet(T element) {
        SECOND_PROFILE_ELEMENTS.add(element);
        return element;
    }

    public static boolean isSecondProfileSet(Object element) {
        return SECOND_PROFILE_ELEMENTS.contains(element);
    }
}
