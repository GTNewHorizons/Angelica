package com.gtnewhorizons.angelica.glsm.loading;

public final class EarlyRedirectorCore extends ModRedirector {

    public EarlyRedirectorCore(String... extraExclusions) {
        super(ClassDump.DISABLED, EcosystemNarrowRules.EARLY_REDIRECTOR_TARGETS.clone(), extraExclusions);
    }
}
