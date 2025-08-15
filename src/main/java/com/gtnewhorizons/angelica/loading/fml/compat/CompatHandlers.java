package com.gtnewhorizons.angelica.loading.fml.compat;

import com.gtnewhorizons.angelica.config.CompatConfig;
import com.gtnewhorizons.angelica.loading.fml.compat.handlers.ExtraUtilsCompatHandler;
import com.gtnewhorizons.angelica.loading.fml.compat.handlers.ImmersiveEngineeringCompatHandler;
import com.gtnewhorizons.angelica.loading.fml.compat.handlers.StacksOnStacksCompatHandler;
import com.gtnewhorizons.angelica.loading.fml.compat.handlers.ThaumcraftCompatHandler;
import com.gtnewhorizons.angelica.loading.fml.compat.handlers.ThaumicHorizonsCompatHandler;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Supplier;

public enum CompatHandlers {

    STACKS_ON_STACKS(() -> CompatConfig.fixStacksOnStacks, new StacksOnStacksCompatHandler()),
    EXTRA_UTILS(() -> CompatConfig.fixExtraUtils, new ExtraUtilsCompatHandler()),
    IMMERSIVE_ENGINEERING(() -> CompatConfig.fixImmersiveEngineering, new ImmersiveEngineeringCompatHandler()),
    THAUMCRAFT(() -> CompatConfig.fixThaumcraft, new ThaumcraftCompatHandler()),
    THAUMIC_HORIZONS(() -> CompatConfig.fixThaumicHorizons, new ThaumicHorizonsCompatHandler());

    private final Supplier<Boolean> applyIf;
    private final ICompatHandler handler;

    CompatHandlers(Supplier<Boolean> applyIf, ICompatHandler handler) {
        this.applyIf = applyIf;
        this.handler = handler;
    }

    private static List<ICompatHandler> compatHandlers = null;

    public static List<ICompatHandler> getHandlers() {
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

    /**
     * Returns extra transformers as well as the main transformer.
     * Returns an empty list if no handlers registered.
     */
    public static List<String> getTransformers() {
        final List<ICompatHandler> handlers = getHandlers();
        if (handlers.isEmpty()) {
            return Collections.emptyList();
        }
        List<String> transformers =  new ArrayList<>();
        for (ICompatHandler handler : handlers) {
            if (handler.extraTransformers() != null) {
                transformers.addAll(handler.extraTransformers());
            }
        }
        transformers.add("com.gtnewhorizons.angelica.loading.fml.transformers.GenericCompatTransformer");
        return transformers;
    }
}
