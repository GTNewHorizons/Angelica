package net.coderbot.iris.pipeline.transform;

import com.gtnewhorizons.angelica.glsm.GlslTransformUtils;
import net.coderbot.iris.gl.image.ImageInformation;
import com.gtnewhorizons.angelica.glsm.texture.InternalTextureFormat;
import org.antlr.v4.runtime.CommonToken;
import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.tree.ParseTree;
import org.antlr.v4.runtime.tree.TerminalNode;
import org.taumc.glsl.ShaderParser;
import org.taumc.glsl.Transformer;
import org.taumc.glsl.grammar.GLSLLexer;
import org.taumc.glsl.grammar.GLSLParser;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.HashSet;
import java.util.Set;

public final class RwImageStoreExtractor {

    public enum RwExtractMode { CHUNK, COMPOSITE_VSH, COMPOSITE_FSH }

    public static RwExtractMode parseSentinel(String computeSource) {
        if (computeSource == null) return null;
        if (computeSource.startsWith("// _vg_mode: chunk")) return RwExtractMode.CHUNK;
        if (computeSource.startsWith("// _vg_mode: composite-vsh")) return RwExtractMode.COMPOSITE_VSH;
        if (computeSource.startsWith("// _vg_mode: composite-fsh")) return RwExtractMode.COMPOSITE_FSH;
        return null;
    }

    public record AttributeSlot(String name, String glslType, int byteOffset, String unpackExpr, boolean liveBranch) {
        public AttributeSlot(String name, String glslType, int byteOffset, String unpackExpr) {
            this(name, glslType, byteOffset, unpackExpr, true);
        }
    }

    public record Result(String strippedSource, String computeSource, Set<String> writtenImages, RwExtractMode mode) {}

    public record ImageDecl(String name, String glslType, String layoutFormat) {}

    public static class UnknownAttributeException extends RuntimeException {
        public UnknownAttributeException(String msg) { super(msg); }
    }

    private RwImageStoreExtractor() {}

    public static final int VG_VBUF_SSBO_BINDING = 9;
    private static final int STRIDE_BYTES = 48;
    private static final int STRIDE_UINTS = STRIDE_BYTES / 4;

    private static volatile Map<String, ImageInformation> activeCustomImages = Map.of();

    public static void setActiveCustomImages(Map<String, ImageInformation> images) {
        activeCustomImages = (images == null) ? Map.of() : Map.copyOf(images);
    }

    private static final Set<String> RW_CALLS = Set.of(
        "imageStore",
        "imageAtomicAdd", "imageAtomicAnd", "imageAtomicOr", "imageAtomicXor",
        "imageAtomicMin", "imageAtomicMax", "imageAtomicExchange", "imageAtomicCompSwap"
    );

    public static Result tryExtract(String source, PatchShaderType stage, String programName) {
        if (source == null) return null;
        if (stage != PatchShaderType.VERTEX && stage != PatchShaderType.FRAGMENT) return null;

        final ShaderParser.ParsedShader parsed = ShaderParser.parseShader(source);
        final GLSLParser.Translation_unitContext root = parsed.full();

        final List<GLSLParser.Single_declarationContext> decls = new ArrayList<>();
        final List<GLSLParser.Postfix_expressionContext> exprs = new ArrayList<>();
        GlslAstHelpers.collectInto(root,
            new List[] { decls, exprs },
            new Class<?>[] { GLSLParser.Single_declarationContext.class, GLSLParser.Postfix_expressionContext.class });

        final Map<String, ImageDecl> declared = collectImageDecls(decls);
        final Set<String> writtenImages = new LinkedHashSet<>();
        final Set<String> nonWriteonlyImages = new LinkedHashSet<>();
        classifyImageCalls(exprs, declared, writtenImages, nonWriteonlyImages);
        if (writtenImages.isEmpty()) return null;

        final Map<String, String> chunkAttrAstTypes = (stage == PatchShaderType.VERTEX) ? collectChunkAttrAstTypes(decls) : Map.of();
        final Set<String> chunkAttrsUsed = (stage == PatchShaderType.VERTEX) ? collectChunkAttrsUsed(decls, root, source) : Set.of();

        final RwExtractMode mode = (stage == PatchShaderType.VERTEX) ? (chunkAttrsUsed.isEmpty() ? RwExtractMode.COMPOSITE_VSH : RwExtractMode.CHUNK) : RwExtractMode.COMPOSITE_FSH;

        final List<AttributeSlot> attrs = (mode == RwExtractMode.CHUNK) ? collectVertexAttributes(chunkAttrsUsed, chunkAttrAstTypes) : List.of();

        final String stripped = buildRasterOutputViaSourceSlicing(source, decls, exprs, writtenImages, declared, mode);

        final Transformer tc = new Transformer(root);
        applyComputeMutations(tc, writtenImages, declared, chunkAttrsUsed, mode, nonWriteonlyImages);
        final String compute = buildComputeOutput(root, mode, writtenImages, nonWriteonlyImages, declared, attrs);

        return new Result(stripped, compute, writtenImages, mode);
    }

