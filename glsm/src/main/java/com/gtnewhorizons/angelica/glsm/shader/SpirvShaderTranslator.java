package com.gtnewhorizons.angelica.glsm.shader;

import com.gtnewhorizons.angelica.glsm.GlslTransformUtils;
import com.gtnewhorizons.angelica.glsm.shader.GlslVulkanPreprocess.Edit;
import me.eigenraven.lwjgl3ify.api.Lwjgl3Aware;
import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.tree.ParseTree;
import org.antlr.v4.runtime.tree.ParseTreeWalker;
import org.antlr.v4.runtime.tree.TerminalNode;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.PointerBuffer;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL32;
import org.lwjgl.opengl.GL40;
import org.lwjgl.opengl.GL43;
import org.lwjgl.util.shaderc.Shaderc;
import org.lwjgl.util.spvc.Spvc;
import org.lwjgl.util.spvc.SpvcReflectedResource;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;
import org.taumc.glsl.grammar.GLSLParser;
import org.taumc.glsl.grammar.GLSLParserBaseListener;

import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static com.gtnewhorizons.angelica.glsm.shader.GlslVulkanPreprocess.startIdx;
import static com.gtnewhorizons.angelica.glsm.shader.GlslVulkanPreprocess.stopIdx;
import static org.lwjgl.system.MemoryStack.stackPush;
import static org.lwjgl.system.MemoryUtil.memFree;

/** GLSL 330 core -> SPIR-V -> GLSL ES 320 via shaderc + SPIRV-Cross. Returns null on failure. */
@Lwjgl3Aware
public final class SpirvShaderTranslator {

    private static final Logger LOGGER = LogManager.getLogger("SpirvShaderTranslator");

    // SpvDecoration ordinals (SPIR-V spec). spvc_compiler_unset_decoration takes this as an int.
    private static final int SPV_DECORATION_LOCATION = 30;
    private static final int SPV_DECORATION_BINDING = 33;

    private SpirvShaderTranslator() {}

    public static @Nullable String glslToGlslEs(String source, int glShaderType, String debugName) {
        return glslToGlslEsImpl(source, glShaderType, debugName, false);
    }

    /** Test-only: bypass {@link #postProcessEsOutput}. */
    static @Nullable String glslToGlslEsRawForTest(String source, int glShaderType, String debugName) {
        return glslToGlslEsImpl(source, glShaderType, debugName, true);
    }

    private static @Nullable String glslToGlslEsImpl(String source, int glShaderType, String debugName, boolean rawOutput) {
        final int shaderKind = shaderKindForGlType(glShaderType);
        if (shaderKind < 0) {
            LOGGER.warn("Unknown GL shader type 0x{} for '{}' - skipping SPIR-V translation", Integer.toHexString(glShaderType), debugName);
            return null;
        }

        final GlslVulkanPreprocess.Result pre = GlslVulkanPreprocess.run(source, glShaderType, debugName);
        if (pre == null) return null;

        final SpirvCompiler.Result cr = SpirvCompiler.compile(pre.rewrittenSource(), shaderKind, debugName,
                SpirvCompiler.Options.vulkanRelaxed());
        if (cr.spirv() == null) {
            final String firstErr = cr.error() == null ? "(no error)" : cr.error().split("\\R", 2)[0];
            LOGGER.error("shaderc failed for '{}' (kind=0x{}): {} (dumped to {})",
                    debugName, Integer.toHexString(shaderKind), firstErr, cr.dumpPath() == null ? "<dump failed>" : cr.dumpPath());
            return null;
        }
        try {
            final String emitted = emitGlslEs(cr.spirv(), debugName, glShaderType, pre.explicitVsInputs());
            if (emitted == null) return null;
            return rawOutput ? emitted : postProcessEsOutput(emitted, pre.boolUniforms());
        } finally {
            memFree(cr.spirv());
        }
    }

