package com.gtnewhorizons.umbra.loading.fml.transformers;

import com.gtnewhorizons.angelica.glsm.loading.ModRedirector;
import com.gtnewhorizons.umbra.loading.shared.transformers.UmbraRedirector;
import net.minecraft.launchwrapper.IClassTransformer;

public class UmbraRedirectorTransformer implements IClassTransformer {

    private final ModRedirector inner = UmbraRedirector.create();

    @Override
    public byte[] transform(final String className, String transformedName, byte[] basicClass) {
        return inner.transform(transformedName, basicClass, this);
    }
}
