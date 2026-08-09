package com.gtnewhorizons.angelica.glsm.hooks;

import com.gtnewhorizons.angelica.glsm.shader.ShaderType;

@FunctionalInterface
public interface ShaderTransformPostProcessor {
    void onTransformed(String transformedSource, ShaderType shaderType);
}