    // Post-process the raw SPIRV-Cross output via AST-guided text edits:
    //   - unwrap shaderc's synthetic `_RESERVED_IDENTIFIER_FIXUP_*DefaultUniformBlock` struct
    //     back to loose uniforms (neither EMIT_UNIFORM_BUFFER_AS_PLAIN_UNIFORMS nor flatten_buffer_block remove it)
    //   - restore `uniform bool X` where SPIR-V flattened `bool` to `uint` (`glUniform1i` rejects uint)
    private static @Nullable String postProcessEsOutput(String glslEs, Set<String> boolUniformNames) {
        final GLSLParser.Translation_unitContext root;
        try {
            root = GlslTransformUtils.parseFullQuiet(glslEs);
        } catch (Exception e) {
            LOGGER.error("Failed to parse spvc ES output for post-processing: {}", e.getMessage());
            return null;
        }

        final DefaultBlockInfo block = findDefaultBlock(root);
        final List<Edit> edits = new ArrayList<>();

        if (block != null) {
            edits.add(new Edit(startIdx(block.structExternalDecl), stopIdx(block.structExternalDecl), buildLooseUniforms(block.members, boolUniformNames)));
            edits.add(new Edit(startIdx(block.uniformExternalDecl), stopIdx(block.uniformExternalDecl), ""));
        }

        final UseSiteRewriter rewriter = new UseSiteRewriter(block, boolUniformNames, edits);
        ParseTreeWalker.DEFAULT.walk(rewriter, root);

        return GlslVulkanPreprocess.applyEdits(glslEs, edits);
    }

    private record DefaultBlockInfo(
            ParserRuleContext structExternalDecl,
            ParserRuleContext uniformExternalDecl,
            String blockVarName,
            List<MemberInfo> members) {}

    private record MemberInfo(String typePrefix, String name) {}

    private static String buildLooseUniforms(List<MemberInfo> members, Set<String> boolNames) {
        final StringBuilder sb = new StringBuilder();
        for (MemberInfo m : members) {
            if (boolNames.contains(m.name)) {
                sb.append("uniform bool ").append(m.name).append(";\n");
            } else {
                sb.append("uniform ").append(m.typePrefix).append(' ').append(m.name).append(";\n");
            }
        }
        if (!sb.isEmpty() && sb.charAt(sb.length() - 1) == '\n') sb.deleteCharAt(sb.length() - 1);
        return sb.toString();
    }

    private static @Nullable DefaultBlockInfo findDefaultBlock(GLSLParser.Translation_unitContext root) {
        final DefaultBlockFinder finder = new DefaultBlockFinder();
        ParseTreeWalker.DEFAULT.walk(finder, root);
        if (finder.structExternalDecl == null || finder.uniformExternalDecl == null) return null;
        return new DefaultBlockInfo(finder.structExternalDecl, finder.uniformExternalDecl, finder.blockVarName, finder.members);
    }

    private static final class DefaultBlockFinder extends GLSLParserBaseListener {
        String structName;
        ParserRuleContext structExternalDecl;
        ParserRuleContext uniformExternalDecl;
        String blockVarName;
        final List<MemberInfo> members = new ArrayList<>();

        @Override
        public void enterStruct_specifier(GLSLParser.Struct_specifierContext ctx) {
            if (structExternalDecl != null) return;
            final TerminalNode id = ctx.IDENTIFIER();
            if (id == null) return;
            final String idText = id.getText();
            if (!idText.startsWith("_RESERVED_IDENTIFIER_FIXUP_") || !idText.endsWith("DefaultUniformBlock")) return;

            structName = idText;
            structExternalDecl = findExternalDeclaration(ctx);

            for (GLSLParser.Struct_declarationContext sd : ctx.struct_declaration_list().struct_declaration()) {
                final StringBuilder typePrefix = new StringBuilder();
                if (sd.type_qualifier() != null) typePrefix.append(textWithSpaces(sd.type_qualifier())).append(' ');
                typePrefix.append(textWithSpaces(sd.type_specifier()));

                for (GLSLParser.Struct_declaratorContext decl : sd.struct_declarator_list().struct_declarator()) {
                    final String arraySuffix = decl.array_specifier() == null ? "" : decl.array_specifier().getText();
                    members.add(new MemberInfo(typePrefix + arraySuffix, decl.IDENTIFIER().getText()));
                }
            }
        }

        @Override
        public void enterInit_declarator_list(GLSLParser.Init_declarator_listContext ctx) {
            if (structName == null || uniformExternalDecl != null) return;
            final GLSLParser.Single_declarationContext single = ctx.single_declaration();
            if (single == null || single.typeless_declaration() == null) return;
            final GLSLParser.Fully_specified_typeContext fst = single.fully_specified_type();
            if (fst == null || fst.type_qualifier() == null) return;

            boolean isUniform = false;
            for (GLSLParser.Single_type_qualifierContext stq : fst.type_qualifier().single_type_qualifier()) {
                if (stq.storage_qualifier() != null && "uniform".equals(stq.storage_qualifier().getText())) {
                    isUniform = true;
                    break;
                }
            }
            if (!isUniform) return;

            if (fst.type_specifier() == null || fst.type_specifier().type_specifier_nonarray() == null) return;
            final GLSLParser.Type_specifier_nonarrayContext ts = fst.type_specifier().type_specifier_nonarray();
            if (ts.type_name() == null) return;
            if (!structName.equals(ts.type_name().getText())) return;

            blockVarName = single.typeless_declaration().IDENTIFIER().getText();
            uniformExternalDecl = findExternalDeclaration(ctx);
        }