    private static Map<String, ImageDecl> collectImageDecls(List<GLSLParser.Single_declarationContext> decls) {
        final LinkedHashMap<String, ImageDecl> out = new LinkedHashMap<>();
        for (var decl : decls) {
            final var ts = decl.fully_specified_type();
            if (ts == null || ts.type_specifier() == null || ts.type_specifier().type_specifier_nonarray() == null) continue;
            final var nonArray = ts.type_specifier().type_specifier_nonarray();
            if (nonArray.getChildCount() == 0) continue;
            final var first = nonArray.getChild(0);
            if (!(first instanceof TerminalNode tn)) continue;
            final int tokenType = tn.getSymbol().getType();
            if (!isImageToken(tokenType)) continue;
            final var typeless = decl.typeless_declaration();
            if (typeless == null || typeless.IDENTIFIER() == null) continue;
            final String name = typeless.IDENTIFIER().getText();
            final String glslType = tn.getText();
            final String layoutFormat = extractLayoutFormat(ts);
            out.putIfAbsent(name, new ImageDecl(name, glslType, layoutFormat));
        }
        return out;
    }

    private static String extractLayoutFormat(GLSLParser.Fully_specified_typeContext fst) {
        if (fst.type_qualifier() == null) return null;
        for (var sq : fst.type_qualifier().single_type_qualifier()) {
            final var lq = sq.layout_qualifier();
            if (lq == null || lq.layout_qualifier_id_list() == null) continue;
            for (var id : lq.layout_qualifier_id_list().layout_qualifier_id()) {
                if (id.IDENTIFIER() == null) continue;
                final String text = id.IDENTIFIER().getText();
                if (isImageFormat(text)) return text;
            }
        }
        return null;
    }

    private static boolean isImageFormat(String s) {
        return s.startsWith("rgba") || s.startsWith("rgb") || s.startsWith("rg")
            || s.startsWith("r32") || s.startsWith("r16") || s.startsWith("r8")
            || s.startsWith("r11f") || s.startsWith("r10");
    }

    private static boolean isImageToken(int t) {
        return t == GLSLLexer.IMAGE1D || t == GLSLLexer.IMAGE2D || t == GLSLLexer.IMAGE3D
            || t == GLSLLexer.IIMAGE1D || t == GLSLLexer.IIMAGE2D || t == GLSLLexer.IIMAGE3D
            || t == GLSLLexer.UIMAGE1D || t == GLSLLexer.UIMAGE2D || t == GLSLLexer.UIMAGE3D;
    }

    private static void classifyImageCalls(List<GLSLParser.Postfix_expressionContext> exprs, Map<String, ImageDecl> declared, Set<String> writtenImages, Set<String> nonWriteonlyImages) {
        for (var expr : exprs) {
            final String fname = GlslAstHelpers.extractCallName(expr);
            if (fname == null) continue;
            final String firstArg = GlslAstHelpers.firstArgIdentifier(expr);
            if (firstArg.isEmpty() || !declared.containsKey(firstArg)) continue;
            if (RW_CALLS.contains(fname)) {
                writtenImages.add(firstArg);
                if (!"imageStore".equals(fname)) {
                    nonWriteonlyImages.add(firstArg);
                }
            } else if ("imageLoad".equals(fname)) {
                nonWriteonlyImages.add(firstArg);
            }
        }
    }

    private static Set<String> collectChunkAttrsUsed(List<GLSLParser.Single_declarationContext> decls, GLSLParser.Translation_unitContext root, String source) {
        final LinkedHashSet<String> out = new LinkedHashSet<>();
        for (var decl : decls) {
            if (decl.typeless_declaration() == null || decl.typeless_declaration().IDENTIFIER() == null) continue;
            final String name = decl.typeless_declaration().IDENTIFIER().getText();
            if (CHUNK_VERTEX_ATTRS.containsKey(name)) out.add(name);
        }
        for (var ref : GlslAstHelpers.collectAll(root, GLSLParser.Variable_identifierContext.class)) {
            if (ref.IDENTIFIER() == null) continue;
            final String name = ref.IDENTIFIER().getText();
            if (CHUNK_VERTEX_ATTRS.containsKey(name)) out.add(name);
        }
        for (String attr : CHUNK_VERTEX_ATTRS.keySet()) {
            if (containsAsToken(source, attr)) out.add(attr);
        }
        return out;
    }

    private static boolean containsAsToken(String text, String name) {
        int from = 0;
        while (true) {
            final int idx = text.indexOf(name, from);
            if (idx < 0) return false;
            final boolean beforeOk = idx == 0 || !isIdentChar(text.charAt(idx - 1));
            final int end = idx + name.length();
            final boolean afterOk = end >= text.length() || !isIdentChar(text.charAt(end));
            if (beforeOk && afterOk) return true;
            from = idx + 1;
        }
    }

