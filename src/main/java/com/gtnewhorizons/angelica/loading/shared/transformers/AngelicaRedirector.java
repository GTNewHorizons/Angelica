package com.gtnewhorizons.angelica.loading.shared.transformers;

import com.gtnewhorizons.angelica.glsm.loading.ModRedirector;
import com.gtnewhorizons.angelica.loading.shared.AngelicaClassDump;

public final class AngelicaRedirector {

    public static final String LEGACY_HINT = "Call GL11 for GL calls; read cached state via com.gtnewhorizons.angelica.api.glstate.GLState";

    public static ModRedirector create() {
        final ModRedirector redirector = new ModRedirector(AngelicaClassDump.INSTANCE, "com.gtnewhorizons.angelica.transform");
        redirector.setLegacyHint(LEGACY_HINT);
        redirector.setLegacyRewriter(GLSMApiRedirects.INSTANCE);
        return redirector;
    }

    private AngelicaRedirector() {}
}
