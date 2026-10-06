package com.gtnewhorizons.angelica.glsm;

import org.antlr.v4.runtime.BailErrorStrategy;
import org.antlr.v4.runtime.BufferedTokenStream;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonToken;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.Parser;
import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.WritableToken;
import org.antlr.v4.runtime.atn.PredictionMode;
import org.antlr.v4.runtime.misc.ParseCancellationException;
import org.antlr.v4.runtime.tree.ParseTree;
import org.antlr.v4.runtime.tree.TerminalNode;
import org.taumc.glsl.grammar.GLSLLexer;
import org.taumc.glsl.grammar.GLSLParser;
import org.taumc.glsl.grammar.GLSLPreParser;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Shared GLSL shader transformation utilities.
 */
public class GlslTransformUtils {

    private static final String RENAMED_PREFIX = "angelica_renamed_";

    /** Texture function renames */
    public static final Map<String, String> TEXTURE_RENAMES = Map.ofEntries(
        Map.entry("texture2D", "texture"),
        Map.entry("texture3D", "texture"),
        Map.entry("texture2DLod", "textureLod"),
        Map.entry("texture3DLod", "textureLod"),
        Map.entry("texture2DProj", "textureProj"),
        Map.entry("texture3DProj", "textureProj"),
        Map.entry("texture2DGrad", "textureGrad"),
        Map.entry("texture2DGradARB", "textureGrad"),
        Map.entry("texture3DGrad", "textureGrad"),
        Map.entry("texelFetch2D", "texelFetch"),
        Map.entry("texelFetch3D", "texelFetch"),
        Map.entry("textureSize2D", "textureSize")
    );

    private static final Pattern TEXTURE_PATTERN = Pattern.compile("\\btexture\\s*\\(|(\\btexture\\b)");

    /** Reserved words added in later GLSL versions that may appear as identifiers in older shaders. */
    private record ReservedWordRename(Pattern pattern, String replacement) {}
    private static final Map<Integer, List<ReservedWordRename>> VERSIONED_RESERVED_WORDS = Map.of(
        // Some reserved words to always rename
        0, List.of(
            new ReservedWordRename(Pattern.compile("sample(?<=\\bsample)\\b"), RENAMED_PREFIX + "sample"),
            new ReservedWordRename(Pattern.compile("new(?<=\\bnew)\\b"), RENAMED_PREFIX + "new")
        ),
        400, List.of(
            new ReservedWordRename(Pattern.compile("sampler(?<=\\bsampler)\\b(?!\\d)"), RENAMED_PREFIX + "sampler")
        )
    );

    public static String replaceTexture(String input) {
        final Matcher matcher = TEXTURE_PATTERN.matcher(input);
        final StringBuilder builder = new StringBuilder();
        while (matcher.find()) {
            if (matcher.group(1) != null) {
                matcher.appendReplacement(builder, RENAMED_PREFIX + "texture");
            } else {
                matcher.appendReplacement(builder, Matcher.quoteReplacement(matcher.group(0)));
            }
        }
        matcher.appendTail(builder);
        return builder.toString();
    }

    public static String renameReservedWords(String source, int targetVersion) {
        if (!containsWholeWord(source, "sample") && !containsWholeWord(source, "new")
            && (targetVersion < 400 || !containsWholeWord(source, "sampler"))) {
            return source;
        }
        for (var entry : VERSIONED_RESERVED_WORDS.entrySet()) {
            // Rename identifiers that become reserved words at or above this GLSL version
            if (targetVersion >= entry.getKey()) {
                for (var rename : entry.getValue()) {
                    source = rename.pattern().matcher(source).replaceAll(rename.replacement());
                }
            }
        }
        return source;
    }

    static boolean containsWholeWord(String source, String word) {
        int from = 0;
        while (true) {
            final int match = source.indexOf(word, from);
            if (match < 0) return false;
            final int end = match + word.length();
            if ((match == 0 || !isAsciiWordChar(source.charAt(match - 1))) && (end == source.length() || !isAsciiWordChar(source.charAt(end)))) {
                return true;
            }
            from = match + 1;
        }
    }