    private static boolean isIdentChar(char c) {
        return (c >= '0' && c <= '9') || (c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z') || c == '_';
    }


    private record SourceEdit(int start, int stop, String replacement) {}

    private static String buildRasterOutputViaSourceSlicing(String source, List<GLSLParser.Single_declarationContext> decls, List<GLSLParser.Postfix_expressionContext> exprs, Set<String> writtenImages, Map<String, ImageDecl> declared, RwExtractMode mode) {
        final List<SourceEdit> edits = new ArrayList<>();

        for (var decl : decls) {
            if (decl.typeless_declaration() == null || decl.typeless_declaration().IDENTIFIER() == null) continue;
            final String name = decl.typeless_declaration().IDENTIFIER().getText();
            if (!writtenImages.contains(name)) continue;
            final var ext = GlslAstHelpers.enclosingOfType(decl, GLSLParser.External_declarationContext.class);
            if (ext == null) continue;
            edits.add(rangeOf(ext));
        }

        for (var expr : exprs) {
            final String fname = GlslAstHelpers.extractCallName(expr);
            if (fname == null) continue;
            final String firstArg = GlslAstHelpers.firstArgIdentifier(expr);
            if (!writtenImages.contains(firstArg)) continue;
            if (RW_CALLS.contains(fname)) {
                final var stmt = GlslAstHelpers.enclosingOfType(expr, GLSLParser.StatementContext.class);
                if (stmt != null) edits.add(replaceOf(stmt, ";"));
            } else if ("imageLoad".equals(fname)) {
                final ImageDecl d = declared.get(firstArg);
                if (d == null) continue;
                edits.add(replaceOf(expr, GlslAstHelpers.zeroLiteralFor(d.glslType())));
            }
        }

        return applyEdits(source, edits);
    }

    private static SourceEdit rangeOf(ParserRuleContext ctx) {
        return new SourceEdit(ctx.getStart().getStartIndex(), ctx.getStop().getStopIndex() + 1, "");
    }

    private static SourceEdit replaceOf(ParserRuleContext ctx, String text) {
        return new SourceEdit(ctx.getStart().getStartIndex(), ctx.getStop().getStopIndex() + 1, text);
    }

    private static String applyEdits(String source, List<SourceEdit> edits) {
        if (edits.isEmpty()) return source;
        edits.sort(Comparator.comparingInt(SourceEdit::start));
        final StringBuilder out = new StringBuilder(source.length());
        int cursor = 0;
        for (SourceEdit e : edits) {
            if (e.start() < cursor) continue;
            out.append(source, cursor, e.start());
            out.append(e.replacement());
            cursor = e.stop();
        }
        if (cursor < source.length()) out.append(source, cursor, source.length());
        return out.toString();
    }

    private static void applyComputeMutations(Transformer tc, Set<String> writtenImages, Map<String, ImageDecl> declared, Set<String> chunkAttrsUsed, RwExtractMode mode, Set<String> nonWriteonlyImages) {
        hoistNonConstGlobalInitializers(tc);
        renameMainToVgBody(tc);
        rewriteImageAtomicsToNonAtomic(tc, declared, writtenImages, nonWriteonlyImages);
        for (String name : writtenImages) tc.removeVariable(name);
        if (mode == RwExtractMode.CHUNK) {
            for (String name : chunkAttrsUsed) tc.removeVariable(name);
            stripStorageQualifiers(tc, Set.of("out", "varying"));
        } else if (mode == RwExtractMode.COMPOSITE_VSH) {
            tc.renameFunctionCall("ftransform", "iris_ftransform");
            stripStorageQualifiers(tc, Set.of("in", "attribute", "out", "varying"));
        } else if (mode == RwExtractMode.COMPOSITE_FSH) {
            stripFragOutputs(tc);
            stripStorageQualifiers(tc, Set.of("in", "varying"));
        }
        stripInterfaceOnlyQualifiers(tc);
    }

    private static final Set<String> ATOMIC_CALLS = Set.of(
        "imageAtomicAdd", "imageAtomicAnd", "imageAtomicOr", "imageAtomicXor",
        "imageAtomicMin", "imageAtomicMax", "imageAtomicExchange", "imageAtomicCompSwap"
    );

    private static void rewriteImageAtomicsToNonAtomic(Transformer tc, Map<String, ImageDecl> declared, Set<String> writtenImages, Set<String> nonWriteonlyImages) {
        tc.mutateTree(tree -> {
            for (var expr : GlslAstHelpers.collectAll(tree, GLSLParser.Postfix_expressionContext.class)) {
                final String fname = GlslAstHelpers.extractCallName(expr);
                if (fname == null || !ATOMIC_CALLS.contains(fname)) continue;
                final String imgName = GlslAstHelpers.firstArgIdentifier(expr);
                if (!writtenImages.contains(imgName)) continue;
                final ImageDecl decl = declared.get(imgName);
                if (decl == null) continue;
                if (!isCallTopLevelStatement(expr)) continue;
                final String inlined = buildInlineAtomicEmulation(fname, imgName, GlslAstHelpers.argsOf(expr), decl);
                if (inlined == null) continue;
                final var replacement = ShaderParser.parseSnippet(inlined, GLSLParser::statement);
                if (replacement == null) continue;
                if (replaceEnclosingStatement(expr, replacement)) {
                    nonWriteonlyImages.add(imgName);
                }
            }
        });
    }

    private static boolean isCallTopLevelStatement(GLSLParser.Postfix_expressionContext expr) {
        final GLSLParser.StatementContext stmt = GlslAstHelpers.enclosingOfType(expr, GLSLParser.StatementContext.class);
        if (stmt == null) return false;
        return stmt.getText().equals(expr.getText() + ";");
    }

    private static boolean replaceEnclosingStatement(ParseTree expr, ParserRuleContext replacement) {
        final GLSLParser.StatementContext stmt = GlslAstHelpers.enclosingOfType(expr, GLSLParser.StatementContext.class);
        if (stmt == null) return false;
        if (!(stmt.parent instanceof ParserRuleContext parent) || parent.children == null) return false;
        final int idx = parent.children.indexOf(stmt);
        if (idx < 0) return false;
        replacement.parent = parent;
        parent.children.set(idx, replacement);
        return true;
    }

    private static String buildInlineAtomicEmulation(String op, String imgName, List<GLSLParser.Assignment_expressionContext> args, ImageDecl decl) {
        if (args.size() < 2) return null;
        final String idx = args.get(1).getText();
        final String glslType = decl.glslType();
        final String scalar = scalarFor(glslType);
        final String vec = componentVecFor(glslType);
        final String vecArgs = vecArgsFor(vec);
        final String compute;
        switch (op) {
            case "imageAtomicAdd" -> {
                if (args.size() < 3) return null;
                compute = "_vg_prev + " + args.get(2).getText();
            }
            case "imageAtomicAnd" -> {
                if (args.size() < 3) return null;
                compute = "_vg_prev & " + args.get(2).getText();
            }
            case "imageAtomicOr" -> {
                if (args.size() < 3) return null;
                compute = "_vg_prev | " + args.get(2).getText();
            }
            case "imageAtomicXor" -> {
                if (args.size() < 3) return null;
                compute = "_vg_prev ^ " + args.get(2).getText();
            }
            case "imageAtomicMin" -> {
                if (args.size() < 3) return null;
                compute = "min(_vg_prev, " + args.get(2).getText() + ")";
            }
            case "imageAtomicMax" -> {
                if (args.size() < 3) return null;
                compute = "max(_vg_prev, " + args.get(2).getText() + ")";
            }
            case "imageAtomicExchange" -> {
                if (args.size() < 3) return null;
                compute = args.get(2).getText();
            }
            case "imageAtomicCompSwap" -> {
                if (args.size() < 4) return null;
                compute = "(_vg_prev == " + args.get(2).getText() + " ? " + args.get(3).getText() + " : _vg_prev)";
            }
            default -> {
                return null;
            }
        }
        return "{ " + scalar + " _vg_prev = imageLoad(" + imgName + ", " + idx + ").x; "
            + "imageStore(" + imgName + ", " + idx + ", " + vec + "(" + compute + vecArgs + ")); }";
    }

    private static String scalarFor(String glslType) {
        if (glslType.startsWith("iimage")) return "int";
        if (glslType.startsWith("uimage")) return "uint";
        return "float";
    }

    private static String componentVecFor(String glslType) {
        if (glslType.startsWith("iimage")) return "ivec4";
        if (glslType.startsWith("uimage")) return "uvec4";
        return "vec4";
    }

    private static String vecArgsFor(String vec) {
        return switch (vec) {
            case "ivec4" -> ", 0, 0, 0";
            case "uvec4" -> ", 0u, 0u, 0u";
            default -> ", 0.0, 0.0, 0.0";
        };
    }

    private static void stripStorageQualifiers(Transformer t, Set<String> kill) {
        t.mutateTree(tree -> {
            for (var sq : GlslAstHelpers.collectAll(tree, GLSLParser.Storage_qualifierContext.class)) {
                if (!kill.contains(sq.getText())) continue;
                blankLeadingToken(sq);
            }
        });
    }

    private static void stripInterfaceOnlyQualifiers(Transformer t) {
        t.mutateTree(tree -> {
            for (var iq : GlslAstHelpers.collectAll(tree, GLSLParser.Interpolation_qualifierContext.class)) {
                blankLeadingToken(iq);
            }
            for (var sq : GlslAstHelpers.collectAll(tree, GLSLParser.Storage_qualifierContext.class)) {
                if (!AUXILIARY_STORAGE_QUALIFIERS.contains(sq.getText())) continue;
                blankLeadingToken(sq);
            }
        });
    }

    private static final Set<String> AUXILIARY_STORAGE_QUALIFIERS = Set.of("centroid", "sample", "patch");

    private static void blankLeadingToken(ParserRuleContext ctx) {
        if (ctx.getChildCount() == 0) return;
        if (!(ctx.getChild(0) instanceof TerminalNode tn)) return;
        if (tn.getSymbol() instanceof CommonToken ct) ct.setText("");
    }

    private static void hoistNonConstGlobalInitializers(Transformer t) {
        final StringBuilder assigns = new StringBuilder();
        t.mutateTree(tree -> {
            for (var ed : ((GLSLParser.Translation_unitContext) tree).external_declaration()) {
                final var decl = ed.declaration();
                if (decl == null) continue;
                final var singles = GlslAstHelpers.collectAll(decl, GLSLParser.Single_declarationContext.class);
                if (singles.isEmpty()) continue;
                final var fst = singles.get(0).fully_specified_type();
                if (fst == null || (fst.type_qualifier() != null && hasStorageQualifier(fst.type_qualifier()))) continue;
                if (hasUnsizedArraySpecifierOutsideInitializer(decl)) continue;
                for (var td : GlslAstHelpers.collectAll(decl, GLSLParser.Typeless_declarationContext.class)) {
                    if (td.IDENTIFIER() == null || td.EQUAL() == null || td.initializer() == null) continue;
                    assigns.append(td.IDENTIFIER().getText()).append(" = ").append(joinTerminals(td.initializer())).append(";\n");
                    if (td.EQUAL().getSymbol() instanceof CommonToken ct) ct.setText("");
                    blankAllTerminals(td.initializer());
                }
            }
        });
        if (assigns.length() > 0) t.prependMain("{\n" + assigns + "}\n");
    }

    private static boolean hasUnsizedArraySpecifierOutsideInitializer(ParserRuleContext decl) {
        for (var as : GlslAstHelpers.collectAll(decl, GLSLParser.Array_specifierContext.class)) {
            if (GlslAstHelpers.enclosingOfType(as, GLSLParser.InitializerContext.class) != null) continue;
            if (as.getText().replace(" ", "").contains("[]")) return true;
        }
        return false;
    }

    private static boolean hasStorageQualifier(GLSLParser.Type_qualifierContext tq) {
        for (var sq : tq.single_type_qualifier()) {
            if (sq.storage_qualifier() != null) return true;
        }
        return false;
    }

    private static String joinTerminals(ParseTree node) {
        final StringBuilder sb = new StringBuilder();
        joinTerminalsWalk(node, sb);
        return sb.toString();
    }

    private static void joinTerminalsWalk(ParseTree node, StringBuilder sb) {
        if (node instanceof TerminalNode tn) {
            if (sb.length() > 0) sb.append(' ');
            sb.append(tn.getText());
            return;
        }
        for (int i = 0; i < node.getChildCount(); i++) joinTerminalsWalk(node.getChild(i), sb);
    }

    private static void blankAllTerminals(ParseTree node) {
        if (node instanceof TerminalNode tn) {
            if (tn.getSymbol() instanceof CommonToken ct) ct.setText("");
            return;
        }
        for (int i = 0; i < node.getChildCount(); i++) blankAllTerminals(node.getChild(i));
    }

    private static void renameMainToVgBody(Transformer t) {
        t.mutateTree(tree -> {
            for (var fdef : GlslAstHelpers.collectAll(tree, GLSLParser.Function_definitionContext.class)) {
                final var id = fdef.function_prototype().IDENTIFIER();
                if (id == null) continue;
                if (!"main".equals(id.getText())) continue;
                if (id.getSymbol() instanceof CommonToken ct) {
                    ct.setText("_vg_body");
                }
                return;
            }
        });
    }

    private static void stripFragOutputs(Transformer t) {
        final LinkedHashSet<String> outVars = new LinkedHashSet<>();
        t.mutateTree(tree -> {
            for (var decl : GlslAstHelpers.collectAll(tree, GLSLParser.Single_declarationContext.class)) {
                final var fst = decl.fully_specified_type();
                if (fst == null || fst.type_qualifier() == null) continue;
                if (decl.typeless_declaration() == null || decl.typeless_declaration().IDENTIFIER() == null) continue;
                boolean isOut = false;
                for (var sq : fst.type_qualifier().single_type_qualifier()) {
                    if (sq.storage_qualifier() != null && sq.storage_qualifier().getText().equals("out")) {
                        isOut = true;
                        break;
                    }
                }
                if (isOut) outVars.add(decl.typeless_declaration().IDENTIFIER().getText());
            }
        });
        for (String name : outVars) t.removeVariable(name);
        t.mutateTree(tree -> {
            for (var assign : GlslAstHelpers.collectAll(tree, GLSLParser.Assignment_expressionContext.class)) {
                if (assign.unary_expression() == null) continue;
                final String lhs = assign.unary_expression().getText();
                if (lhs.startsWith("gl_FragData") || lhs.equals("gl_FragColor")) {
                    GlslAstHelpers.deleteStatementContaining(assign);
                }
            }
        });
    }

    private static String buildComputeOutput(GLSLParser.Translation_unitContext computeRoot, RwExtractMode mode, Set<String> writtenImages, Set<String> nonWriteonlyImages, Map<String, ImageDecl> declared, List<AttributeSlot> attrs) {
        final StringBuilder out = new StringBuilder(8192);
        out.append("// _vg_mode: ").append(modeTag(mode)).append('\n');
        out.append("#version 460 core\n");
        switch (mode) {
            case CHUNK -> emitChunkPrelude(out, writtenImages, nonWriteonlyImages, declared, attrs);
            case COMPOSITE_VSH -> emitCompositeVshPrelude(out, writtenImages, nonWriteonlyImages, declared);
            case COMPOSITE_FSH -> emitCompositeFshPrelude(out, writtenImages, nonWriteonlyImages, declared);
        }
        out.append('\n');
        out.append(GlslTransformUtils.getFormattedShader(computeRoot, ""));
        out.append('\n');
        emitDispatchMain(out, mode, attrs);
        return out.toString();
    }

    private static String modeTag(RwExtractMode mode) {
        return switch (mode) {
            case CHUNK -> "chunk";
            case COMPOSITE_VSH -> "composite-vsh";
            case COMPOSITE_FSH -> "composite-fsh";
        };
    }

    private static void emitImageDecls(StringBuilder out, Set<String> writtenImages, Set<String> nonWriteonlyImages, Map<String, ImageDecl> declared) {
        final List<String> sorted = new ArrayList<>(writtenImages);
        Collections.sort(sorted);
        final Map<String, ImageInformation> infos = activeCustomImages;
        for (int i = 0; i < sorted.size(); i++) {
            final String name = sorted.get(i);
            final ImageDecl d = declared.get(name);
            if (d == null) continue;
            final InternalTextureFormat fmt = (infos != null && infos.get(name) != null) ? infos.get(name).internalTextureFormat() : null;
            final String layoutInner = (fmt != null) ? "binding = " + i + ", " + fmt.name().toLowerCase(Locale.ROOT) : "binding = " + i;
            final String qualifier = nonWriteonlyImages.contains(name) ? "" : "writeonly ";
            out.append("layout(").append(layoutInner).append(") ").append(qualifier).append("uniform ").append(d.glslType()).append(' ').append(name).append(";\n");
        }
    }

    private static void emitChunkPrelude(StringBuilder out, Set<String> writtenImages, Set<String> nonWriteonlyImages, Map<String, ImageDecl> declared, List<AttributeSlot> attrs) {
        out.append("layout(local_size_x = 64) in;\n\n");
        emitImageDecls(out, writtenImages, nonWriteonlyImages, declared);
        out.append("\nuniform int _vg_startVertex;\n");
        out.append("uniform int _vg_vertexCount;\n\n");
        out.append("layout(std430, binding = ").append(VG_VBUF_SSBO_BINDING).append(") readonly buffer _VgVbuf { uint data[]; } _vg_vbuf;\n\n");
        out.append("vec4 _vg_sink_pos;\n");
        out.append("float _vg_sink_psize;\n");
        out.append("int _vg_id_global;\n");
        out.append("#define gl_Position _vg_sink_pos\n");
        out.append("#define gl_PointSize _vg_sink_psize\n");
        out.append("#define gl_VertexID _vg_id_global\n");
        out.append("#define gl_VertexIndex _vg_id_global\n");
        out.append("#define gl_InstanceID 0\n");
        out.append("#define gl_InstanceIndex 0\n\n");
        emitAttributeGlobals(out, attrs);
        out.append('\n');
        out.append("int _vg_si8(uint v) { return int(v << 24) >> 24; }\n\n");
        out.append("void _vg_unpack(uint id) {\n");
        out.append("    uint base = id * ").append(STRIDE_UINTS).append("u;\n");
        emitAttributeAssignments(out, attrs);
        out.append("}\n\n");
        out.append("void _vert_init() {}\n");
    }

    private static void emitCompositeVshPrelude(StringBuilder out, Set<String> writtenImages, Set<String> nonWriteonlyImages, Map<String, ImageDecl> declared) {
        out.append("layout(local_size_x = 64) in;\n\n");
        emitImageDecls(out, writtenImages, nonWriteonlyImages, declared);
        out.append("\nconst vec4 _vg_quad[4] = vec4[4](\n");
        out.append("    vec4(1.0, 1.0, 0.0, 1.0),\n");
        out.append("    vec4(0.0, 1.0, 0.0, 1.0),\n");
        out.append("    vec4(1.0, 0.0, 0.0, 1.0),\n");
        out.append("    vec4(0.0, 0.0, 0.0, 1.0));\n");
        out.append("vec4 _vg_gl_vertex;\n");
        out.append("int _vg_gl_vertex_id;\n");
        out.append("#define gl_Vertex _vg_gl_vertex\n");
        out.append("#define gl_VertexID _vg_gl_vertex_id\n");
        out.append("uniform mat4 gl_ModelViewProjectionMatrix;\n");
        out.append("vec4 iris_ftransform() { return gl_ModelViewProjectionMatrix * gl_Vertex; }\n");
    }

    private static void emitCompositeFshPrelude(StringBuilder out, Set<String> writtenImages, Set<String> nonWriteonlyImages, Map<String, ImageDecl> declared) {
        out.append("layout(local_size_x = 8, local_size_y = 8, local_size_z = 1) in;\n\n");
        emitImageDecls(out, writtenImages, nonWriteonlyImages, declared);
        out.append("\nuniform ivec2 _vg_target_size;\n");
        out.append("vec4 _vg_gl_fragcoord_raw;\n");
        out.append("#define gl_FragCoord _vg_gl_fragcoord_raw\n");
    }

    private static void emitVertexDecode(StringBuilder out, List<AttributeSlot> attrs) {
        final Set<String> present = new HashSet<>();
        for (AttributeSlot slot : attrs) present.add(slot.name());

        out.append("#ifdef USE_VERTEX_COMPRESSION\n");
        if (present.contains("a_PosId")) {
            out.append("    _vert_position = vec3(a_PosId.xyz) * VERT_POS_SCALE + VERT_POS_OFFSET;\n");
            out.append("    _draw_id = (a_PosId.w >> 8u) & 0xFFu;\n");
            out.append("    _material_params = (a_PosId.w >> 0u) & 0xFFu;\n");
        }
        if (present.contains("a_TexCoord")) out.append("    _vert_tex_diffuse_coord = a_TexCoord * VERT_TEX_SCALE;\n");
        if (present.contains("a_LightCoord")) out.append("    _vert_tex_light_coord = a_LightCoord;\n");
        if (present.contains("a_Color")) out.append("    _vert_color = a_Color;\n");
        out.append("#else\n");
        if (present.contains("a_PosId")) out.append("    _vert_position = a_PosId;\n");
        if (present.contains("a_TexCoord")) out.append("    _vert_tex_diffuse_coord = a_TexCoord;\n");
        if (present.contains("a_Color")) out.append("    _vert_color = a_Color;\n");
        if (present.contains("a_LightCoord")) {
            out.append("    uint _vg_draw_params = a_LightCoord & 0xFFFFu;\n");
            out.append("    _material_params = _vg_draw_params & 0xFFu;\n");
            out.append("    _draw_id = (_vg_draw_params >> 8) & 0xFFu;\n");
            out.append("    _vert_tex_light_coord = ivec2((uvec2((a_LightCoord >> 16) & 0xFFFFu) >> uvec2(0, 8)) & uvec2(0xFFu));\n");
        }
        out.append("#endif\n");
    }

    private static void emitDispatchMain(StringBuilder out, RwExtractMode mode, List<AttributeSlot> attrs) {
        out.append("void main() {\n");
        switch (mode) {
            case CHUNK -> {
                out.append("    int id = _vg_startVertex + int(gl_GlobalInvocationID.x);\n");
                out.append("    if (id >= _vg_startVertex + _vg_vertexCount) return;\n");
                out.append("    _vg_id_global = id;\n");
                out.append("    _vg_unpack(uint(id));\n");
                emitVertexDecode(out, attrs);
                out.append("    _vg_body();\n");
            }
            case COMPOSITE_VSH -> {
                out.append("    uint vid = gl_GlobalInvocationID.x;\n");
                out.append("    if (vid >= 4u) return;\n");
                out.append("    _vg_gl_vertex = _vg_quad[vid];\n");
                out.append("    _vg_gl_vertex_id = int(vid);\n");
                out.append("    _vg_body();\n");
            }
            case COMPOSITE_FSH -> {
                out.append("    ivec2 px = ivec2(gl_GlobalInvocationID.xy);\n");
                out.append("    if (px.x >= _vg_target_size.x || px.y >= _vg_target_size.y) return;\n");
                out.append("    _vg_gl_fragcoord_raw = vec4(vec2(px) + 0.5, 0.0, 1.0);\n");
                out.append("    _vg_body();\n");
            }
        }
        out.append("}\n");
    }

    private static List<AttributeSlot> collectVertexAttributes(Set<String> chunkAttrsUsed, Map<String, String> chunkAttrAstTypes) {
        final List<AttributeSlot> out = new ArrayList<>();
        for (String name : chunkAttrsUsed) {
            final Map<String, AttributeSlot> variants = CHUNK_VERTEX_ATTRS.get(name);
            if (variants == null) continue;
            final String astType = chunkAttrAstTypes.get(name);
            if (astType != null) {
                final AttributeSlot pinned = variants.get(astType);
                if (pinned != null) {
                    out.add(pinned);
                    continue;
                }
            }
            out.addAll(variants.values());
        }
        return out;
    }

    private static Map<String, String> collectChunkAttrAstTypes(List<GLSLParser.Single_declarationContext> decls) {
        final LinkedHashMap<String, String> out = new LinkedHashMap<>();
        for (var decl : decls) {
            if (decl.typeless_declaration() == null || decl.typeless_declaration().IDENTIFIER() == null) continue;
            final String name = decl.typeless_declaration().IDENTIFIER().getText();
            if (!CHUNK_VERTEX_ATTRS.containsKey(name)) continue;
            if (decl.fully_specified_type() == null || decl.fully_specified_type().type_specifier() == null) continue;
            final String typeText = decl.fully_specified_type().type_specifier().getText();
            out.putIfAbsent(name, typeText);
        }
        return out;
    }

    private static void emitAttributeGlobals(StringBuilder out, List<AttributeSlot> attrs) {
        final LinkedHashMap<String, List<AttributeSlot>> byName = groupByName(attrs);
        for (var e : byName.entrySet()) {
            final List<AttributeSlot> variants = e.getValue();
            if (variants.size() == 1) {
                final AttributeSlot v = variants.get(0);
                out.append(v.glslType()).append(' ').append(v.name()).append(";\n");
            } else {
                final AttributeSlot live = pickLive(variants);
                final AttributeSlot dead = pickDead(variants, live);
                out.append("#ifdef USE_VERTEX_COMPRESSION\n");
                out.append(dead.glslType()).append(' ').append(dead.name()).append(";\n");
                out.append("#else\n");
                out.append(live.glslType()).append(' ').append(live.name()).append(";\n");
                out.append("#endif\n");
            }
        }
    }

    private static void emitAttributeAssignments(StringBuilder out, List<AttributeSlot> attrs) {
        final LinkedHashMap<String, List<AttributeSlot>> byName = groupByName(attrs);
        for (var e : byName.entrySet()) {
            final List<AttributeSlot> variants = e.getValue();
            if (variants.size() == 1) {
                final AttributeSlot v = variants.get(0);
                out.append("    ").append(v.name()).append(" = ").append(v.unpackExpr()).append(";\n");
            } else {
                final AttributeSlot live = pickLive(variants);
                final AttributeSlot dead = pickDead(variants, live);
                out.append("#ifdef USE_VERTEX_COMPRESSION\n");
                out.append("    ").append(dead.name()).append(" = ").append(dead.unpackExpr()).append(";\n");
                out.append("#else\n");
                out.append("    ").append(live.name()).append(" = ").append(live.unpackExpr()).append(";\n");
                out.append("#endif\n");
            }
        }
    }

    private static LinkedHashMap<String, List<AttributeSlot>> groupByName(List<AttributeSlot> attrs) {
        final LinkedHashMap<String, List<AttributeSlot>> out = new LinkedHashMap<>();
        for (AttributeSlot a : attrs) out.computeIfAbsent(a.name(), k -> new ArrayList<>()).add(a);
        return out;
    }

    private static AttributeSlot pickLive(List<AttributeSlot> variants) {
        for (AttributeSlot v : variants) if (v.liveBranch()) return v;
        return variants.get(0);
    }

    private static AttributeSlot pickDead(List<AttributeSlot> variants, AttributeSlot live) {
        for (AttributeSlot v : variants) if (v != live) return v;
        return variants.get(0);
    }

    static final Map<String, Map<String, AttributeSlot>> CHUNK_VERTEX_ATTRS = buildAttrTable();

    private static Map<String, Map<String, AttributeSlot>> buildAttrTable() {
        final LinkedHashMap<String, LinkedHashMap<String, AttributeSlot>> m = new LinkedHashMap<>();

        addAttr(m, new AttributeSlot("iris_Vertex", "vec4", 0,
            "vec4(uintBitsToFloat(_vg_vbuf.data[base + 0u]), uintBitsToFloat(_vg_vbuf.data[base + 1u]), uintBitsToFloat(_vg_vbuf.data[base + 2u]), 1.0)"));
        addAttr(m, new AttributeSlot("iris_Color", "vec4", 12,
            "vec4(float(_vg_vbuf.data[base + 3u] & 0xFFu), float((_vg_vbuf.data[base + 3u] >> 8) & 0xFFu), float((_vg_vbuf.data[base + 3u] >> 16) & 0xFFu), float((_vg_vbuf.data[base + 3u] >> 24) & 0xFFu)) * (1.0 / 255.0)"));
        addAttr(m, new AttributeSlot("iris_MultiTexCoord0", "vec4", 16,
            "vec4(uintBitsToFloat(_vg_vbuf.data[base + 4u]), uintBitsToFloat(_vg_vbuf.data[base + 5u]), 0.0, 1.0)"));
        addAttr(m, new AttributeSlot("iris_MultiTexCoord1", "vec4", 24,
            "vec4(float(_vg_vbuf.data[base + 6u] & 0xFFFFu) * (1.0/16.0), float((_vg_vbuf.data[base + 6u] >> 16) & 0xFFFFu) * (1.0/16.0), 0.0, 1.0)"));

        addAttr(m, new AttributeSlot("mc_midTexCoord", "vec4", 28,
            "vec4(float(_vg_vbuf.data[base + 7u] & 0xFFFFu), float((_vg_vbuf.data[base + 7u] >> 16) & 0xFFFFu), 0.0, 1.0)"));
        addAttr(m, new AttributeSlot("mc_midTexCoord", "vec2", 28,
            "vec2(float(_vg_vbuf.data[base + 7u] & 0xFFFFu), float((_vg_vbuf.data[base + 7u] >> 16) & 0xFFFFu))"));

        addAttr(m, new AttributeSlot("at_tangent", "vec4", 32,
            "vec4(float(_vg_si8(_vg_vbuf.data[base + 8u])) * (1.0/127.0), float(_vg_si8(_vg_vbuf.data[base + 8u] >> 8)) * (1.0/127.0), float(_vg_si8(_vg_vbuf.data[base + 8u] >> 16)) * (1.0/127.0), float(_vg_si8(_vg_vbuf.data[base + 8u] >> 24)) * (1.0/127.0))"));

        addAttr(m, new AttributeSlot("iris_Normal", "vec3", 36,
            "vec3(float(_vg_si8(_vg_vbuf.data[base + 9u])) * (1.0/127.0), float(_vg_si8(_vg_vbuf.data[base + 9u] >> 8)) * (1.0/127.0), float(_vg_si8(_vg_vbuf.data[base + 9u] >> 16)) * (1.0/127.0))"));

        addAttr(m, new AttributeSlot("mc_Entity", "vec4", 40,
            "vec4(float(_vg_vbuf.data[base + 10u]), 0.0, 0.0, 0.0)"));
        addAttr(m, new AttributeSlot("mc_Entity", "uint", 40,
            "_vg_vbuf.data[base + 10u]"));

        addAttr(m, new AttributeSlot("at_midBlock", "vec4", 44,
            "vec4(float(_vg_si8(_vg_vbuf.data[base + 11u])), float(_vg_si8(_vg_vbuf.data[base + 11u] >> 8)), float(_vg_si8(_vg_vbuf.data[base + 11u] >> 16)), float(_vg_si8(_vg_vbuf.data[base + 11u] >> 24)))"));

        addAttr(m, new AttributeSlot("a_PosId", "vec3", 0,
            "vec3(uintBitsToFloat(_vg_vbuf.data[base + 0u]), uintBitsToFloat(_vg_vbuf.data[base + 1u]), uintBitsToFloat(_vg_vbuf.data[base + 2u]))"));
        addAttr(m, new AttributeSlot("a_Color", "vec4", 12,
            "vec4(float(_vg_vbuf.data[base + 3u] & 0xFFu), float((_vg_vbuf.data[base + 3u] >> 8) & 0xFFu), float((_vg_vbuf.data[base + 3u] >> 16) & 0xFFu), float((_vg_vbuf.data[base + 3u] >> 24) & 0xFFu)) * (1.0 / 255.0)"));
        addAttr(m, new AttributeSlot("a_TexCoord", "vec2", 16,
            "vec2(uintBitsToFloat(_vg_vbuf.data[base + 4u]), uintBitsToFloat(_vg_vbuf.data[base + 5u]))"));
        addAttr(m, new AttributeSlot("a_LightCoord", "uint", 24,
            "_vg_vbuf.data[base + 6u]"));

        addAttr(m, new AttributeSlot("a_PosId", "uvec4", 0,
                "uvec4(0u, 0u, 0u, 0u)", false));
        addAttr(m, new AttributeSlot("a_LightCoord", "ivec2", 24,
                "ivec2(0, 0)", false));

        final LinkedHashMap<String, Map<String, AttributeSlot>> immutable = new LinkedHashMap<>();
        for (var e : m.entrySet()) immutable.put(e.getKey(), Map.copyOf(e.getValue()));
        return Map.copyOf(immutable);
    }

    private static void addAttr(LinkedHashMap<String, LinkedHashMap<String, AttributeSlot>> m, AttributeSlot slot) {
        m.computeIfAbsent(slot.name(), _ -> new LinkedHashMap<>()).put(slot.glslType(), slot);
    }
}
