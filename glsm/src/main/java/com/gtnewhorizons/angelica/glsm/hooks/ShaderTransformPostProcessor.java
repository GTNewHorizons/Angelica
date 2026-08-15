package com.gtnewhorizons.angelica.glsm.hooks;

import com.gtnewhorizons.angelica.glsm.shader.ShaderType;
import org.taumc.glsl.grammar.GLSLParser;

@FunctionalInterface
public interface ShaderTransformPostProcessor {
    void onTransformed(String transformedSource, ShaderType shaderType);

    default void onTransformed(String transformedSource, GLSLParser.Translation_unitContext bodyTree, int headerLen, ShaderType shaderType) {
        onTransformed(transformedSource, shaderType);
    }
}
