package com.gtnewhorizons.angelica.sdlgpu.shader;

import com.gtnewhorizons.angelica.glsm.shader.GlslVulkanPreprocess;
import com.gtnewhorizons.angelica.glsm.shader.GlslVulkanPreprocess.Edit;
import com.gtnewhorizons.angelica.glsm.shader.ShaderIndex;
import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.tree.ParseTree;
import org.jetbrains.annotations.Nullable;
import org.taumc.glsl.grammar.GLSLLexer;
import org.taumc.glsl.grammar.GLSLParser;

import java.util.ArrayList;
import java.util.Comparator;
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

    private static final Map<String, Set<Integer>> BUILTIN_OUT_PARAMS = Map.of(
        "modf", Set.of(1),
        "frexp", Set.of(1),
        "uaddCarry", Set.of(2),
        "usubBorrow", Set.of(2),
        "umulExtended", Set.of(2, 3),
        "imulExtended", Set.of(2, 3));

    private static final String UNKNOWN = "*";

    private DerivativeHoisting() {}

    private record Declared(String type, boolean global) {}

    private record Operand(String name, @Nullable String swizzle) {}

    private record DerivativeCall(GLSLParser.Postfix_expressionContext call, String callee) {}

    private record Effects(Set<String> declared, Set<String> written, Set<String> called) {
        Effects() {
            this(new HashSet<>(), new HashSet<>(), new HashSet<>());
        }
    }

    public static void collectEdits(ShaderIndex index, List<Edit> edits) {
        final List<DerivativeCall> calls = new ArrayList<>();
        for (String callee : DERIVATIVES) {
            for (GLSLParser.Postfix_expressionContext call : index.callsTo(callee)) calls.add(new DerivativeCall(call, callee));
        }
        if (calls.isEmpty()) return;
        calls.sort(Comparator.comparingInt(c -> startIdx(c.call())));

        final String source = index.source();
        final GLSLParser.Translation_unitContext root = index.root();
        final Functions functions = new Functions(index);
        final int earlierEditCount = edits.size();
        final Map<GLSLParser.Selection_statementContext, Effects> ifEffects = new HashMap<>();
        final Map<GLSLParser.Selection_statementContext, StringBuilder> declarations = new LinkedHashMap<>();
        final Map<String, String> hoisted = new HashMap<>();
        boolean checkedDirectives = false;

        for (DerivativeCall derivative : calls) {
            final GLSLParser.Postfix_expressionContext call = derivative.call();
            final GLSLParser.Function_call_parametersContext params = call.function_call_parameters();
            if (params == null || params.assignment_expression().size() != 1) continue;
            final GLSLParser.Assignment_expressionContext arg = params.assignment_expression(0);
            final Operand operand = operand(arg);
            if (operand == null) continue;

            final Declared declared = declaredType(call, operand.name(), root);
            if (declared == null) continue;
            final String type = resultType(declared.type(), operand.swizzle());
            if (type == null) continue;

            final GLSLParser.Selection_statementContext target = outermostSafeIf(call, operand.name(), declared.global(), functions, ifEffects);
            if (target == null) continue;
            if (!checkedDirectives) {
                if (index.directives().hasConditionalOrMacro()) return;
                checkedDirectives = true;
            }

            final String callee = derivative.callee();
            final String argText = sliceWithEdits(source, startIdx(arg), stopIdx(arg), edits.subList(0, earlierEditCount));
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

    private static final class Functions {
        final Map<String, Set<Integer>> outParams = new HashMap<>();
        final Set<String> names = new HashSet<>();
        final Map<GLSLParser.Postfix_expressionContext, String> calleeNames = new HashMap<>();
        private final List<GLSLParser.Function_definitionContext> definitions = new ArrayList<>();
        private @Nullable Map<String, Set<String>> globalWrites;

        Functions(ShaderIndex index) {
            for (GLSLParser.External_declarationContext external : index.root().external_declaration()) {
                final GLSLParser.Function_prototypeContext prototype;
                if (external.function_definition() != null) {
                    definitions.add(external.function_definition());
                    prototype = external.function_definition().function_prototype();
                } else if (external.declaration() != null) {
                    prototype = external.declaration().function_prototype();
                } else {
                    prototype = null;
                }
                if (prototype == null || prototype.IDENTIFIER() == null) continue;
                final String name = prototype.IDENTIFIER().getText();
                names.add(name);
                final List<GLSLParser.Parameter_declarationContext> params = parameters(prototype);
                for (int i = 0; i < params.size(); i++) {
                    final GLSLParser.Type_qualifierContext q = params.get(i).type_qualifier();
                    if (ShaderIndex.hasStorageQualifier(q, "out") || ShaderIndex.hasStorageQualifier(q, "inout")) {
                        outParams.computeIfAbsent(name, k -> new HashSet<>()).add(i);
                    }
                }
            }
            for (String name : names) addCalls(index, name);
            for (String name : BUILTIN_OUT_PARAMS.keySet()) addCalls(index, name);
        }

        private void addCalls(ShaderIndex index, String name) {
            for (GLSLParser.Postfix_expressionContext call : index.callsTo(name)) calleeNames.put(call, name);
        }

        Set<String> globalWrites(String function) {
            if (globalWrites == null) globalWrites = summarize();
            return globalWrites.getOrDefault(function, Set.of());
        }

        private Map<String, Set<String>> summarize() {
            final Map<String, Set<String>> writes = new HashMap<>();
            final Map<String, Set<String>> callees = new HashMap<>();
            for (GLSLParser.Function_definitionContext definition : definitions) {
                final String name = definition.function_prototype().IDENTIFIER().getText();
                final Effects effects = new Effects();
                collectEffects(definition.compound_statement_no_new_scope(), this, effects);
                for (GLSLParser.Parameter_declarationContext param : parameters(definition.function_prototype())) {
                    final String parameter = parameterName(param);
                    if (parameter != null) effects.written().remove(parameter);
                }
                writes.computeIfAbsent(name, k -> new HashSet<>()).addAll(effects.written());
                callees.computeIfAbsent(name, k -> new HashSet<>()).addAll(effects.called());
            }
            boolean changed = true;
            while (changed) {
                changed = false;
                for (Map.Entry<String, Set<String>> e : callees.entrySet()) {
                    final Set<String> into = writes.get(e.getKey());
                    for (String callee : e.getValue()) {
                        final Set<String> from = writes.get(callee);
                        if (from != null) changed |= into.addAll(from);
                    }
                }
            }
            return writes;
        }
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
                for (GLSLParser.Parameter_declarationContext param : parameters(function.function_prototype())) {
                    if (name.equals(parameterName(param))) {
                        final GLSLParser.Parameter_declaratorContext d = param.parameter_declarator();
                        return new Declared(d.array_specifier() == null ? baseType(d.type_specifier()) : "", false);
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
        for (GLSLParser.Typeless_declarationContext d : ShaderIndex.declarators(list)) {
            if (d.IDENTIFIER() == null || !name.equals(d.IDENTIFIER().getText())) continue;
            if (d.array_specifier() != null) return "";
            return baseType(list.single_declaration().fully_specified_type().type_specifier());
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

    private static List<GLSLParser.Parameter_declarationContext> parameters(GLSLParser.Function_prototypeContext prototype) {
        final GLSLParser.Function_parametersContext params = prototype.function_parameters();
        return params == null ? List.of() : params.parameter_declaration();
    }

    private static @Nullable String parameterName(GLSLParser.Parameter_declarationContext param) {
        final GLSLParser.Parameter_declaratorContext d = param.parameter_declarator();
        return d == null || d.IDENTIFIER() == null ? null : d.IDENTIFIER().getText();
    }

    private static GLSLParser.@Nullable Selection_statementContext outermostSafeIf(ParserRuleContext call, String name, boolean global, Functions functions,
                                                                                   Map<GLSLParser.Selection_statementContext, Effects> ifEffects) {
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
            Effects effects = ifEffects.get(candidate);
            if (effects == null) {
                effects = new Effects();
                collectEffects(candidate, functions, effects);
                ifEffects.put(candidate, effects);
            }
            if (effects.declared().contains(name) || writes(effects, name, global, functions)) continue;
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

    private static boolean writes(Effects effects, String name, boolean global, Functions functions) {
        if (effects.written().contains(name) || effects.written().contains(UNKNOWN)) return true;
        if (!global) return false;
        for (String callee : effects.called()) {
            final Set<String> w = functions.globalWrites(callee);
            if (w.contains(name) || w.contains(UNKNOWN)) return true;
        }
        return false;
    }

    private static void collectEffects(ParseTree node, Functions functions, Effects effects) {
        final Set<String> written = effects.written();
        if (node instanceof GLSLParser.Typeless_declarationContext d && d.IDENTIFIER() != null) {
            effects.declared().add(d.IDENTIFIER().getText());
        } else if (node instanceof GLSLParser.ConditionContext c && c.IDENTIFIER() != null) {
            effects.declared().add(c.IDENTIFIER().getText());
        } else if (node instanceof GLSLParser.Assignment_expressionContext a && a.assignment_operator() != null) {
            written.add(lvalueName(a.unary_expression()));
        } else if (node instanceof GLSLParser.Unary_expressionContext u && (u.INC_OP() != null || u.DEC_OP() != null)) {
            written.add(lvalueName(u.unary_expression()));
        } else if (node instanceof GLSLParser.Postfix_expressionContext p) {
            if (p.INC_OP() != null || p.DEC_OP() != null) written.add(lvalueName(p.postfix_expression()));
            final String callee = functions.calleeNames.get(p);
            if (callee != null) {
                if (functions.names.contains(callee)) effects.called().add(callee);
                Set<Integer> positions = functions.outParams.get(callee);
                if (positions == null) positions = BUILTIN_OUT_PARAMS.get(callee);
                if (positions != null && p.function_call_parameters() != null) {
                    final List<GLSLParser.Assignment_expressionContext> args = p.function_call_parameters().assignment_expression();
                    for (int i : positions) {
                        if (i < args.size()) written.add(lvalueName(args.get(i)));
                    }
                }
            }
        }
        for (int i = 0; i < node.getChildCount(); i++) {
            collectEffects(node.getChild(i), functions, effects);
        }
    }

    private static String lvalueName(@Nullable ParserRuleContext lvalue) {
        if (lvalue == null) return UNKNOWN;
        final Token start = lvalue.getStart();
        return start != null && start.getType() == GLSLLexer.IDENTIFIER ? start.getText() : UNKNOWN;
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
