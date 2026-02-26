package com.gtnewhorizons.angelica.glsm;

import org.antlr.v4.runtime.tree.ParseTree;
import org.antlr.v4.runtime.tree.TerminalNode;

import java.util.List;
import java.util.Map;
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
        400, List.of(new ReservedWordRename(Pattern.compile("\\bsample\\b"), RENAMED_PREFIX + "sample"))
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
        for (var entry : VERSIONED_RESERVED_WORDS.entrySet()) {
            if (targetVersion < entry.getKey()) {
                for (var rename : entry.getValue()) {
                    source = rename.pattern().matcher(source).replaceAll(rename.replacement());
                }
            }
        }
        return source;
    }

    public static String restoreReservedWords(String source) {
        source = source.replace(RENAMED_PREFIX + "texture", "texture");
        source = source.replace(RENAMED_PREFIX + "sample", "sample");
        return source;
    }

    public static String getFormattedShader(ParseTree tree, String header) {
        final StringBuilder sb = new StringBuilder(header + "\n");
        final String[] tabHolder = {""};
        getFormattedShader(tree, sb, tabHolder);
        return sb.toString();
    }

    private static void getFormattedShader(ParseTree tree, StringBuilder stringBuilder, String[] tabHolder) {
        if (tree instanceof TerminalNode) {
            final String text = tree.getText();
            if (text.equals("<EOF>")) {
                return;
            }
            if (text.equals("#")) {
                stringBuilder.append("\n#");
                return;
            }
            stringBuilder.append(text);
            if (text.equals("{")) {
                stringBuilder.append(" \n\t");
                tabHolder[0] = "\t";
            }

            if (text.equals("}")) {
                if (stringBuilder.length() >= 2) {
                    stringBuilder.deleteCharAt(stringBuilder.length() - 2);
                }
                tabHolder[0] = "";
                stringBuilder.append(" \n");
            } else {
                stringBuilder.append(text.equals(";") ? " \n" + tabHolder[0] : " ");
            }
        } else {
            for (int i = 0; i < tree.getChildCount(); ++i) {
                getFormattedShader(tree.getChild(i), stringBuilder, tabHolder);
            }
        }
    }
}
