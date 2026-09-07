package com.gtnewhorizons.angelica.glsm.loading;

import com.gtnewhorizons.retrofuturabootstrap.SharedConfig;

public final class Lwjgl3ifyExclusions {

    public static void apply() {
        final var handle = SharedConfig.getRfbTransformers().stream()
            .filter(transformer -> transformer.id().equals("lwjgl3ify:redirect"))
            .findFirst()
            .orElse(null);
        if (handle == null) return;
        for (String exclusion : EcosystemNarrowRules.LWJGL3IFY_EXCLUSIONS_SHARED) {
            handle.exclusions().add(exclusion);
        }
    }

    private Lwjgl3ifyExclusions() {}
}
