package com.gtnewhorizons.angelica.glsm.shader;

import com.gtnewhorizons.angelica.glsm.GlslTransformUtils;
import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.tree.ParseTree;
import org.antlr.v4.runtime.tree.TerminalNode;
import org.jetbrains.annotations.Nullable;
import org.taumc.glsl.grammar.GLSLLexer;
import org.taumc.glsl.grammar.GLSLParser;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * What the source passes look up in a parsed shader.
 */
public final class ShaderIndex {

    public record Directives(Set<String> identifiers, boolean hasConditionalOrMacro, int versionEnd) {
        private static final Directives NONE = new Directives(Set.of(), false, -1);
    }

    private final GLSLParser.Translation_unitContext root;
    private final String source;
    private final Map<String, List<Token>> identifiers = new HashMap<>();
    private final Map<String, List<GLSLParser.Postfix_expressionContext>> calls = new HashMap<>();
    private final List<GLSLParser.DeclarationContext> globalDeclarations = new ArrayList<>();
    private @Nullable Directives directives;

    public ShaderIndex(GLSLParser.Translation_unitContext root, String source) {
        this.root = root;
        this.source = source;
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
        if (node instanceof GLSLParser.Postfix_expressionContext p && p.LEFT_PAREN() != null) {
            calls.computeIfAbsent(calleeName(p), k -> new ArrayList<>(2)).add(p);
        }
        for (int i = 0, n = node.getChildCount(); i < n; i++) collect(node.getChild(i));
    }

    public GLSLParser.Translation_unitContext root() {
        return root;
    }

    public String source() {
        return source;
    }

    public List<Token> occurrences(String name) {
        return identifiers.getOrDefault(name, List.of());
    }

    public List<GLSLParser.Postfix_expressionContext> callsTo(String name) {
        return calls.getOrDefault(name, List.of());
    }

    public List<GLSLParser.DeclarationContext> globalDeclarations() {
        return globalDeclarations;
    }

    /** Lexes the source on first use. */
    public Directives directives() {
        if (directives == null) directives = scanDirectives(source);
        return directives;
    }

    private static Directives scanDirectives(String source) {
        if (source.indexOf('#') < 0) return Directives.NONE;
        final GLSLLexer lexer = GlslTransformUtils.quietLexer(source);
        final Set<String> names = new HashSet<>();
        boolean conditionalOrMacro = false;
        int versionEnd = -1;
        boolean inVersion = false;
        for (Token token = lexer.nextToken(); token.getType() != Token.EOF; token = lexer.nextToken()) {
            final int type = token.getType();
            if (type == GLSLLexer.VERSION_DIRECTIVE && versionEnd < 0) {
                inVersion = true;
                versionEnd = token.getStopIndex() + 1;
                continue;
            }
            if (inVersion && (type == GLSLLexer.NUMBER || type == GLSLLexer.PROFILE)) {
                versionEnd = token.getStopIndex() + 1;
                continue;
            }
            inVersion = false;
            switch (type) {
                case GLSLLexer.PROGRAM_TEXT, GLSLLexer.MACRO_TEXT, GLSLLexer.CONSTANT_EXPRESSION -> collectIdentifiers(token.getText(), names);
                case GLSLLexer.IF_DIRECTIVE, GLSLLexer.IFDEF_DIRECTIVE, GLSLLexer.IFNDEF_DIRECTIVE, GLSLLexer.DEFINE_DIRECTIVE -> conditionalOrMacro = true;
                default -> { }
            }
        }
        return new Directives(names, conditionalOrMacro, versionEnd);
    }

    private static void collectIdentifiers(String text, Set<String> out) {
        if (text == null) return;
        final int len = text.length();
        int i = 0;
        while (i < len) {
            final char c = text.charAt(i);
            if (c != '_' && !Character.isLetter(c)) {
                i++;
                continue;
            }
            int end = i + 1;
            while (end < len) {
                final char n = text.charAt(end);
                if (n != '_' && !Character.isLetterOrDigit(n)) break;
                end++;
            }
            out.add(text.substring(i, end));
            i = end;
        }
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
