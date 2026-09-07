package com.gtnewhorizons.angelica.loading.fml.transformers;

import com.gtnewhorizons.angelica.glsm.loading.ModRedirector;
import com.gtnewhorizons.angelica.loading.shared.transformers.AngelicaRedirector;
import net.minecraft.launchwrapper.IClassTransformer;

public class AngelicaRedirectorTransformer implements IClassTransformer {

    private final ModRedirector inner = AngelicaRedirector.create();

    @Override
    public byte[] transform(final String className, String transformedName, byte[] basicClass) {
        return inner.transform(transformedName, basicClass, this);
    }
}
