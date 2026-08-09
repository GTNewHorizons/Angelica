package net.coderbot.iris.pipeline.transform;

import org.antlr.v4.runtime.tree.TerminalNode;
import org.taumc.glsl.ShaderParser;
import org.taumc.glsl.Transformer;
import org.taumc.glsl.grammar.GLSLLexer;
import org.taumc.glsl.grammar.GLSLParser;

import java.util.Map;

final class LocalZeroInitTransformer {

    private LocalZeroInitTransformer() {}

    static void transformGrouped(Map<PatchShaderType, Transformer> trees) {
        for (Transformer t : trees.values()) {
            if (t != null) transform(t);
        }
    }

    static void transform(Transformer transformer) {
        transformer.mutateTree(tree -> {
            for (GLSLParser.Declaration_statementContext stmt :
                    GlslAstHelpers.collectAll(tree, GLSLParser.Declaration_statementContext.class)) {
                initializeStatement(stmt);
            }
        });
    }

    private static void initializeStatement(GLSLParser.Declaration_statementContext stmt) {
        final GLSLParser.DeclarationContext declaration = stmt.declaration();
        if (declaration == null) return;
        final GLSLParser.Init_declarator_listContext list = declaration.init_declarator_list();
        if (list == null) return;
        final GLSLParser.Single_declarationContext single = list.single_declaration();
        if (single == null) return;

        final GLSLParser.Fully_specified_typeContext type = single.fully_specified_type();
        if (type == null || type.type_qualifier() != null) return;
        final GLSLParser.Type_specifierContext specifier = type.type_specifier();
        if (specifier == null || specifier.array_specifier() != null) return;

        final String zero = zeroInitializerFor(specifier.type_specifier_nonarray());
        if (zero == null) return;

        initializeDeclarator(single.typeless_declaration(), zero);
        for (GLSLParser.Typeless_declarationContext declarator : list.typeless_declaration()) {
            initializeDeclarator(declarator, zero);
        }
    }

    private static void initializeDeclarator(GLSLParser.Typeless_declarationContext declarator, String zero) {
        if (declarator == null) return;
        if (declarator.initializer() != null || declarator.array_specifier() != null) return;
        final TerminalNode name = declarator.IDENTIFIER();
        if (name == null) return;

        final GLSLParser.Typeless_declarationContext replacement =
            ShaderParser.parseSnippet(name.getText() + " = " + zero, GLSLParser::typeless_declaration);
        if (replacement != null) GlslAstHelpers.replaceNode(declarator, replacement);
    }

    private static String zeroInitializerFor(GLSLParser.Type_specifier_nonarrayContext specifier) {
        if (specifier == null || specifier.getChildCount() != 1) return null;
        if (!(specifier.getChild(0) instanceof TerminalNode token)) return null;
        final String type = switch (token.getSymbol().getType()) {
            case GLSLLexer.FLOAT -> "float";
            case GLSLLexer.INT -> "int";
            case GLSLLexer.UINT -> "uint";
            case GLSLLexer.VEC2 -> "vec2";
            case GLSLLexer.VEC3 -> "vec3";
            case GLSLLexer.VEC4 -> "vec4";
            case GLSLLexer.IVEC2 -> "ivec2";
            case GLSLLexer.IVEC3 -> "ivec3";
            case GLSLLexer.IVEC4 -> "ivec4";
            case GLSLLexer.UVEC2 -> "uvec2";
            case GLSLLexer.UVEC3 -> "uvec3";
            case GLSLLexer.UVEC4 -> "uvec4";
            case GLSLLexer.MAT2 -> "mat2";
            case GLSLLexer.MAT3 -> "mat3";
            case GLSLLexer.MAT4 -> "mat4";
            case GLSLLexer.MAT2X2 -> "mat2x2";
            case GLSLLexer.MAT2X3 -> "mat2x3";
            case GLSLLexer.MAT2X4 -> "mat2x4";
            case GLSLLexer.MAT3X2 -> "mat3x2";
            case GLSLLexer.MAT3X3 -> "mat3x3";
            case GLSLLexer.MAT3X4 -> "mat3x4";
            case GLSLLexer.MAT4X2 -> "mat4x2";
            case GLSLLexer.MAT4X3 -> "mat4x3";
            case GLSLLexer.MAT4X4 -> "mat4x4";
            default -> null;
        };
        return type == null ? null : type + "(0)";
    }
}
