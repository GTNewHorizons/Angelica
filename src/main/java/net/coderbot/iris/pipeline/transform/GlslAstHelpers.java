package net.coderbot.iris.pipeline.transform;

import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.tree.ParseTree;
import org.taumc.glsl.ShaderParser;
import org.taumc.glsl.grammar.GLSLParser;

import java.util.ArrayList;
import java.util.List;

final class GlslAstHelpers {

    private GlslAstHelpers() {}

    @SuppressWarnings("unchecked")
    static <T extends ParseTree> List<T> collectAll(ParseTree root, Class<T> klass) {
        final List<T> out = new ArrayList<>();
        walk(root, klass, out);
        return out;
    }

    @SuppressWarnings("unchecked")
    static <T extends ParseTree> void walk(ParseTree node, Class<T> klass, List<T> out) {
        if (klass.isInstance(node)) out.add((T) node);
        for (int i = 0, n = node.getChildCount(); i < n; i++) {
            walk(node.getChild(i), klass, out);
        }
    }

    @SuppressWarnings("unchecked")
    static <T extends ParserRuleContext> T enclosingOfType(ParseTree node, Class<T> type) {
        ParseTree cur = node;
        while (cur != null && !type.isInstance(cur)) cur = cur.getParent();
        return (T) cur;
    }

    static void collectInto(ParseTree root, List<? extends ParseTree>[] sinks, Class<?>[] types) {
        if (types.length != sinks.length) throw new IllegalArgumentException();
        collectIntoWalk(root, sinks, types);
    }

    @SuppressWarnings("unchecked")
    private static void collectIntoWalk(ParseTree node, List<? extends ParseTree>[] sinks, Class<?>[] types) {
        for (int i = 0; i < types.length; i++) {
            if (types[i].isInstance(node)) ((List<ParseTree>) sinks[i]).add(node);
        }
        for (int i = 0, n = node.getChildCount(); i < n; i++) collectIntoWalk(node.getChild(i), sinks, types);
    }

    static String extractCallName(GLSLParser.Postfix_expressionContext expr) {
        if (expr.function_call_parameters() == null) return null;
        if (expr.postfix_expression() == null) {
            return expr.type_specifier() != null ? expr.type_specifier().getText() : null;
        }
        final GLSLParser.Postfix_expressionContext inner = expr.postfix_expression();
        if (inner.primary_expression() == null) return null;
        if (inner.primary_expression().variable_identifier() == null) return null;
        return inner.primary_expression().variable_identifier().getText();
    }

    static String firstArgIdentifier(GLSLParser.Postfix_expressionContext expr) {
        final List<GLSLParser.Assignment_expressionContext> args = argsOf(expr);
        if (args.isEmpty()) return "";
        return args.get(0).getText();
    }

    static String secondArgText(GLSLParser.Postfix_expressionContext expr) {
        final List<GLSLParser.Assignment_expressionContext> args = argsOf(expr);
        if (args.size() < 2) return null;
        return args.get(1).getText();
    }

    static List<GLSLParser.Assignment_expressionContext> argsOf(GLSLParser.Postfix_expressionContext expr) {
        final GLSLParser.Function_call_parametersContext params = expr.function_call_parameters();
        if (params == null) return List.of();
        return params.assignment_expression();
    }

    static void replaceNode(ParseTree oldNode, ParseTree newNode) {
        if (!(oldNode instanceof ParserRuleContext oldCtx)) return;
        if (!(newNode instanceof ParserRuleContext newCtx)) return;
        if (!(oldCtx.parent instanceof ParserRuleContext parentCtx)) return;
        final int idx = parentCtx.children.indexOf(oldCtx);
        if (idx < 0) return;
        newCtx.parent = parentCtx;
        parentCtx.children.set(idx, newCtx);
    }

    static boolean deleteStatementContaining(ParseTree expr) {
        final GLSLParser.StatementContext stmt = enclosingOfType(expr, GLSLParser.StatementContext.class);
        if (stmt == null) return false;
        if (!(stmt.parent instanceof ParserRuleContext parent) || parent.children == null) return false;
        final var empty = ShaderParser.parseSnippet(";", GLSLParser::statement);
        if (empty == null) return parent.children.remove(stmt);
        final int idx = parent.children.indexOf(stmt);
        if (idx < 0) return false;
        empty.parent = parent;
        parent.children.set(idx, empty);
        return true;
    }

    static String zeroLiteralFor(String imageGlslType) {
        return switch (componentTypeFor(imageGlslType)) {
            case "ivec4" -> "ivec4(0)";
            case "uvec4" -> "uvec4(0u)";
            default -> "vec4(0.0)";
        };
    }

    static String componentTypeFor(String imageGlslType) {
        if (imageGlslType == null) return "vec4";
        if (imageGlslType.startsWith("iimage")) return "ivec4";
        if (imageGlslType.startsWith("uimage")) return "uvec4";
        return "vec4";
    }
}
