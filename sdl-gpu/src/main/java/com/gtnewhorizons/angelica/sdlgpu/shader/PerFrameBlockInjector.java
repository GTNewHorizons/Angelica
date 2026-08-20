package com.gtnewhorizons.angelica.sdlgpu.shader;

import com.gtnewhorizons.angelica.glsm.GlslTransformUtils;
import com.gtnewhorizons.angelica.glsm.hooks.PerFrameUniformBlock;
import com.gtnewhorizons.angelica.glsm.hooks.PerFrameUniformBlock.Member;
import com.gtnewhorizons.angelica.glsm.shader.GlslVulkanPreprocess.Edit;
import com.gtnewhorizons.angelica.glsm.shader.UniformType;
import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.tree.ParseTreeWalker;
import org.antlr.v4.runtime.tree.TerminalNode;
import org.taumc.glsl.grammar.GLSLLexer;
import org.taumc.glsl.grammar.GLSLParser;
import org.taumc.glsl.grammar.GLSLParserBaseListener;

import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.List;
import java.util.Set;

import static com.gtnewhorizons.angelica.glsm.shader.GlslVulkanPreprocess.applyEdits;

public final class PerFrameBlockInjector {

    private static final String SHADOWED_PREFIX = "angelica_pfb_unused_";

    private PerFrameBlockInjector() {}

    public static String inject(String source, PerFrameUniformBlock perFrame, PerFrameUniformBlock perPass) {
        final GLSLParser.Translation_unitContext root;
        try {
            root = GlslTransformUtils.parseFullQuiet(source);
        } catch (Exception e) {
            return source;
        }

        final List<Edit> edits = new ArrayList<>();
        collectEdits(root, perFrame, perPass, edits);
        return edits.isEmpty() ? source : applyEdits(source, edits);
    }

    public static void collectEdits(GLSLParser.Translation_unitContext root, PerFrameUniformBlock perFrame, PerFrameUniformBlock perPass, List<Edit> out) {
        final PerFrameUniformBlock[] blocks = new PerFrameUniformBlock[ShaderManager.BLOCK_COUNT];
        blocks[ShaderManager.BLOCK_PER_FRAME] = normalize(perFrame);
        blocks[ShaderManager.BLOCK_PER_PASS] = normalize(perPass);
        if (blocks[0] == null && blocks[1] == null) return;

        final int insertAt = firstDeclarationStart(root);
        if (insertAt < 0) return;

        final Map<String, String> memberTypes = new LinkedHashMap<>();
        final Object2IntOpenHashMap<String> memberBlock = new Object2IntOpenHashMap<>();
        memberBlock.defaultReturnValue(-1);
        for (int b = 0; b < blocks.length; b++) {
            if (blocks[b] == null) continue;
            for (Member m : blocks[b].members()) {
                memberTypes.put(m.name(), std140Type(m.type()));
                memberBlock.put(m.name(), b);
            }
        }

        final List<Edit> stripEdits = new ArrayList<>();
        final IntArrayList stripBlocks = new IntArrayList();
        final Set<String> shadowed = new HashSet<>();
        classifyDeclarations(root, memberTypes, memberBlock, stripEdits, stripBlocks, shadowed);

        final Set<String> referenced = referencedMembers(root, memberTypes.keySet(), shadowed);
        final boolean[] injected = new boolean[blocks.length];
        boolean anyInjected = false;
        for (int b = 0; b < blocks.length; b++) {
            if (blocks[b] == null) continue;
            for (Member m : blocks[b].members()) {
                if (referenced.contains(m.name())) { injected[b] = true; anyInjected = true; break; }
            }
        }
        if (!anyInjected) return;

        final StringBuilder blockDecls = new StringBuilder(128);
        for (int b = 0; b < blocks.length; b++) {
            if (injected[b]) appendBlockText(blockDecls, b, blocks[b].members(), shadowed);
        }
        out.add(new Edit(insertAt, insertAt - 1, blockDecls.toString()));
        for (int i = 0; i < stripEdits.size(); i++) {
            if (injected[stripBlocks.getInt(i)]) out.add(stripEdits.get(i));
        }
    }

    private static PerFrameUniformBlock normalize(PerFrameUniformBlock block) {
        return (block == null || block.isEmpty()) ? null : block;
    }

    static String std140Type(UniformType type) {
        return switch (type) {
            case FLOAT -> "float";
            case INT, BOOL -> "int";
            case VEC2 -> "vec2";
            case VEC3 -> "vec3";
            case VEC4 -> "vec4";
            case VEC2I -> "ivec2";
            case VEC3I -> "ivec3";
            case VEC4I -> "ivec4";
            case MAT3 -> "mat3";
            case MAT4 -> "mat4";
        };
    }

