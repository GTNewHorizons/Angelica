package com.gtnewhorizons.angelica.sdlgpu.shader;

import com.gtnewhorizons.angelica.glsm.shader.GlslVulkanPreprocess;
import com.gtnewhorizons.angelica.glsm.shader.GlslVulkanPreprocess.Edit;
import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.tree.ParseTree;
import org.antlr.v4.runtime.tree.ParseTreeWalker;
import org.jetbrains.annotations.Nullable;
import org.taumc.glsl.grammar.GLSLLexer;
import org.taumc.glsl.grammar.GLSLParser;
import org.taumc.glsl.grammar.GLSLParserBaseListener;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.gtnewhorizons.angelica.glsm.shader.GlslVulkanPreprocess.startIdx;
import static com.gtnewhorizons.angelica.glsm.shader.GlslVulkanPreprocess.stopIdx;

/**
 * Moves {@code dFdx}/{@code dFdy}/{@code fwidth} calls in front of the {@code if} blocks around them, so every section of
 * the 2x2 quad computes them. Packs take derivatives inside branches that only part of a quad enters, which GLSL
 * leaves undefined. ACO writes {@code src - src[lane0]} in the active lanes only, then reads lane 1/2 with
 * fetch-inactive, so a neighbor that skipped the branch hands back a stale register, often NaN!
 * This was observed on Euphoria Patches and this aims to fix this wherever possible on Vulkan.
 */
public final class DerivativeHoisting {

    private static final String PREFIX = "angelica_derivative_";

    private static final Set<String> DERIVATIVES = Set.of(
        "dFdx", "dFdy", "fwidth",
        "dFdxFine", "dFdyFine", "fwidthFine",
        "dFdxCoarse", "dFdyCoarse", "fwidthCoarse");

    private static final Set<String> BUILTINS_WITH_OUT_PARAMS = Set.of(
        "modf", "frexp", "uaddCarry", "usubBorrow", "umulExtended", "imulExtended");

    private DerivativeHoisting() {}

    private record Declared(String type, boolean global) {}

    private record Operand(String name, @Nullable String swizzle) {}

    private record Functions(Map<String, Set<Integer>> outParams, Map<String, Set<String>> globalWrites) {}

    private static final String UNKNOWN = "*";

    public static void collectEdits(GLSLParser.Translation_unitContext root, String source, List<Edit> edits) {
        final List<GLSLParser.Postfix_expressionContext> calls = new ArrayList<>();
        final List<GLSLParser.Function_definitionContext> definitions = new ArrayList<>();
        final Map<String, Set<Integer>> outParams = new HashMap<>();
        final Map<String, Set<String>> globalWrites = new HashMap<>();

        ParseTreeWalker.DEFAULT.walk(new GLSLParserBaseListener() {
            @Override
            public void enterFunction_prototype(GLSLParser.Function_prototypeContext ctx) {
                if (ctx.IDENTIFIER() == null) return;
                final String name = ctx.IDENTIFIER().getText();
                globalWrites.computeIfAbsent(name, k -> new HashSet<>());
                if (ctx.function_parameters() == null) return;
                final List<GLSLParser.Parameter_declarationContext> params = ctx.function_parameters().parameter_declaration();
                for (int i = 0; i < params.size(); i++) {
                    if (writesParameter(params.get(i))) outParams.computeIfAbsent(name, k -> new HashSet<>()).add(i);
                }
            }

            @Override
            public void enterFunction_definition(GLSLParser.Function_definitionContext ctx) {
                if (ctx.function_prototype().IDENTIFIER() != null) definitions.add(ctx);
            }

            @Override
            public void enterPostfix_expression(GLSLParser.Postfix_expressionContext ctx) {
                if (ctx.LEFT_PAREN() != null && DERIVATIVES.contains(calleeName(ctx))) calls.add(ctx);
            }
        }, root);

        if (calls.isEmpty()) return;

        final Functions functions = new Functions(outParams, globalWrites);
        summarizeGlobalWrites(definitions, functions);

        final List<Edit> earlierEdits = List.copyOf(edits);
        final Map<GLSLParser.Selection_statementContext, StringBuilder> declarations = new LinkedHashMap<>();
        final Map<String, String> hoisted = new HashMap<>();

        for (GLSLParser.Postfix_expressionContext call : calls) {
            final GLSLParser.Function_call_parametersContext params = call.function_call_parameters();
            if (params == null || params.assignment_expression().size() != 1) continue;
            final GLSLParser.Assignment_expressionContext arg = params.assignment_expression(0);
            final Operand operand = operand(arg);
            if (operand == null) continue;

            final Declared declared = declaredType(call, operand.name(), root);
            if (declared == null) continue;
            final String type = resultType(declared.type(), operand.swizzle());
            if (type == null) continue;

            final GLSLParser.Selection_statementContext target = outermostSafeIf(call, operand.name(), declared.global(), functions);
            if (target == null) continue;

            final String callee = calleeName(call);
            final String argText = sliceWithEdits(source, startIdx(arg), stopIdx(arg), earlierEdits);
            final String key = startIdx(target) + "|" + callee + "|" + argText;
            String variable = hoisted.get(key);
            if (variable == null) {
                variable = PREFIX + hoisted.size();
                hoisted.put(key, variable);
                declarations.computeIfAbsent(target, k -> new StringBuilder())
                    .append(type).append(' ').append(variable).append(" = ").append(callee).append('(').append(argText).append(");\n")
                    .append(indentBefore(source, startIdx(target)));
            }
            edits.add(new Edit(startIdx(call), stopIdx(call), variable));
        }

        for (Map.Entry<GLSLParser.Selection_statementContext, StringBuilder> e : declarations.entrySet()) {
            final int at = startIdx(e.getKey());
            edits.add(new Edit(at, at - 1, e.getValue().toString()));
        }
    }

