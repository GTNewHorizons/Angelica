package com.gtnewhorizons.angelica.loading.fml.compat.handlers;

import com.gtnewhorizons.angelica.loading.fml.compat.ICompatHandler;

import java.util.Collections;
import java.util.List;

/**
 * Adds Galacticraft-specific compat transformers for legacy Galacticraft (non-GTNH).
 * The handler exposes the extra transformer class to AngelicaTweaker so it gets loaded
 * as an ASM transformer during startup.
 */
public class GalacticraftCompatHandler implements ICompatHandler {

    @Override
    public List<String> extraTransformers() {
        // Fully-qualified transformer class name to be loaded by AngelicaTweaker
        return Collections.singletonList("com.gtnewhorizons.angelica.loading.fml.compat.transformers.galacticraft.GalacticraftCameraTransformer");
    }
}
