package com.gtnewhorizons.umbra.loading.fml.transformers;

import com.gtnewhorizons.angelica.glsm.loading.EarlyRedirectorCore;
import com.gtnewhorizons.angelica.glsm.loading.EcosystemNarrowRules;
import net.minecraft.launchwrapper.IClassTransformer;

/**
 * A scoped redirector that only transforms classes from known-misbehaving (core)mod packages.
 * <p>
 * Some mods prematurely load classes that call GL functions during coremod discovery/injectData.
 * Those classes get missed by the late-registered {@link UmbraRedirectorTransformer}, so their
 * GL calls permanently bypass GLSM.
 * <p>
 * Registered in {@code UmbraClientTweaker} constructor. Removed by {@code UmbraLateTweaker}
 * once the full redirector is in its proper post-mixin position.
 *
 * @see EcosystemNarrowRules#EARLY_REDIRECTOR_TARGETS
 */
public class EarlyRedirectorTransformer implements IClassTransformer {

    private final EarlyRedirectorCore impl = new EarlyRedirectorCore(
        "com.gtnewhorizons.umbra.loading"
    );

    @Override
    public byte[] transform(String name, String transformedName, byte[] basicClass) {
        return impl.transform(transformedName, basicClass);
    }
}
