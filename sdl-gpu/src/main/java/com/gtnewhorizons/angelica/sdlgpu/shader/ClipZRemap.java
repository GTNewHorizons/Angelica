package com.gtnewhorizons.angelica.sdlgpu.shader;

import com.gtnewhorizons.angelica.glsm.GlslTransformUtils;
import com.gtnewhorizons.angelica.glsm.shader.GlslVulkanPreprocess.Edit;
import org.antlr.v4.runtime.tree.ParseTreeWalker;
import org.taumc.glsl.grammar.GLSLParser;
import org.taumc.glsl.grammar.GLSLParserBaseListener;

import java.util.ArrayList;
import java.util.List;

import static com.gtnewhorizons.angelica.glsm.shader.GlslVulkanPreprocess.applyEdits;

public final class ClipZRemap {

    private static final String INJECTION_STD = "gl_Position.z = gl_Position.z * 0.5 + gl_Position.w * 0.5;\n";

    private ClipZRemap() {}

    public static String injectGLToVulkanClipZ(String source) {
        final GLSLParser.Translation_unitContext root;
        try {
            root = GlslTransformUtils.parseFullQuiet(source);
        } catch (Exception e) {
            return source;
        }
        final ArrayList<Edit> edits = new ArrayList<>(1);
        collectEdits(root, edits);
        return edits.isEmpty() ? source : applyEdits(source, edits);
    }

    public static void collectEdits(GLSLParser.Translation_unitContext root, List<Edit> edits) {
        final int[] mainCloseBraceIdx = { -1 };
        ParseTreeWalker.DEFAULT.walk(new GLSLParserBaseListener() {
            @Override
            public void enterFunction_definition(GLSLParser.Function_definitionContext ctx) {
                if (mainCloseBraceIdx[0] >= 0) return;
                final GLSLParser.Function_prototypeContext proto = ctx.function_prototype();
                if (proto == null || proto.IDENTIFIER() == null) return;
                if (!"main".equals(proto.IDENTIFIER().getText())) return;
                final GLSLParser.Compound_statement_no_new_scopeContext body = ctx.compound_statement_no_new_scope();
                if (body == null || body.RIGHT_BRACE() == null) return;
                mainCloseBraceIdx[0] = body.RIGHT_BRACE().getSymbol().getStartIndex();
            }
        }, root);

        if (mainCloseBraceIdx[0] < 0) return;
        final int idx = mainCloseBraceIdx[0];
        edits.add(new Edit(idx, idx - 1, INJECTION_STD));
    }
}
