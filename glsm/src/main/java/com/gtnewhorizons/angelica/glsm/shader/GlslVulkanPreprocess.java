package com.gtnewhorizons.angelica.glsm.shader;

import com.gtnewhorizons.angelica.glsm.GlslTransformUtils;
import it.unimi.dsi.fastutil.objects.Object2ObjectLinkedOpenHashMap;
import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.Token;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.opengl.GL20;
import org.taumc.glsl.grammar.GLSLParser;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class GlslVulkanPreprocess {

    private static final Logger LOGGER = LogManager.getLogger("GlslVulkanPreprocess");

    public static final String SAMPLER_RENAMED = "angelica_sampler_renamed";

    public static final String RESERVED_RENAMED_PREFIX = "angelica_renamed_";

    private static final Set<String> TARGET_RESERVED = Set.of(
        "try", "catch", "throw", "delete", "operator", "private", "protected", "friend", "virtual", "explicit",
        "mutable", "typename", "typeid", "register", "signed", "char", "auto", "nullptr", "constexpr", "decltype",
        "noexcept", "alignas", "alignof", "static_assert", "thread_local", "wchar_t", "char16_t", "char32_t",
        "const_cast", "dynamic_cast", "reinterpret_cast", "static_cast", "export",
        "and", "or", "xor", "bitand", "bitor", "compl", "and_eq", "or_eq", "xor_eq", "not_eq");

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
        final Metadata meta = collectEdits(new ShaderIndex(root, source), glShaderType, debugName, separateReadOnlyImages, edits);

        final Result out = new Result(applyEdits(source, edits), meta.boolUniforms(), meta.explicitVsInputs());
        synchronized (CACHE) {
            CACHE.putAndMoveToFirst(key, out);
            while (CACHE.size() > CACHE_MAX) CACHE.removeLast();
        }
        return out;
    }

    public record Metadata(Set<String> boolUniforms, Set<String> explicitVsInputs, boolean needsSamplerless) {}

    public static Metadata collectEdits(ShaderIndex index, int glShaderType, String debugName, boolean separateReadOnlyImages, List<Edit> edits) {
        final boolean isVertex = glShaderType == GL20.GL_VERTEX_SHADER;
        final Set<String> bools = new HashSet<>();
        final Set<String> explicitInputs = new HashSet<>();
        final Set<String> readOnlyImages = new HashSet<>();
        boolean needsSamplerless = false;

        int maxExplicitLoc = -1;
        final List<Integer> unlocatedVsInputStarts = new ArrayList<>();

        for (GLSLParser.DeclarationContext declaration : index.globalDeclarations()) {
            final GLSLParser.Init_declarator_listContext list = declaration.init_declarator_list();
            if (list == null) continue;
            final GLSLParser.Single_declarationContext single = list.single_declaration();
            if (single == null || single.fully_specified_type() == null) continue;

            final GLSLParser.Fully_specified_typeContext fst = single.fully_specified_type();
            final GLSLParser.Type_qualifierContext tq = fst.type_qualifier();
            if (tq == null) continue;

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
            final List<GLSLParser.Typeless_declarationContext> declarators = ShaderIndex.declarators(list);
            for (GLSLParser.Typeless_declarationContext td : declarators) {
                handleDeclarator(td, hasUniform, boolDecl, explicitInputDecl, bools, explicitInputs, edits);
            }

            if (separateReadOnlyImages && hasUniform && fst.type_specifier() != null) {
                final String typeText = fst.type_specifier().getText();
                if (separateTextureType(typeText) != null) {
                    needsSamplerless = true;
                } else if (hasReadonly) {
                    final String asTexture = separateTextureTypeForImage(typeText);
                    if (asTexture != null) {
                        needsSamplerless = true;
                        for (GLSLParser.Typeless_declarationContext td : declarators) {
                            if (td.IDENTIFIER() != null) readOnlyImages.add(td.IDENTIFIER().getText());
                        }
                        final String binding = bindingValue >= 0 ? "layout(binding = " + bindingValue + ") " : "";
                        edits.add(new Edit(startIdx(fst), stopIdx(fst), binding + "uniform " + asTexture));
                    }
                }
            }

            if (isVertex && hasIn) {
                if (hasLocation && locValue >= 0) {
                    if (locValue > maxExplicitLoc) maxExplicitLoc = locValue;
                } else if (!hasLocation) {
                    unlocatedVsInputStarts.add(list.getStart().getStartIndex());
                }
            }
        }

        if (!readOnlyImages.isEmpty()) {
            rewriteReadOnlyImageCalls(index.callsTo("imageLoad"), "texelFetch", readOnlyImages, edits);
            rewriteReadOnlyImageCalls(index.callsTo("imageSize"), "textureSize", readOnlyImages, edits);
        }
        if (needsSamplerless) {
            final int versionEnd = index.directives().versionEnd();
            if (versionEnd >= 0) edits.add(new Edit(versionEnd, versionEnd - 1, "\n" + SAMPLERLESS_EXTENSION));
        }

        rename(index, "sampler", SAMPLER_RENAMED, edits);
        rename(index, "gl_VertexID", "gl_VertexIndex", edits);
        rename(index, "gl_InstanceID", "gl_InstanceIndex", edits);
        for (String reserved : TARGET_RESERVED) rename(index, reserved, RESERVED_RENAMED_PREFIX + reserved, edits);

        if (maxExplicitLoc >= 0 && !unlocatedVsInputStarts.isEmpty()) {
            int next = maxExplicitLoc + 1;
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

        return new Metadata(bools, explicitInputs, needsSamplerless);
    }

    private static void rename(ShaderIndex index, String name, String replacement, List<Edit> edits) {
        for (Token tok : index.occurrences(name)) edits.add(new Edit(tok.getStartIndex(), tok.getStopIndex(), replacement));
    }

    private static void rewriteReadOnlyImageCalls(List<GLSLParser.Postfix_expressionContext> calls, String replacement, Set<String> readOnlyImages, List<Edit> edits) {
        for (GLSLParser.Postfix_expressionContext call : calls) {
            if (call.RIGHT_PAREN() == null) continue;
            final GLSLParser.Function_call_parametersContext params = call.function_call_parameters();
            if (params == null || params.assignment_expression().isEmpty()) continue;
            if (!readOnlyImages.contains(params.assignment_expression(0).getText())) continue;

            final ParserRuleContext callee = (ParserRuleContext) call.getChild(0);
            edits.add(new Edit(startIdx(callee), stopIdx(callee), replacement));
            final int rparen = call.RIGHT_PAREN().getSymbol().getStartIndex();
            edits.add(new Edit(rparen, rparen - 1, ", 0"));
        }
    }

    public static void clearCache() {
        synchronized (CACHE) { CACHE.clear(); }
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
