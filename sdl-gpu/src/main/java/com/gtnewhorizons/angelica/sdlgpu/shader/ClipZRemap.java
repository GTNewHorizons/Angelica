package com.gtnewhorizons.angelica.sdlgpu.shader;

import com.gtnewhorizons.angelica.glsm.shader.GlslVulkanPreprocess.Edit;
import org.taumc.glsl.grammar.GLSLParser;

import java.util.List;

public final class ClipZRemap {

    private static final String INJECTION_STD = "gl_Position.z = gl_Position.z * 0.5 + gl_Position.w * 0.5;\n";

    private ClipZRemap() {}

    public static void collectEdits(GLSLParser.Translation_unitContext root, List<Edit> edits) {
        for (GLSLParser.External_declarationContext external : root.external_declaration()) {
            final GLSLParser.Function_definitionContext function = external.function_definition();
            if (function == null || function.function_prototype().IDENTIFIER() == null) continue;
            if (!"main".equals(function.function_prototype().IDENTIFIER().getText())) continue;
            final GLSLParser.Compound_statement_no_new_scopeContext body = function.compound_statement_no_new_scope();
            if (body == null || body.RIGHT_BRACE() == null) return;
            final int idx = body.RIGHT_BRACE().getSymbol().getStartIndex();
            edits.add(new Edit(idx, idx - 1, INJECTION_STD));
            return;
        }
    }
}