    private static void appendBlockText(StringBuilder sb, int blockIndex, List<Member> members, Set<String> shadowed) {
        sb.append("layout(std140, binding = ").append(ShaderManager.BLOCK_BINDINGS[blockIndex])
            .append(") readonly buffer ").append(ShaderManager.BLOCK_NAMES[blockIndex]).append(" {\n");
        for (Member m : members) {
            final String name = shadowed.contains(m.name()) ? SHADOWED_PREFIX + m.name() : m.name();
            sb.append("    ").append(std140Type(m.type())).append(' ').append(name).append(";\n");
        }
        sb.append("};\n");
    }

    private static int firstDeclarationStart(GLSLParser.Translation_unitContext root) {
        final List<GLSLParser.External_declarationContext> decls = root.external_declaration();
        return (decls == null || decls.isEmpty()) ? -1 : decls.get(0).getStart().getStartIndex();
    }

    private static void classifyDeclarations(GLSLParser.Translation_unitContext root, Map<String, String> memberTypes, Object2IntOpenHashMap<String> memberBlock, List<Edit> stripEdits, IntArrayList stripBlocks, Set<String> shadowed) {
        for (GLSLParser.External_declarationContext ed : root.external_declaration()) {
            final GLSLParser.DeclarationContext ctx = ed.declaration();
            if (ctx == null) continue;

            final List<String> declared = declaredNames(ctx);
            if (declared.isEmpty()) continue;

            int block = -1;
            for (String name : declared) {
                final int b = memberBlock.getInt(name);
                if (b >= 0) { block = b; break; }
            }
            if (block < 0) continue;

            if (isReplaceableUniform(ctx, declared, memberTypes)) {
                stripEdits.add(new Edit(ctx.getStart().getStartIndex(), ctx.getStop().getStopIndex(), ""));
                stripBlocks.add(block);
                continue;
            }

            for (String name : declared) {
                if (memberTypes.containsKey(name)) shadowed.add(name);
            }
        }
    }

    private static boolean isReplaceableUniform(GLSLParser.DeclarationContext ctx, List<String> declared, Map<String, String> memberTypes) {
        final GLSLParser.Init_declarator_listContext idl = ctx.init_declarator_list();
        if (idl == null) return false;
        final GLSLParser.Single_declarationContext single = idl.single_declaration();
        if (single == null || single.fully_specified_type() == null) return false;
        final GLSLParser.Fully_specified_typeContext fst = single.fully_specified_type();
        if (fst.type_qualifier() == null) return false;

        boolean hasUniform = false;
        for (GLSLParser.Single_type_qualifierContext stq : fst.type_qualifier().single_type_qualifier()) {
            if (stq.storage_qualifier() != null && "uniform".equals(stq.storage_qualifier().getText())) {
                hasUniform = true;
                break;
            }
        }
        if (!hasUniform) return false;
        if (fst.type_specifier() == null || fst.type_specifier().type_specifier_nonarray() == null) return false;
        final String declaredType = fst.type_specifier().type_specifier_nonarray().getText();

        for (String name : declared) {
            if (!declaredType.equals(memberTypes.get(name))) return false;
        }
        return true;
    }

    private static Set<String> referencedMembers(GLSLParser.Translation_unitContext root, Set<String> memberNames, Set<String> shadowed) {
        final Set<String> referenced = new HashSet<>();
        ParseTreeWalker.DEFAULT.walk(new GLSLParserBaseListener() {
            @Override
            public void visitTerminal(TerminalNode node) {
                final Token tok = node.getSymbol();
                if (tok.getType() != GLSLLexer.IDENTIFIER) return;
                final String name = tok.getText();
                if (memberNames.contains(name) && !shadowed.contains(name)) referenced.add(name);
            }
        }, root);
        return referenced;
    }

    private static List<String> declaredNames(GLSLParser.DeclarationContext ctx) {
        final GLSLParser.Init_declarator_listContext idl = ctx.init_declarator_list();
        if (idl == null) return List.of();
        final GLSLParser.Single_declarationContext single = idl.single_declaration();
        if (single == null) return List.of();
        final List<String> out = new ArrayList<>(2);
        if (single.typeless_declaration() != null && single.typeless_declaration().IDENTIFIER() != null) {
            out.add(single.typeless_declaration().IDENTIFIER().getText());
        }
        for (GLSLParser.Typeless_declarationContext td : idl.typeless_declaration()) {
            if (td.IDENTIFIER() != null) out.add(td.IDENTIFIER().getText());
        }
        return out;
    }

}
