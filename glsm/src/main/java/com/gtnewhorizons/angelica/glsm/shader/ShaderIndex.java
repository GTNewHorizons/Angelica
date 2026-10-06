package com.gtnewhorizons.angelica.glsm.shader;

import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.tree.ParseTree;
import org.antlr.v4.runtime.tree.TerminalNode;
import org.jetbrains.annotations.Nullable;
import org.taumc.glsl.grammar.GLSLLexer;
import org.taumc.glsl.grammar.GLSLParser;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * What the source passes look up in a parsed shader.
 */
public final class ShaderIndex {

    private final GLSLParser.Translation_unitContext root;
    private final Map<String, List<Token>> identifiers = new HashMap<>();
    private final List<GLSLParser.Postfix_expressionContext> calls = new ArrayList<>();
    private final List<GLSLParser.DeclarationContext> globalDeclarations = new ArrayList<>();

    public ShaderIndex(GLSLParser.Translation_unitContext root) {
        this.root = root;
        collect(root);
        for (GLSLParser.External_declarationContext external : root.external_declaration()) {
            if (external.declaration() != null) globalDeclarations.add(external.declaration());
        }
    }

    private void collect(ParseTree node) {
        if (node instanceof TerminalNode terminal) {
            final Token token = terminal.getSymbol();
            if (token.getType() == GLSLLexer.IDENTIFIER) identifiers.computeIfAbsent(token.getText(), k -> new ArrayList<>(2)).add(token);
            return;
        }
        if (node instanceof GLSLParser.Postfix_expressionContext p && p.LEFT_PAREN() != null) calls.add(p);
        for (int i = 0, n = node.getChildCount(); i < n; i++) collect(node.getChild(i));
    }

    public GLSLParser.Translation_unitContext root() {
        return root;
    }

    public List<Token> occurrences(String name) {
        return identifiers.getOrDefault(name, List.of());
    }

    public List<GLSLParser.Postfix_expressionContext> calls() {
        return calls;
    }

    public List<GLSLParser.DeclarationContext> globalDeclarations() {
        return globalDeclarations;
    }

    public static String calleeName(GLSLParser.Postfix_expressionContext call) {
        final ParseTree callee = call.getChild(0);
        return callee instanceof ParserRuleContext ? callee.getText() : "";
    }

    public static List<GLSLParser.Typeless_declarationContext> declarators(GLSLParser.Init_declarator_listContext list) {
        final GLSLParser.Single_declarationContext single = list.single_declaration();
        final List<GLSLParser.Typeless_declarationContext> rest = list.typeless_declaration();
        if (single == null || single.typeless_declaration() == null) return rest;
        final List<GLSLParser.Typeless_declarationContext> all = new ArrayList<>(rest.size() + 1);
        all.add(single.typeless_declaration());
        all.addAll(rest);
        return all;
    }

    public static boolean hasStorageQualifier(@Nullable GLSLParser.Type_qualifierContext qualifier, String keyword) {
        if (qualifier == null) return false;
        for (GLSLParser.Single_type_qualifierContext q : qualifier.single_type_qualifier()) {
            if (q.storage_qualifier() != null && keyword.equals(q.storage_qualifier().getText())) return true;
        }
        return false;
    }
}
