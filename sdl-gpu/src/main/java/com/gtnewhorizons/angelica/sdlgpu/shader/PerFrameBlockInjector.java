package com.gtnewhorizons.angelica.sdlgpu.shader;

import com.gtnewhorizons.angelica.glsm.hooks.PerFrameUniformBlock;
import com.gtnewhorizons.angelica.glsm.hooks.PerFrameUniformBlock.Member;
import com.gtnewhorizons.angelica.glsm.shader.GlslVulkanPreprocess.Edit;
import com.gtnewhorizons.angelica.glsm.shader.ShaderIndex;
import com.gtnewhorizons.angelica.glsm.shader.UniformType;
import org.taumc.glsl.grammar.GLSLParser;

import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.List;
import java.util.Set;


public final class PerFrameBlockInjector {

    private static final String SHADOWED_PREFIX = "angelica_pfb_unused_";

    private PerFrameBlockInjector() {}

    public static void collectEdits(ShaderIndex index, PerFrameUniformBlock perFrame, PerFrameUniformBlock perPass, List<Edit> out) {
        final PerFrameUniformBlock[] blocks = new PerFrameUniformBlock[ShaderManager.BLOCK_COUNT];
        blocks[ShaderManager.BLOCK_PER_FRAME] = normalize(perFrame);
        blocks[ShaderManager.BLOCK_PER_PASS] = normalize(perPass);
        if (blocks[0] == null && blocks[1] == null) return;

        final int insertAt = firstDeclarationStart(index.root());
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
        classifyDeclarations(index, memberTypes, memberBlock, stripEdits, stripBlocks, shadowed);

        final Set<String> referenced = new HashSet<>();
        for (String name : memberTypes.keySet()) {
            if (!shadowed.contains(name) && !index.occurrences(name).isEmpty()) referenced.add(name);
        }
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

    private static void classifyDeclarations(ShaderIndex index, Map<String, String> memberTypes, Object2IntOpenHashMap<String> memberBlock, List<Edit> stripEdits, IntArrayList stripBlocks, Set<String> shadowed) {
        for (GLSLParser.DeclarationContext ctx : index.globalDeclarations()) {
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
        if (!ShaderIndex.hasStorageQualifier(fst.type_qualifier(), "uniform")) return false;
        if (fst.type_specifier() == null || fst.type_specifier().type_specifier_nonarray() == null) return false;
        final String declaredType = fst.type_specifier().type_specifier_nonarray().getText();

        for (String name : declared) {
            if (!declaredType.equals(memberTypes.get(name))) return false;
        }
        return true;
    }

    private static List<String> declaredNames(GLSLParser.DeclarationContext ctx) {
        final GLSLParser.Init_declarator_listContext idl = ctx.init_declarator_list();
        if (idl == null || idl.single_declaration() == null) return List.of();
        final List<String> out = new ArrayList<>(2);
        for (GLSLParser.Typeless_declarationContext td : ShaderIndex.declarators(idl)) {
            if (td.IDENTIFIER() != null) out.add(td.IDENTIFIER().getText());
        }
        return out;
    }

}
