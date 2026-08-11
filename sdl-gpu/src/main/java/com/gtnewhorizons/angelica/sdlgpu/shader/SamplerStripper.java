package com.gtnewhorizons.angelica.sdlgpu.shader;

import com.gtnewhorizons.angelica.glsm.GlslTransformUtils;
import com.gtnewhorizons.angelica.glsm.shader.GlslVulkanPreprocess.Edit;
import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.tree.ParseTreeWalker;
import org.antlr.v4.runtime.tree.TerminalNode;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.taumc.glsl.grammar.GLSLLexer;
import org.taumc.glsl.grammar.GLSLParser;
import org.taumc.glsl.grammar.GLSLParserBaseListener;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.gtnewhorizons.angelica.glsm.shader.GlslVulkanPreprocess.applyEdits;

public final class SamplerStripper {

    private static final Logger LOGGER = LogManager.getLogger("Angelica-SDLGPU");

    private SamplerStripper() {}

    public static String stripUnused(String source) {
        final GLSLParser.Translation_unitContext root;
        try {
            root = GlslTransformUtils.parseFullQuiet(source);
        } catch (Exception e) {
            return source;
        }
        final List<Edit> edits = new ArrayList<>();
        collectEdits(root, source, edits);
        return edits.isEmpty() ? source : applyEdits(source, edits);
    }

    public static void collectEdits(GLSLParser.Translation_unitContext root, String source, List<Edit> edits) {
        record SamplerDecl(String name, int start, int stop) {}
        final List<SamplerDecl> samplers = new ArrayList<>();

        ParseTreeWalker.DEFAULT.walk(new GLSLParserBaseListener() {
            @Override
            public void enterDeclaration(GLSLParser.DeclarationContext ctx) {
                final GLSLParser.Init_declarator_listContext idl = ctx.init_declarator_list();
                if (idl == null) return;
                final GLSLParser.Single_declarationContext single = idl.single_declaration();
                if (single == null || single.fully_specified_type() == null) return;
                final GLSLParser.Fully_specified_typeContext fst = single.fully_specified_type();
                if (fst.type_qualifier() == null) return;

                boolean hasUniform = false;
                for (GLSLParser.Single_type_qualifierContext stq : fst.type_qualifier().single_type_qualifier()) {
                    if (stq.storage_qualifier() != null && "uniform".equals(stq.storage_qualifier().getText())) {
                        hasUniform = true;
                        break;
                    }
                }
                if (!hasUniform) return;
                if (fst.type_specifier() == null || fst.type_specifier().type_specifier_nonarray() == null) return;

                final String typeText = fst.type_specifier().type_specifier_nonarray().getText();
                if (!isSamplerType(typeText)) return;

                final int start = ctx.getStart().getStartIndex();
                final int stop = ctx.getStop().getStopIndex();
                if (single.typeless_declaration() != null && single.typeless_declaration().IDENTIFIER() != null) {
                    samplers.add(new SamplerDecl(single.typeless_declaration().IDENTIFIER().getText(), start, stop));
                }
                for (GLSLParser.Typeless_declarationContext td : idl.typeless_declaration()) {
                    if (td.IDENTIFIER() != null) {
                        samplers.add(new SamplerDecl(td.IDENTIFIER().getText(), start, stop));
                    }
                }
            }
        }, root);

        if (samplers.isEmpty()) return;

        final Map<String, Integer> refCounts = new HashMap<>();
        for (SamplerDecl s : samplers) refCounts.put(s.name(), 0);

        ParseTreeWalker.DEFAULT.walk(new GLSLParserBaseListener() {
            @Override
            public void visitTerminal(TerminalNode node) {
                final Token tok = node.getSymbol();
                if (tok.getType() != GLSLLexer.IDENTIFIER) return;
                final String name = tok.getText();
                final Integer prior = refCounts.get(name);
                if (prior == null) return;
                final int pos = tok.getStartIndex();
                for (SamplerDecl s : samplers) {
                    if (pos >= s.start() && pos <= s.stop()) return;
                }
                refCounts.put(name, prior + 1);
            }
        }, root);

        final Set<String> directiveRefs = GlslTransformUtils.identifiersInDirectiveText(source);

        for (SamplerDecl s : samplers) {
            if (refCounts.getOrDefault(s.name(), 0) != 0) continue;
            if (directiveRefs.contains(s.name())) {
                LOGGER.debug("Keeping sampler {}: referenced only inside a preprocessor block", s.name());
                continue;
            }
            edits.add(new Edit(s.start(), s.stop(), ""));
        }
    }

    private static boolean isSamplerType(String t) {
        return t.startsWith("sampler") || t.startsWith("isampler") || t.startsWith("usampler");
    }
}
