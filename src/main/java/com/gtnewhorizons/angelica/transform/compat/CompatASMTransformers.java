package com.gtnewhorizons.angelica.transform.compat;

import com.gtnewhorizons.angelica.config.AngelicaConfig;
import com.gtnewhorizons.angelica.loading.AngelicaTweaker;
import com.gtnewhorizons.angelica.transform.compat.handlers.CompatHandler;
import cpw.mods.fml.relauncher.FMLLaunchHandler;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Supplier;

public enum CompatASMTransformers {

    FIELD_LEVEL_TESSELLATOR("Field Level Tessellator Transformer", () -> AngelicaConfig.enableSodium, Side.CLIENT,
        "com.gtnewhorizons.angelica.transform.compat.transformers.generic.FieldLevelTessellatorTransformer"),
    GET_TILE_ENTITY_NULL_GUARD("getTileEntity Null Guard Transformer", () -> AngelicaConfig.enableSodium, Side.CLIENT,
        "com.gtnewhorizons.angelica.transform.compat.transformers.generic.GetTileEntityNullGuardTransformer"),
    THREAD_SAFE_ISBRH_ANNOTATION("ThreadSafeISBRHAnnotation Transformer", () -> AngelicaConfig.enableSodium,
        Side.CLIENT, "com.gtnewhorizons.angelica.transform.compat.transformers.generic.ThreadSafeISBRHAnnotationTransformer"),
    HUDCACHING_EARLY_RETURN("HUDCaching Early Return Transformer", () -> AngelicaConfig.enableHudCaching, Side.CLIENT,
        "com.gtnewhorizons.angelica.transform.compat.transformers.generic.HUDCachingEarlyReturnTransformer");


    private final Supplier<Boolean> applyIf;
    private final Side side;
    private final String[] transformerClasses;

    CompatASMTransformers(String description, Supplier<Boolean> applyIf, Side side, String... transformers) {
        this.applyIf = applyIf;
        this.side = side;
        this.transformerClasses = transformers;
    }

    private boolean shouldBeLoaded() { return applyIf.get() && shouldLoadSide(); }

    private boolean shouldLoadSide() {
        return side == Side.BOTH || (side == Side.SERVER && FMLLaunchHandler.side()
            .isServer())
            || (side == Side.CLIENT && FMLLaunchHandler.side()
                .isClient());
    }

    public static List<String> getTransformers() {
        final List<String> list = new ArrayList<>();
        for (CompatASMTransformers transformer : values()) {
            if (transformer.shouldBeLoaded()) {
                AngelicaTweaker.LOGGER.info("Loading transformer {}", (Object[]) transformer.transformerClasses);
                list.addAll(Arrays.asList(transformer.transformerClasses));
            } else {
                AngelicaTweaker.LOGGER.info("Not loading transformer {}", (Object[]) transformer.transformerClasses);
            }
        }

        for (CompatHandler compatHandler : CompatRegistry.INSTANCE.getHandlers()) {
            if (compatHandler.extraTransformers() != null) {
                list.addAll(compatHandler.extraTransformers());
            }
        }

        return list;
    }

    private enum Side {
        BOTH,
        CLIENT,
        SERVER
    }
}