    private static String calleeName(GLSLParser.Postfix_expressionContext call) {
        final ParseTree callee = call.getChild(0);
        return callee instanceof ParserRuleContext ? callee.getText() : "";
    }

    private static boolean writesParameter(GLSLParser.Parameter_declarationContext param) {
        if (param.type_qualifier() == null) return false;
        for (GLSLParser.Single_type_qualifierContext q : param.type_qualifier().single_type_qualifier()) {
            final GLSLParser.Storage_qualifierContext s = q.storage_qualifier();
            if (s != null && (s.OUT() != null || s.INOUT() != null)) return true;
        }
        return false;
    }

    private static @Nullable Operand operand(GLSLParser.Assignment_expressionContext arg) {
        if (arg.assignment_operator() != null) return null;
        final GLSLParser.Constant_expressionContext c = arg.constant_expression();
        if (c == null || c.QUESTION() != null) return null;
        final GLSLParser.Unary_expressionContext u = c.binary_expression().unary_expression();
        if (u == null || u.postfix_expression() == null) return null;
        final GLSLParser.Postfix_expressionContext p = u.postfix_expression();

        final String name = variableName(p);
        if (name != null) return new Operand(name, null);
        if (p.DOT() == null || p.field_selection() == null || p.field_selection().variable_identifier() == null) return null;
        final String base = variableName(p.postfix_expression());
        return base == null ? null : new Operand(base, p.field_selection().variable_identifier().getText());
    }

    private static @Nullable String variableName(@Nullable GLSLParser.Postfix_expressionContext p) {
        if (p == null || p.primary_expression() == null || p.primary_expression().variable_identifier() == null) return null;
        return p.primary_expression().variable_identifier().getText();
    }

    private static @Nullable String resultType(String declaredType, @Nullable String swizzle) {
        final int size = switch (declaredType) {
            case "float" -> 1;
            case "vec2" -> 2;
            case "vec3" -> 3;
            case "vec4" -> 4;
            default -> 0;
        };
        if (size == 0) return null;
        if (swizzle == null) return declaredType;
        if (size == 1 || swizzle.isEmpty() || swizzle.length() > 4) return null;
        for (String set : new String[] { "xyzw", "rgba", "stpq" }) {
            boolean all = true;
            for (int i = 0; i < swizzle.length(); i++) {
                final int component = set.indexOf(swizzle.charAt(i));
                if (component < 0 || component >= size) { all = false; break; }
            }
            if (all) return swizzle.length() == 1 ? "float" : "vec" + swizzle.length();
        }
        return null;
    }

