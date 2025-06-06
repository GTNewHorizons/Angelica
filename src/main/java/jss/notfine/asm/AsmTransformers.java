package jss.notfine.asm;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Supplier;

import com.gtnewhorizons.angelica.config.AngelicaConfig;
import com.gtnewhorizons.angelica.loading.AngelicaTweaker;

import cpw.mods.fml.relauncher.FMLLaunchHandler;
import jss.notfine.config.MCPatcherForgeConfig;
import jss.notfine.config.NotFineConfig;

/**
 * Adapted from Hodgepodge
 */
public enum AsmTransformers {

    RENDERBLOCKS("RenderBlocks transformer", () -> AngelicaConfig.enableMCPatcherForgeFeatures
        && MCPatcherForgeConfig.CustomColors.enabled,
        Side.CLIENT, "jss.notfine.asm.RenderBlocksTransformer"),
    WORLDRENDERER("WorldRenderer transformer", () -> NotFineConfig.renderPass, Side.CLIENT,
                  "jss.notfine.asm.WorldRendererTransformer");

    private final Supplier<Boolean> applyIf;
    private final Side side;
    private final String[] transformerClasses;

    AsmTransformers(@SuppressWarnings("unused") String description, Supplier<Boolean> applyIf, Side side,
        String... transformers) {
        this.applyIf = applyIf;
        this.side = side;
        this.transformerClasses = transformers;
    }

    private boolean shouldBeLoaded() {
        return applyIf.get() && shouldLoadSide();
    }

    private boolean shouldLoadSide() {
        return side == Side.BOTH || (side == Side.SERVER && FMLLaunchHandler.side()
            .isServer())
            || (side == Side.CLIENT && FMLLaunchHandler.side()
                .isClient());
    }

    public static List<String> getTransformers() {
        final List<String> list = new ArrayList<>();
        for (AsmTransformers transformer : values()) {
            if (transformer.shouldBeLoaded()) {
                AngelicaTweaker.LOGGER.info("Loading transformer {}", (Object[]) transformer.transformerClasses);
                list.addAll(Arrays.asList(transformer.transformerClasses));
            } else {
                AngelicaTweaker.LOGGER.info("Not loading transformer {}", (Object[]) transformer.transformerClasses);
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
