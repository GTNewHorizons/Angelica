package com.gtnewhorizons.angelica.transform.compat.handlers;

import com.gtnewhorizons.angelica.config.CompatConfig;
import lombok.Getter;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

public enum CompatHandlers {

    STACKS_ON_STACKS(() -> CompatConfig.fixStacksOnStacks, new StacksOnStacksCompatHandler()),
    EXTRA_UTILS(() -> CompatConfig.fixExtraUtils, new ExtraUtilsCompatHandler()),
    IMMERSIVE_ENGINEERING(() -> CompatConfig.fixImmersiveEngineering, new ImmersiveEngineeringCompatHandler()),
    THAUMCRAFT(() -> CompatConfig.fixThaumcraft, new ThaumcraftCompatHandler()),
    THAUMIC_HORIZONS(() -> CompatConfig.fixThaumicHorizons, new ThaumicHorizonsCompatHandler());

    private final Supplier<Boolean> applyIf;
    private final CompatHandler handler;

    CompatHandlers(Supplier<Boolean> applyIf, CompatHandler handler) {
        this.applyIf = applyIf;
        this.handler = handler;
    }

    private static List<CompatHandler> compatHandlers = null;

    public static List<CompatHandler> getHandlers() {
        if (compatHandlers != null) {
            return compatHandlers;
        }
        compatHandlers = new ArrayList<>();
        for (CompatHandlers value : values()) {
            if (value.applyIf.get()) {
                compatHandlers.add(value.handler);
            }
        }
        return compatHandlers;
    }
}