        private static String textWithSpaces(ParseTree tree) {
            final StringBuilder sb = new StringBuilder();
            appendTextWithSpaces(tree, sb);
            return sb.toString().trim();
        }

        private static void appendTextWithSpaces(ParseTree tree, StringBuilder sb) {
            if (tree instanceof TerminalNode) {
                if (!sb.isEmpty() && sb.charAt(sb.length() - 1) != ' ') sb.append(' ');
                sb.append(tree.getText());
                return;
            }
            for (int i = 0; i < tree.getChildCount(); i++) {
                appendTextWithSpaces(tree.getChild(i), sb);
            }
        }
    }

    private static ParserRuleContext findExternalDeclaration(ParserRuleContext ctx) {
        ParseTree cur = ctx;
        while (cur != null && !(cur instanceof GLSLParser.External_declarationContext)) {
            cur = cur.getParent();
        }
        return (ParserRuleContext) cur;
    }

    /** Rewrites bool use sites the SPIR-V flattening produced:
     *  `X != 0u` -> `X`, `X == 0u` -> `!X`, `bool(X)` -> `X`, and the `_NN.X` field-access
     *  variants. Also unwraps bare `_NN.member` into `member` when the block was replaced. */
    private static final class UseSiteRewriter extends GLSLParserBaseListener {
        private final @Nullable DefaultBlockInfo block;
        private final Set<String> boolNames;
        private final List<Edit> edits;
        private final Set<String> memberNames;

        UseSiteRewriter(@Nullable DefaultBlockInfo block, Set<String> boolNames, List<Edit> edits) {
            this.block = block;
            this.boolNames = boolNames;
            this.edits = edits;
            this.memberNames = new HashSet<>();
            if (block != null) for (MemberInfo m : block.members) memberNames.add(m.name);
        }

        @Override
        public void enterBinary_expression(GLSLParser.Binary_expressionContext ctx) {
            if (ctx.getChildCount() != 3) return;
            final ParseTree opNode = ctx.getChild(1);
            if (!(opNode instanceof TerminalNode op)) return;
            final String opText = op.getText();
            final boolean isNe = "!=".equals(opText);
            final boolean isEq = "==".equals(opText);
            if (!isNe && !isEq) return;

            final ParseTree rhs = ctx.getChild(2);
            if (!"0u".equals(rhs.getText())) return;

            final ParseTree lhs = ctx.getChild(0);
            final String nameIfBool = resolveBoolOperand(lhs);
            if (nameIfBool == null) return;

            final String replacement = isNe ? nameIfBool : "!" + nameIfBool;
            edits.add(new Edit(startIdx(ctx), stopIdx(ctx), replacement));
        }

        @Override
        public void enterPostfix_expression(GLSLParser.Postfix_expressionContext ctx) {
            if (ctx.type_specifier() != null && ctx.LEFT_PAREN() != null
                    && "bool".equals(ctx.type_specifier().getText())
                    && ctx.function_call_parameters() != null
                    && ctx.function_call_parameters().assignment_expression() != null
                    && ctx.function_call_parameters().assignment_expression().size() == 1) {
                final String nameIfBool = resolveBoolOperand(ctx.function_call_parameters().assignment_expression(0));
                if (nameIfBool != null) {
                    edits.add(new Edit(startIdx(ctx), stopIdx(ctx), nameIfBool));
                    return;
                }
            }

            if (block != null && ctx.getChildCount() == 3 && ctx.DOT() != null && ctx.field_selection() != null) {
                final ParseTree inner = ctx.getChild(0);
                if (inner.getText().equals(block.blockVarName)) {
                    final String member = ctx.field_selection().getText();
                    if (memberNames.contains(member) && !coveredByAncestorBoolCompare(ctx)) {
                        edits.add(new Edit(startIdx(ctx), stopIdx(ctx), member));
                    }
                }
            }
        }

        /** {@code X} or {@code _NN.X} -> {@code X} iff X is in the bool-uniform set. */
        private @Nullable String resolveBoolOperand(ParseTree operand) {
            final String text = operand.getText();
            if (boolNames.contains(text)) return text;
            if (block != null && text.startsWith(block.blockVarName + ".")) {
                final String member = text.substring(block.blockVarName.length() + 1);
                if (boolNames.contains(member) && memberNames.contains(member)) return member;
            }
            return null;
        }