    private static @Nullable Declared declaredType(ParserRuleContext from, String name, GLSLParser.Translation_unitContext root) {
        ParseTree child = from;
        for (ParserRuleContext p = from.getParent(); p != null; child = p, p = p.getParent()) {
            if (p instanceof GLSLParser.Statement_listContext list) {
                for (int i = list.children.indexOf(child) - 1; i >= 0; i--) {
                    final String type = typeOf(list.getChild(i), name);
                    if (type != null) return new Declared(type, false);
                }
            } else if (p instanceof GLSLParser.Iteration_statementContext loop && loop.for_init_statement() != null && child != loop.for_init_statement()) {
                final String type = typeOf(loop.for_init_statement(), name);
                if (type != null) return new Declared(type, false);
            } else if (p instanceof GLSLParser.Function_definitionContext function) {
                final GLSLParser.Function_parametersContext params = function.function_prototype().function_parameters();
                if (params != null) {
                    for (GLSLParser.Parameter_declarationContext param : params.parameter_declaration()) {
                        final GLSLParser.Parameter_declaratorContext d = param.parameter_declarator();
                        if (d != null && d.IDENTIFIER() != null && name.equals(d.IDENTIFIER().getText())) {
                            return new Declared(d.array_specifier() == null ? baseType(d.type_specifier()) : "", false);
                        }
                    }
                }
                break;
            }
        }
        for (GLSLParser.External_declarationContext external : root.external_declaration()) {
            if (external.declaration() == null) continue;
            final String type = typeOf(external.declaration(), name);
            if (type != null) return new Declared(type, true);
        }
        return null;
    }

    private static @Nullable String typeOf(ParseTree node, String name) {
        final GLSLParser.Init_declarator_listContext list = firstDeclaratorList(node);
        if (list == null || list.single_declaration() == null) return null;
        final GLSLParser.Single_declarationContext single = list.single_declaration();
        final List<GLSLParser.Typeless_declarationContext> declarators = new ArrayList<>();
        if (single.typeless_declaration() != null) declarators.add(single.typeless_declaration());
        declarators.addAll(list.typeless_declaration());
        for (GLSLParser.Typeless_declarationContext d : declarators) {
            if (d.IDENTIFIER() == null || !name.equals(d.IDENTIFIER().getText())) continue;
            if (d.array_specifier() != null) return "";
            return baseType(single.fully_specified_type().type_specifier());
        }
        return null;
    }

    private static GLSLParser.@Nullable Init_declarator_listContext firstDeclaratorList(ParseTree node) {
        ParseTree n = node;
        while (n != null) {
            if (n instanceof GLSLParser.Init_declarator_listContext list) return list;
            if (n instanceof GLSLParser.Simple_statementContext s) n = s.declaration_statement();
            else if (n instanceof GLSLParser.StatementContext s) n = s.simple_statement();
            else if (n instanceof GLSLParser.For_init_statementContext f) n = f.declaration_statement();
            else if (n instanceof GLSLParser.Declaration_statementContext d) n = d.declaration();
            else if (n instanceof GLSLParser.DeclarationContext d) n = d.init_declarator_list();
            else return null;
        }
        return null;
    }

    private static String baseType(GLSLParser.Type_specifierContext type) {
        if (type == null || type.array_specifier() != null || type.type_specifier_nonarray() == null) return "";
        return type.type_specifier_nonarray().getText();
    }

    private static GLSLParser.@Nullable Selection_statementContext outermostSafeIf(ParserRuleContext call, String name, boolean global, Functions functions) {
        final List<GLSLParser.Selection_statementContext> enclosing = new ArrayList<>();
        for (ParserRuleContext p = call.getParent(); p != null; p = p.getParent()) {
            if (p instanceof GLSLParser.Selection_rest_statementContext rest) {
                enclosing.add((GLSLParser.Selection_statementContext) rest.getParent());
            } else if (p instanceof GLSLParser.Iteration_statementContext || p instanceof GLSLParser.Switch_statementContext
                || p instanceof GLSLParser.Function_definitionContext) {
                break;
            }
        }
        for (int i = enclosing.size() - 1; i >= 0; i--) {
            final GLSLParser.Selection_statementContext candidate = enclosing.get(i);
            if (!inStatementList(candidate)) continue;
            if (declares(candidate, name) || writes(candidate, name, global, functions)) continue;
            return candidate;
        }
        return null;
    }

    private static boolean inStatementList(GLSLParser.Selection_statementContext s) {
        return s.getParent() instanceof GLSLParser.Simple_statementContext simple
            && simple.getParent() instanceof GLSLParser.StatementContext statement
            && statement.getParent() instanceof GLSLParser.Statement_listContext list
            && !(list.getParent() instanceof GLSLParser.Switch_statementContext);
    }

    private static boolean declares(ParseTree node, String name) {
        if (node instanceof GLSLParser.Typeless_declarationContext d) {
            return d.IDENTIFIER() != null && name.equals(d.IDENTIFIER().getText());
        }
        if (node instanceof GLSLParser.ConditionContext c && c.IDENTIFIER() != null && name.equals(c.IDENTIFIER().getText())) {
            return true;
        }
        for (int i = 0; i < node.getChildCount(); i++) {
            if (declares(node.getChild(i), name)) return true;
        }
        return false;
    }

