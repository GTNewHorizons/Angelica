package com.gtnewhorizons.angelica.transform.compat.handlers;

import com.gtnewhorizons.angelica.config.CompatConfig;
import lombok.Getter;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

public enum CompatHandlers {

    STACKS_ON_STACKS(() -> CompatConfig.fixStacksOnStacks, new StacksOnStacksCompatHandler()),
    EXTRA_UTILS(() -> CompatConfig.fixExtraUtils, new ExtraUtilsCompatHandler()),
    THAUMCRAFT(() -> CompatConfig.fixThaumcraft, new ThaumcraftCompatHandler()),
    THAUMIC_HORIZONS(() -> CompatConfig.fixThaumicHorizons, new ThaumicHorizonsCompatHandler());

    private static List<CompatHandler> compatHandlers = null;

    private final Supplier<Boolean> applyIf;

    @Getter
    private final CompatHandler handler;

    CompatHandlers(Supplier<Boolean> applyIf, CompatHandler handler) {
        this.applyIf = applyIf;
        this.handler = handler;
    }

    public boolean shouldBeLoaded() {
        return applyIf.get();
    }

    public static List<CompatHandler> getHandlers() {
        if (compatHandlers != null) {
            return compatHandlers;
        }
        compatHandlers = new ArrayList<>();
        for (CompatHandlers handler : values()) {
            if (handler.shouldBeLoaded()) {
                compatHandlers.add(handler.getHandler());
            }
        }
        return compatHandlers;
    }
}