        private boolean coveredByAncestorBoolCompare(GLSLParser.Postfix_expressionContext ctx) {
            final int start = startIdx(ctx), stop = stopIdx(ctx);
            for (Edit e : edits) {
                if (e.startIdx() <= start && stop <= e.stopIdx() && e.stopIdx() > stop) return true;
            }
            return false;
        }
    }

    private static @Nullable String emitGlslEs(ByteBuffer spirv, String debugName, int glShaderType, Set<String> explicitVsInputs) {
        try (MemoryStack stack = stackPush()) {
            final PointerBuffer pContext = stack.pointers(0);
            if (Spvc.spvc_context_create(pContext) != Spvc.SPVC_SUCCESS) {
                LOGGER.error("spvc_context_create failed for '{}'", debugName);
                return null;
            }
            final long ctx = pContext.get(0);
            try {
                final IntBuffer spirvWords = spirv.asIntBuffer();
                final PointerBuffer pIr = stack.pointers(0);
                if (Spvc.spvc_context_parse_spirv(ctx, spirvWords, spirvWords.remaining(), pIr) != Spvc.SPVC_SUCCESS) {
                    LOGGER.error("spvc_context_parse_spirv failed for '{}': {}", debugName, lastSpvcError(ctx));
                    return null;
                }
                final PointerBuffer pCompiler = stack.pointers(0);
                if (Spvc.spvc_context_create_compiler(ctx, Spvc.SPVC_BACKEND_GLSL, pIr.get(0), Spvc.SPVC_CAPTURE_MODE_TAKE_OWNERSHIP, pCompiler) != Spvc.SPVC_SUCCESS) {
                    LOGGER.error("spvc_context_create_compiler(GLSL) failed for '{}': {}", debugName, lastSpvcError(ctx));
                    return null;
                }
                final long compiler = pCompiler.get(0);

                final PointerBuffer pOptions = stack.pointers(0);
                if (Spvc.spvc_compiler_create_compiler_options(compiler, pOptions) != Spvc.SPVC_SUCCESS) {
                    LOGGER.error("spvc_compiler_create_compiler_options failed for '{}': {}", debugName, lastSpvcError(ctx));
                    return null;
                }
                final long options = pOptions.get(0);
                Spvc.spvc_compiler_options_set_uint(options, Spvc.SPVC_COMPILER_OPTION_GLSL_VERSION, 320);
                Spvc.spvc_compiler_options_set_bool(options, Spvc.SPVC_COMPILER_OPTION_GLSL_ES, true);
                Spvc.spvc_compiler_options_set_bool(options, Spvc.SPVC_COMPILER_OPTION_GLSL_EMIT_UNIFORM_BUFFER_AS_PLAIN_UNIFORMS, true);
                if (Spvc.spvc_compiler_install_compiler_options(compiler, options) != Spvc.SPVC_SUCCESS) {
                    LOGGER.error("spvc_compiler_install_compiler_options failed for '{}': {}", debugName, lastSpvcError(ctx));
                    return null;
                }

                applyResourceDecorationFixups(ctx, compiler, glShaderType, explicitVsInputs, debugName);

                final PointerBuffer pSource = stack.pointers(0);
                if (Spvc.spvc_compiler_compile(compiler, pSource) != Spvc.SPVC_SUCCESS) {
                    LOGGER.error("spvc_compiler_compile failed for '{}': {}", debugName, lastSpvcError(ctx));
                    return null;
                }
                final long sourcePtr = pSource.get(0);
                if (sourcePtr == 0L) {
                    LOGGER.error("spvc_compiler_compile returned NULL source for '{}'", debugName);
                    return null;
                }
                // Copy the spvc-owned buffer before spvc_context_destroy releases it.
                return MemoryUtil.memUTF8(sourcePtr);
            } finally {
                Spvc.spvc_context_destroy(ctx);
            }
        }
    }

