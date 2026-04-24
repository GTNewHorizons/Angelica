package com.gtnewhorizons.angelica.glsm.shader;

import com.gtnewhorizons.angelica.glsm.GlslTransformUtils;
import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.tree.ParseTreeWalker;
import org.antlr.v4.runtime.tree.TerminalNode;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.opengl.GL20;
import org.taumc.glsl.grammar.GLSLLexer;
import org.taumc.glsl.grammar.GLSLParser;
import org.taumc.glsl.grammar.GLSLParserBaseListener;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class GlslVulkanPreprocess {

    private static final Logger LOGGER = LogManager.getLogger("GlslVulkanPreprocess");

    public static final String SAMPLER_RENAMED = "angelica_sampler_renamed";

    private GlslVulkanPreprocess() {}

    public record Result(String rewrittenSource, Set<String> boolUniforms, Set<String> explicitVsInputs) {}

    /** Single text edit: replace {@code [startIdx, stopIdx]} (inclusive, ANTLR-style) with {@code replacement}. */
    public record Edit(int startIdx, int stopIdx, String replacement) {}

    public static int startIdx(ParserRuleContext ctx) { return ctx.getStart().getStartIndex(); }

    public static int stopIdx(ParserRuleContext ctx) { return ctx.getStop().getStopIndex(); }

    /** Apply non-overlapping edits to the source in one pass. Overlapping edits (later start inside
     *  an earlier range) are dropped — callers are responsible for keeping their walks disjoint. */
    public static String applyEdits(String src, List<Edit> edits) {
        if (edits.isEmpty()) return src;
        edits.sort(Comparator.comparingInt(Edit::startIdx));
        final StringBuilder sb = new StringBuilder(src.length());
        int cursor = 0;
        for (Edit e : edits) {
            if (e.startIdx < cursor) continue;
            sb.append(src, cursor, e.startIdx);
            sb.append(e.replacement);
            cursor = e.stopIdx + 1;
        }
        sb.append(src, cursor, src.length());
        return sb.toString();
    }

    /** Returns {@code null} on parse failure — callers treat that the same as a shaderc compile failure. */
    public static @Nullable Result run(String source, int glShaderType, String debugName) {
        final GLSLParser.Translation_unitContext root;
        try {
            root = GlslTransformUtils.parseFullQuiet(source);
        } catch (Exception e) {
            LOGGER.warn("glsl-transformation-lib parse failed for '{}': {}", debugName, e.getMessage());
            return null;
        }

        final boolean isVertex = glShaderType == GL20.GL_VERTEX_SHADER;
        final Set<String> bools = new HashSet<>();
        final Set<String> explicitInputs = new HashSet<>();
        final List<Edit> edits = new ArrayList<>();

        ParseTreeWalker.DEFAULT.walk(new GLSLParserBaseListener() {
            @Override
            public void enterInit_declarator_list(GLSLParser.Init_declarator_listContext ctx) {
                final GLSLParser.Single_declarationContext single = ctx.single_declaration();
                if (single == null || single.fully_specified_type() == null) return;

                final GLSLParser.Fully_specified_typeContext fst = single.fully_specified_type();
                final GLSLParser.Type_qualifierContext tq = fst.type_qualifier();
                if (tq == null) return;

                boolean hasUniform = false, hasIn = false, hasLocation = false;
                for (GLSLParser.Single_type_qualifierContext stq : tq.single_type_qualifier()) {
                    if (stq.storage_qualifier() != null) {
                        final String s = stq.storage_qualifier().getText();
                        if ("uniform".equals(s)) hasUniform = true;
                        else if ("in".equals(s)) hasIn = true;
                    } else if (stq.layout_qualifier() != null) {
                        for (GLSLParser.Layout_qualifier_idContext id : stq.layout_qualifier().layout_qualifier_id_list().layout_qualifier_id()) {
                            if (id.IDENTIFIER() != null && "location".equals(id.IDENTIFIER().getText())) {
                                hasLocation = true;
                                break;
                            }
                        }
                    }
                }

                final boolean boolDecl = hasUniform && isBool(fst);
                final boolean explicitInputDecl = isVertex && hasIn && hasLocation;

                if (single.typeless_declaration() != null) {
                    handleDeclarator(single.typeless_declaration(), hasUniform, boolDecl, explicitInputDecl, bools, explicitInputs, edits);
                }
                for (GLSLParser.Typeless_declarationContext td : ctx.typeless_declaration()) {
                    handleDeclarator(td, hasUniform, boolDecl, explicitInputDecl, bools, explicitInputs, edits);
                }
            }

            @Override
            public void visitTerminal(TerminalNode node) {
                final Token tok = node.getSymbol();
                if (tok.getType() == GLSLLexer.IDENTIFIER && "sampler".equals(tok.getText())) {
                    edits.add(new Edit(tok.getStartIndex(), tok.getStopIndex(), SAMPLER_RENAMED));
                }
            }
        }, root);

        return new Result(applyEdits(source, edits), bools, explicitInputs);
    }

    private static void handleDeclarator(GLSLParser.Typeless_declarationContext td, boolean hasUniform, boolean asBool, boolean asInput, Set<String> bools, Set<String> inputs, List<Edit> edits) {
        if (td.IDENTIFIER() == null) return;
        final String name = td.IDENTIFIER().getText();
        if (asBool) bools.add(name);
        if (asInput) inputs.add(name);
        if (hasUniform && td.EQUAL() != null && td.initializer() != null) {
            edits.add(new Edit(td.EQUAL().getSymbol().getStartIndex(), td.initializer().getStop().getStopIndex(), ""));
        }
    }

    private static boolean isBool(GLSLParser.Fully_specified_typeContext fst) {
        return fst.type_specifier() != null && fst.type_specifier().type_specifier_nonarray() != null && "bool".equals(fst.type_specifier().type_specifier_nonarray().getText());
    }
}