    private static boolean isAsciiWordChar(char c) {
        return (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9') || c == '_';
    }

    public static String restoreReservedWords(String source) {
        source = source.replace(RENAMED_PREFIX + "texture", "texture");
        source = source.replace(RENAMED_PREFIX + "sampler", "sampler");
        source = source.replace(RENAMED_PREFIX + "sample", "sample");
        return source;
    }

    public static void restoreReservedWordsInTree(ParseTree tree) {
        if (tree instanceof TerminalNode terminal) {
            final Token token = terminal.getSymbol();
            final String text = token.getText();
            if (text != null && text.startsWith(RENAMED_PREFIX) && token instanceof WritableToken writable) {
                final String rest = text.substring(RENAMED_PREFIX.length());
                if (rest.startsWith("texture") || rest.startsWith("sample")) {
                    writable.setText(rest);
                }
            }
            return;
        }
        for (int i = 0; i < tree.getChildCount(); i++) {
            restoreReservedWordsInTree(tree.getChild(i));
        }
    }

    public record QuietParse(GLSLParser.Translation_unitContext full, GLSLPreParser.Translation_unitContext pre) {}

    public static GLSLParser.Translation_unitContext parseFullQuiet(String source) {
        return parseQuiet(source, lexer -> new GLSLParser(new CommonTokenStream(lexer)), GLSLParser::translation_unit);
    }

    public static QuietParse parseBothQuiet(String source) {
        final GLSLPreParser.Translation_unitContext pre = parseQuiet(source, lexer -> new GLSLPreParser(new BufferedTokenStream(lexer)), GLSLPreParser::translation_unit);
        return new QuietParse(parseFullQuiet(source), pre);
    }

    private static <P extends Parser, T> T parseQuiet(String source, Function<GLSLLexer, P> newParser, Function<P, T> rule) {
        final P fast = newParser.apply(quietLexer(source));
        fast.removeErrorListeners();
        fast.setErrorHandler(new BailErrorStrategy());
        fast.getInterpreter().setPredictionMode(PredictionMode.SLL);
        try {
            return rule.apply(fast);
        } catch (ParseCancellationException e) {
            final P full = newParser.apply(quietLexer(source));
            full.removeErrorListeners();
            return rule.apply(full);
        }
    }

    public static GLSLLexer quietLexer(String source) {
        final GLSLLexer lexer = new GLSLLexer(CharStreams.fromString(source));
        lexer.removeErrorListeners();
        return lexer;
    }

    public static Set<String> identifiersInDirectiveText(String source) {
        if (source.indexOf('#') < 0) return Set.of();
        final GLSLLexer lexer = quietLexer(source);
        final Set<String> names = new HashSet<>();
        for (Token token = lexer.nextToken(); token.getType() != Token.EOF; token = lexer.nextToken()) {
            switch (token.getType()) {
                case GLSLLexer.PROGRAM_TEXT, GLSLLexer.MACRO_TEXT, GLSLLexer.CONSTANT_EXPRESSION -> collectIdentifiers(token.getText(), names);
                default -> { }
            }
        }
        return names;
    }

    public static boolean hasConditionalOrMacroDirectives(String source) {
        if (source.indexOf('#') < 0) return false;
        final GLSLLexer lexer = quietLexer(source);
        for (Token token = lexer.nextToken(); token.getType() != Token.EOF; token = lexer.nextToken()) {
            switch (token.getType()) {
                case GLSLLexer.IF_DIRECTIVE, GLSLLexer.IFDEF_DIRECTIVE, GLSLLexer.IFNDEF_DIRECTIVE, GLSLLexer.DEFINE_DIRECTIVE -> {
                    return true;
                }
                default -> { }
            }
        }
        return false;
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

    public static String getFormattedShader(ParseTree tree, String header) {
        final StringBuilder sb = new StringBuilder(header + "\n");
        final String[] tabHolder = {""};
        getFormattedShader(tree, sb, tabHolder, false);
        return sb.toString();
    }

    public static String getFormattedShaderRebased(ParseTree tree, String header) {
        final StringBuilder sb = new StringBuilder(header + "\n");
        final String[] tabHolder = {""};
        getFormattedShader(tree, sb, tabHolder, true);
        return sb.toString();
    }

    private static void getFormattedShader(ParseTree tree, StringBuilder stringBuilder, String[] tabHolder, boolean rebaseOffsets) {
        if (tree instanceof TerminalNode terminal) {
            final String text = tree.getText();
            if (text.equals("<EOF>")) {
                return;
            }
            if (text.equals("#")) {
                stringBuilder.append("\n#");
                rebaseAt(terminal, stringBuilder.length() - 1, text, rebaseOffsets);
                return;
            }
            stringBuilder.append(text);
            if (!text.equals("}")) {
                rebaseAt(terminal, stringBuilder.length() - text.length(), text, rebaseOffsets);
            }
            if (text.equals("{")) {
                stringBuilder.append(" \n\t");
                tabHolder[0] = "\t";
            }

            if (text.equals("}")) {
                if (stringBuilder.length() >= 2) {
                    stringBuilder.deleteCharAt(stringBuilder.length() - 2);
                }
                rebaseAt(terminal, stringBuilder.length() - 1, text, rebaseOffsets);
                tabHolder[0] = "";
                stringBuilder.append(" \n");
            } else {
                stringBuilder.append(text.equals(";") ? " \n" + tabHolder[0] : " ");
            }
        } else {
            for (int i = 0; i < tree.getChildCount(); ++i) {
                getFormattedShader(tree.getChild(i), stringBuilder, tabHolder, rebaseOffsets);
            }
        }
    }

    private static void rebaseAt(TerminalNode terminal, int startIndex, String text, boolean rebaseOffsets) {
        if (!rebaseOffsets) return;
        if (terminal.getSymbol() instanceof CommonToken common) {
            common.setText(text);
            common.setStartIndex(startIndex);
            common.setStopIndex(startIndex + text.length() - 1);
        }
    }
}
