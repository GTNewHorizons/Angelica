package com.gtnewhorizons.angelica.glsm.shader;

import com.gtnewhorizons.angelica.glsm.GlslTransformUtils;
import it.unimi.dsi.fastutil.objects.Object2ObjectLinkedOpenHashMap;
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

    private static final int MAX_VS_INPUT_LOCATIONS = 16;

    private static final int CACHE_MAX = 256;
    private static final Object2ObjectLinkedOpenHashMap<CacheKey, Result> CACHE = new Object2ObjectLinkedOpenHashMap<>();


    private GlslVulkanPreprocess() {}

    private record CacheKey(String source, int glShaderType) {}
    public record Result(String rewrittenSource, Set<String> boolUniforms, Set<String> explicitVsInputs) {}

    /** Replace {@code [startIdx, stopIdx]} with {@code replacement}. */
    public record Edit(int startIdx, int stopIdx, String replacement) {}

    public static int startIdx(ParserRuleContext ctx) {
        return ctx.getStart().getStartIndex();
    }
    public static int stopIdx(ParserRuleContext ctx) {
        return ctx.getStop().getStopIndex();
    }

    /** Apply non-overlapping edits to the source in one pass. */
    public static String applyEdits(String src, List<Edit> edits) {
        if (edits.isEmpty()) return src;
        final List<Edit> sorted;
        if (edits.size() == 1) {
            sorted = edits;
        } else {
            sorted = new ArrayList<>(edits);
            sorted.sort(Comparator.comparingInt(Edit::startIdx));
        }
        final StringBuilder sb = new StringBuilder(src.length());
        int cursor = 0;
        for (Edit e : sorted) {
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
        final CacheKey key = new CacheKey(source, glShaderType);
        synchronized (CACHE) {
            final Result hit = CACHE.getAndMoveToFirst(key);
            if (hit != null) return hit;
        }

        final GLSLParser.Translation_unitContext root;
        try {
            root = GlslTransformUtils.parseFullQuiet(source);
        } catch (Exception e) {
            LOGGER.warn("glsl-transformation-lib parse failed for '{}': {}", debugName, e.getMessage());
            return null;
        }

        final List<Edit> edits = new ArrayList<>();
        final Metadata meta = collectEdits(root, glShaderType, debugName, edits);

        final Result out = new Result(applyEdits(source, edits), meta.boolUniforms(), meta.explicitVsInputs());
        synchronized (CACHE) {
            CACHE.putAndMoveToFirst(key, out);
            while (CACHE.size() > CACHE_MAX) CACHE.removeLast();
        }
        return out;
    }

    public record Metadata(Set<String> boolUniforms, Set<String> explicitVsInputs) {}

    public static Metadata collectEdits(GLSLParser.Translation_unitContext root, int glShaderType, String debugName, List<Edit> edits) {
        final boolean isVertex = glShaderType == GL20.GL_VERTEX_SHADER;
        final Set<String> bools = new HashSet<>();
        final Set<String> explicitInputs = new HashSet<>();

        final int[] maxExplicitLoc = { -1 };
        final List<Integer> unlocatedVsInputStarts = new ArrayList<>();

        ParseTreeWalker.DEFAULT.walk(new GLSLParserBaseListener() {
            @Override
            public void enterInit_declarator_list(GLSLParser.Init_declarator_listContext ctx) {
                final GLSLParser.Single_declarationContext single = ctx.single_declaration();
                if (single == null || single.fully_specified_type() == null) return;

                final GLSLParser.Fully_specified_typeContext fst = single.fully_specified_type();
                final GLSLParser.Type_qualifierContext tq = fst.type_qualifier();
                if (tq == null) return;

                boolean hasUniform = false, hasIn = false, hasLocation = false;
                int locValue = -1;
                for (GLSLParser.Single_type_qualifierContext stq : tq.single_type_qualifier()) {
                    if (stq.storage_qualifier() != null) {
                        final String s = stq.storage_qualifier().getText();
                        if ("uniform".equals(s)) hasUniform = true;
                        else if ("in".equals(s)) hasIn = true;
                    } else if (stq.layout_qualifier() != null) {
                        for (GLSLParser.Layout_qualifier_idContext id : stq.layout_qualifier().layout_qualifier_id_list().layout_qualifier_id()) {
                            if (id.IDENTIFIER() != null && "location".equals(id.IDENTIFIER().getText())) {
                                hasLocation = true;
                                if (id.constant_expression() != null) {
                                    try { locValue = Integer.parseInt(id.constant_expression().getText()); } catch (NumberFormatException ignored) {}
                                }
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

                if (isVertex && hasIn) {
                    if (hasLocation && locValue >= 0) {
                        if (locValue > maxExplicitLoc[0]) maxExplicitLoc[0] = locValue;
                    } else if (!hasLocation) {
                        unlocatedVsInputStarts.add(ctx.getStart().getStartIndex());
                    }
                }
            }

            @Override
            public void visitTerminal(TerminalNode node) {
                final Token tok = node.getSymbol();
                if (tok.getType() != GLSLLexer.IDENTIFIER) return;
                final String text = tok.getText();
                switch (text) {
                    case "sampler" -> edits.add(new Edit(tok.getStartIndex(), tok.getStopIndex(), SAMPLER_RENAMED));
                    case "gl_VertexID" -> edits.add(new Edit(tok.getStartIndex(), tok.getStopIndex(), "gl_VertexIndex"));
                    case "gl_InstanceID" -> edits.add(new Edit(tok.getStartIndex(), tok.getStopIndex(), "gl_InstanceIndex"));
                    default -> {}
                }
            }
        }, root);

        if (maxExplicitLoc[0] >= 0 && !unlocatedVsInputStarts.isEmpty()) {
            int next = maxExplicitLoc[0] + 1;
            int dropped = 0;
            for (int start : unlocatedVsInputStarts) {
                if (next >= MAX_VS_INPUT_LOCATIONS) { dropped++; continue; }
                edits.add(new Edit(start, start - 1, "layout(location = " + next + ") "));
                next++;
            }
            if (dropped > 0) {
                LOGGER.warn("'{}': dropped {} VS input location-conflict fixes (would exceed {} attribute slots)", debugName, dropped, MAX_VS_INPUT_LOCATIONS);
            }
        }

        return new Metadata(bools, explicitInputs);
    }

    public static void clearCache() {
        synchronized (CACHE) { CACHE.clear(); }
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
