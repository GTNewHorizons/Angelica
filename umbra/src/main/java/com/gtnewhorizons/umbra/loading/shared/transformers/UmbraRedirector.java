package com.gtnewhorizons.umbra.loading.shared.transformers;

import com.gtnewhorizons.angelica.glsm.loading.ClassDump;
import com.gtnewhorizons.angelica.glsm.loading.ModRedirector;

public final class UmbraRedirector {

    private static final ClassDump DUMP = new ClassDump(Boolean.getBoolean("umbra.dumpClass"));

    public static ModRedirector create() {
        return new ModRedirector(DUMP, "com.gtnewhorizons.umbra.loading");
    }

    private UmbraRedirector() {}
}