    private static boolean writes(ParseTree node, String name, boolean global, Functions functions) {
        final Set<String> written = new HashSet<>();
        final Set<String> called = new HashSet<>();
        collectEffects(node, functions, written, called);
        if (written.contains(name) || written.contains(UNKNOWN)) return true;
        if (!global) return false;
        for (String callee : called) {
            final Set<String> w = functions.globalWrites().get(callee);
            if (w.contains(name) || w.contains(UNKNOWN)) return true;
        }
        return false;
    }

    private static void summarizeGlobalWrites(List<GLSLParser.Function_definitionContext> definitions, Functions functions) {
        final Map<String, Set<String>> callees = new HashMap<>();
        for (GLSLParser.Function_definitionContext definition : definitions) {
            final String name = definition.function_prototype().IDENTIFIER().getText();
            final Set<String> written = new HashSet<>();
            final Set<String> called = new HashSet<>();
            collectEffects(definition.compound_statement_no_new_scope(), functions, written, called);
            final GLSLParser.Function_parametersContext params = definition.function_prototype().function_parameters();
            if (params != null) {
                for (GLSLParser.Parameter_declarationContext param : params.parameter_declaration()) {
                    final GLSLParser.Parameter_declaratorContext d = param.parameter_declarator();
                    if (d != null && d.IDENTIFIER() != null) written.remove(d.IDENTIFIER().getText());
                }
            }
            functions.globalWrites().get(name).addAll(written);
            callees.computeIfAbsent(name, k -> new HashSet<>()).addAll(called);
        }
        boolean changed = true;
        while (changed) {
            changed = false;
            for (Map.Entry<String, Set<String>> e : callees.entrySet()) {
                final Set<String> into = functions.globalWrites().get(e.getKey());
                for (String callee : e.getValue()) {
                    changed |= into.addAll(functions.globalWrites().get(callee));
                }
            }
        }
    }

    private static void collectEffects(ParseTree node, Functions functions, Set<String> written, Set<String> called) {
        if (node instanceof GLSLParser.Assignment_expressionContext a && a.assignment_operator() != null) {
            written.add(lvalueName(a.unary_expression()));
        } else if (node instanceof GLSLParser.Unary_expressionContext u && (u.INC_OP() != null || u.DEC_OP() != null)) {
            written.add(lvalueName(u.unary_expression()));
        } else if (node instanceof GLSLParser.Postfix_expressionContext p) {
            if (p.INC_OP() != null || p.DEC_OP() != null) written.add(lvalueName(p.postfix_expression()));
            if (p.LEFT_PAREN() != null) {
                final String callee = calleeName(p);
                if (functions.globalWrites().containsKey(callee)) called.add(callee);
                if (p.function_call_parameters() != null) {
                    final List<GLSLParser.Assignment_expressionContext> args = p.function_call_parameters().assignment_expression();
                    final Set<Integer> positions = functions.outParams().get(callee);
                    final boolean builtin = BUILTINS_WITH_OUT_PARAMS.contains(callee);
                    for (int i = 0; i < args.size(); i++) {
                        if (builtin) {
                            final String root = rootName(args.get(i));
                            if (root != null) written.add(root);
                        } else if (positions != null && positions.contains(i)) {
                            written.add(lvalueName(args.get(i)));
                        }
                    }
                }
            }
        }
        for (int i = 0; i < node.getChildCount(); i++) {
            collectEffects(node.getChild(i), functions, written, called);
        }
    }

    private static String lvalueName(@Nullable ParserRuleContext lvalue) {
        final String root = rootName(lvalue);
        return root != null ? root : UNKNOWN;
    }

    private static @Nullable String rootName(@Nullable ParserRuleContext expression) {
        if (expression == null) return null;
        final Token start = expression.getStart();
        return start != null && start.getType() == GLSLLexer.IDENTIFIER ? start.getText() : null;
    }

    private static String indentBefore(String source, int at) {
        int lineStart = at;
        while (lineStart > 0 && (source.charAt(lineStart - 1) == ' ' || source.charAt(lineStart - 1) == '\t')) lineStart--;
        return source.substring(lineStart, at);
    }

    private static String sliceWithEdits(String source, int start, int stop, List<Edit> edits) {
        final List<Edit> inside = new ArrayList<>();
        for (Edit e : edits) {
            if (e.startIdx() >= start && e.startIdx() <= stop && e.stopIdx() <= stop) {
                inside.add(new Edit(e.startIdx() - start, e.stopIdx() - start, e.replacement()));
            }
        }
        return GlslVulkanPreprocess.applyEdits(source.substring(start, stop + 1), inside);
    }
}
