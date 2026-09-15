package com.gtnewhorizons.angelica.loading.shared.transformers;

import com.gtnewhorizons.angelica.glsm.loading.ModRedirector;
import com.gtnewhorizons.angelica.loading.shared.AngelicaClassDump;

public final class AngelicaRedirector {

    public static ModRedirector create() {
        return new ModRedirector(AngelicaClassDump.INSTANCE, "com.gtnewhorizons.angelica.transform");
    }

    private AngelicaRedirector() {}
}