    // Strip Location on varyings so ES links by name; strip Binding on UBOs/samplers/images for
    // non-compute so output has no `layout(binding=N)` tail. Compute keeps Binding (SSBOs need it).
    // VS inputs: preserve Location present in source (iris_Vertex etc.); strip the ones shaderc
    // auto-assigned via auto_map_locations (mc_Entity, at_midBlock, ...) so glBindAttribLocation
    // at GL link time takes effect.
    private static void applyResourceDecorationFixups(long ctx, long compiler, int glShaderType, Set<String> explicitVsInputs, String debugName) {
        if (glShaderType == GL20.GL_VERTEX_SHADER) {
            unsetDecorationForResourceType(ctx, compiler, Spvc.SPVC_RESOURCE_TYPE_STAGE_OUTPUT, SPV_DECORATION_LOCATION, null, debugName);
            unsetDecorationForResourceType(ctx, compiler, Spvc.SPVC_RESOURCE_TYPE_STAGE_INPUT, SPV_DECORATION_LOCATION, explicitVsInputs, debugName);
        } else if (glShaderType == GL20.GL_FRAGMENT_SHADER) {
            unsetDecorationForResourceType(ctx, compiler, Spvc.SPVC_RESOURCE_TYPE_STAGE_INPUT, SPV_DECORATION_LOCATION, null, debugName);
        } else if (glShaderType == GL32.GL_GEOMETRY_SHADER || glShaderType == GL40.GL_TESS_CONTROL_SHADER || glShaderType == GL40.GL_TESS_EVALUATION_SHADER) {
            unsetDecorationForResourceType(ctx, compiler, Spvc.SPVC_RESOURCE_TYPE_STAGE_OUTPUT, SPV_DECORATION_LOCATION, null, debugName);
            unsetDecorationForResourceType(ctx, compiler, Spvc.SPVC_RESOURCE_TYPE_STAGE_INPUT, SPV_DECORATION_LOCATION, null, debugName);
        }

        if (glShaderType != GL43.GL_COMPUTE_SHADER) {
            for (int resourceType : BINDING_STRIP_RESOURCE_TYPES) {
                unsetDecorationForResourceType(ctx, compiler, resourceType, SPV_DECORATION_BINDING, null, debugName);
            }
        }
    }

    private static final int[] BINDING_STRIP_RESOURCE_TYPES = {
            Spvc.SPVC_RESOURCE_TYPE_UNIFORM_BUFFER,
            Spvc.SPVC_RESOURCE_TYPE_SAMPLED_IMAGE,
            Spvc.SPVC_RESOURCE_TYPE_SEPARATE_IMAGE,
            Spvc.SPVC_RESOURCE_TYPE_SEPARATE_SAMPLERS,
            Spvc.SPVC_RESOURCE_TYPE_STORAGE_IMAGE,
    };

    /** Unset {@code decoration} on every resource of {@code resourceType}; names in
     *  {@code preserve} (when non-null) keep the decoration. */
    private static void unsetDecorationForResourceType(long ctx, long compiler, int resourceType, int decoration, Set<String> preserve, String debugName) {
        try (MemoryStack stack = stackPush()) {
            final PointerBuffer pResources = stack.pointers(0);
            if (Spvc.spvc_compiler_create_shader_resources(compiler, pResources) != Spvc.SPVC_SUCCESS) {
                LOGGER.warn("spvc_compiler_create_shader_resources failed for '{}': {}", debugName, lastSpvcError(ctx));
                return;
            }
            final long resources = pResources.get(0);

            final PointerBuffer pList = stack.pointers(0);
            final PointerBuffer pCount = stack.pointers(0);
            if (Spvc.spvc_resources_get_resource_list_for_type(resources, resourceType, pList, pCount) != Spvc.SPVC_SUCCESS) {
                LOGGER.warn("spvc_resources_get_resource_list_for_type failed for '{}': {}", debugName, lastSpvcError(ctx));
                return;
            }
            final long listAddr = pList.get(0);
            final long count = pCount.get(0);
            if (listAddr == 0L || count <= 0L) return;

            final SpvcReflectedResource.Buffer list = SpvcReflectedResource.create(listAddr, (int) count);
            for (int i = 0; i < list.remaining(); i++) {
                final SpvcReflectedResource res = list.get(i);
                if (preserve != null) {
                    final String name = res.nameString();
                    if (name != null && preserve.contains(name)) continue;
                }
                Spvc.spvc_compiler_unset_decoration(compiler, res.id(), decoration);
            }
        }
    }

    private static String lastSpvcError(long ctx) {
        final String msg = Spvc.spvc_context_get_last_error_string(ctx);
        return msg == null || msg.isEmpty() ? "(no error message)" : msg;
    }

    private static int shaderKindForGlType(int glShaderType) {
        return switch (glShaderType) {
            case GL20.GL_VERTEX_SHADER -> Shaderc.shaderc_vertex_shader;
            case GL20.GL_FRAGMENT_SHADER -> Shaderc.shaderc_fragment_shader;
            case GL32.GL_GEOMETRY_SHADER -> Shaderc.shaderc_geometry_shader;
            case GL40.GL_TESS_CONTROL_SHADER -> Shaderc.shaderc_tess_control_shader;
            case GL40.GL_TESS_EVALUATION_SHADER -> Shaderc.shaderc_tess_evaluation_shader;
            case GL43.GL_COMPUTE_SHADER -> Shaderc.shaderc_compute_shader;
            default -> -1;
        };
    }
}
