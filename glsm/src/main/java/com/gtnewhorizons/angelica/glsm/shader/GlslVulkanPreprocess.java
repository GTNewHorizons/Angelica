package com.gtnewhorizons.angelica.glsm.shader;

import com.gtnewhorizons.angelica.glsm.GlslTransformUtils;
import it.unimi.dsi.fastutil.objects.Object2ObjectLinkedOpenHashMap;
import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.tree.ParseTree;
import org.antlr.v4.runtime.tree.ParseTreeWalker;
import org.antlr.v4.runtime.tree.TerminalNode;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.opengl.GL20;
import org.taumc.glsl.grammar.GLSLLexer;
import org.taumc.glsl.grammar.GLSLParser;
import org.taumc.glsl.grammar.GLSLPreParser;
import org.taumc.glsl.grammar.GLSLParserBaseListener;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class GlslVulkanPreprocess {

    private static final Logger LOGGER = LogManager.getLogger("GlslVulkanPreprocess");

    public static final String SAMPLER_RENAMED = "angelica_sampler_renamed";

    public static final String SAMPLERLESS_EXTENSION = "#extension GL_EXT_samplerless_texture_functions : require";

    private static final int MAX_VS_INPUT_LOCATIONS = 16;

    private static final int CACHE_MAX = 256;
    private static final Object2ObjectLinkedOpenHashMap<CacheKey, Result> CACHE = new Object2ObjectLinkedOpenHashMap<>();


    private GlslVulkanPreprocess() {}

    private record CacheKey(String source, int glShaderType, boolean separateReadOnlyImages) {}
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
            sorted.sort(Comparator.comparingInt(Edit::startIdx).thenComparingInt(Edit::stopIdx));
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
    public static @Nullable Result run(String source, int glShaderType, String debugName, boolean separateReadOnlyImages) {
        final CacheKey key = new CacheKey(source, glShaderType, separateReadOnlyImages);
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
        final Metadata meta = collectEdits(source, root, glShaderType, debugName, separateReadOnlyImages, edits);

        final Result out = new Result(applyEdits(source, edits), meta.boolUniforms(), meta.explicitVsInputs());
        synchronized (CACHE) {
            CACHE.putAndMoveToFirst(key, out);
            while (CACHE.size() > CACHE_MAX) CACHE.removeLast();
        }
        return out;
    }

    public record Metadata(Set<String> boolUniforms, Set<String> explicitVsInputs, boolean needsSamplerless) {}

    public static Metadata collectEdits(String source, GLSLParser.Translation_unitContext root, int glShaderType, String debugName, boolean separateReadOnlyImages, List<Edit> edits) {
        final boolean isVertex = glShaderType == GL20.GL_VERTEX_SHADER;
        final Set<String> bools = new HashSet<>();
        final Set<String> explicitInputs = new HashSet<>();
        final Set<String> readOnlyImages = new HashSet<>();
        final boolean[] needsSamplerless = { false };

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

                boolean hasUniform = false, hasIn = false, hasLocation = false, hasReadonly = false;
                int locValue = -1;
                int bindingValue = -1;
                for (GLSLParser.Single_type_qualifierContext stq : tq.single_type_qualifier()) {
                    if (stq.storage_qualifier() != null) {
                        final String s = stq.storage_qualifier().getText();
                        if ("uniform".equals(s)) hasUniform = true;
                        else if ("in".equals(s)) hasIn = true;
                        else if ("readonly".equals(s)) hasReadonly = true;
                    } else if (stq.layout_qualifier() != null) {
                        for (GLSLParser.Layout_qualifier_idContext id : stq.layout_qualifier().layout_qualifier_id_list().layout_qualifier_id()) {
                            if (id.IDENTIFIER() == null) continue;
                            final String qualifier = id.IDENTIFIER().getText();
                            if ("location".equals(qualifier)) {
                                hasLocation = true;
                                if (id.constant_expression() != null) {
                                    try { locValue = Integer.parseInt(id.constant_expression().getText()); } catch (NumberFormatException ignored) {}
                                }
                            } else if ("binding".equals(qualifier) && id.constant_expression() != null) {
                                try { bindingValue = Integer.parseInt(id.constant_expression().getText()); } catch (NumberFormatException ignored) {}
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

                if (separateReadOnlyImages && hasUniform && fst.type_specifier() != null) {
                    final String typeText = fst.type_specifier().getText();
                    if (separateTextureType(typeText) != null) {
                        needsSamplerless[0] = true;
                    } else if (hasReadonly) {
                        final String asTexture = separateTextureTypeForImage(typeText);
                        if (asTexture != null) {
                            needsSamplerless[0] = true;
                            collectDeclaredNames(single, ctx, readOnlyImages);
                            final String binding = bindingValue >= 0 ? "layout(binding = " + bindingValue + ") " : "";
                            edits.add(new Edit(startIdx(fst), stopIdx(fst), binding + "uniform " + asTexture));
                        }
                    }
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
            public void enterPostfix_expression(GLSLParser.Postfix_expressionContext ctx) {
                if (readOnlyImages.isEmpty() || ctx.LEFT_PAREN() == null || ctx.RIGHT_PAREN() == null) return;
                final GLSLParser.Function_call_parametersContext params = ctx.function_call_parameters();
                if (params == null || params.assignment_expression() == null || params.assignment_expression().isEmpty()) return;
                if (!readOnlyImages.contains(params.assignment_expression(0).getText())) return;

                final ParseTree callee = ctx.getChild(0);
                if (!(callee instanceof ParserRuleContext calleeCtx)) return;
                final String replacement = switch (calleeCtx.getText()) {
                    case "imageLoad" -> "texelFetch";
                    case "imageSize" -> "textureSize";
                    default -> null;
                };
                if (replacement == null) return;

                edits.add(new Edit(startIdx(calleeCtx), stopIdx(calleeCtx), replacement));
                final int rparen = ctx.RIGHT_PAREN().getSymbol().getStartIndex();
                edits.add(new Edit(rparen, rparen - 1, ", 0"));
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

        if (needsSamplerless[0] && source != null) {
            final int at = versionDirectiveEnd(source);
            if (at >= 0) edits.add(new Edit(at, at - 1, "\n" + SAMPLERLESS_EXTENSION));
        }

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

        return new Metadata(bools, explicitInputs, needsSamplerless[0]);
    }

    public static void clearCache() {
        synchronized (CACHE) { CACHE.clear(); }
    }

    private static void collectDeclaredNames(GLSLParser.Single_declarationContext single, GLSLParser.Init_declarator_listContext ctx, Set<String> out) {
        if (single.typeless_declaration() != null && single.typeless_declaration().IDENTIFIER() != null) {
            out.add(single.typeless_declaration().IDENTIFIER().getText());
        }
        for (GLSLParser.Typeless_declarationContext td : ctx.typeless_declaration()) {
            if (td.IDENTIFIER() != null) out.add(td.IDENTIFIER().getText());
        }
    }

    private static @Nullable String separateTextureTypeForImage(String typeText) {
        final String dim = switch (typeText) {
            case "image1D", "iimage1D", "uimage1D" -> "1D";
            case "image2D", "iimage2D", "uimage2D" -> "2D";
            case "image3D", "iimage3D", "uimage3D" -> "3D";
            default -> null;
        };
        if (dim == null) return null;
        return typeText.charAt(0) == 'i' ? "itexture" + dim : typeText.charAt(0) == 'u' ? "utexture" + dim : "texture" + dim;
    }

    private static @Nullable String separateTextureType(String typeText) {
        return switch (typeText) {
            case "texture1D", "itexture1D", "utexture1D",
                 "texture2D", "itexture2D", "utexture2D",
                 "texture3D", "itexture3D", "utexture3D" -> typeText;
            default -> null;
        };
    }

    private static int versionDirectiveEnd(String source) {
        final GLSLPreParser.Translation_unitContext pre;
        try {
            pre = GlslTransformUtils.parsePreQuiet(source);
        } catch (Exception e) {
            return -1;
        }
        final GLSLPreParser.Version_directiveContext ctx = firstVersionDirective(pre);
        return ctx == null || ctx.getStop() == null ? -1 : ctx.getStop().getStopIndex() + 1;
    }

    private static GLSLPreParser.@Nullable Version_directiveContext firstVersionDirective(ParseTree node) {
        if (node instanceof GLSLPreParser.Version_directiveContext v) return v;
        for (int i = 0; i < node.getChildCount(); i++) {
            final GLSLPreParser.Version_directiveContext found = firstVersionDirective(node.getChild(i));
            if (found != null) return found;
        }
        return null;
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
