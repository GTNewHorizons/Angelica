package com.gtnewhorizons.angelica.loading.fml.compat.handlers;

import com.google.common.collect.ImmutableList;
import com.gtnewhorizons.angelica.loading.fml.compat.ICompatHandler;

import java.util.List;

public class KaizPatchXCompatHandler implements ICompatHandler {

    @Override
    public List<String> extraTransformers() {
        return ImmutableList.of("com.gtnewhorizons.angelica.loading.fml.compat.transformers.specific.KaizPatchXTransformer");
    }
}
