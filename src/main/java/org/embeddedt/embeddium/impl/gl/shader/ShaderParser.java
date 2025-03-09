package org.embeddedt.embeddium.impl.gl.shader;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.StringReader;
import java.util.LinkedList;
import java.util.List;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ShaderParser {
    public static String parseShader(String src, Function<String, String> sourceProvider, ShaderConstants constants) {
        List<String> lines = parseShader(src, sourceProvider);
        lines.addAll(1, constants.getDefineStrings());

        return String.join("\n", lines);
    }

    public static List<String> parseShader(String src, Function<String, String> sourceProvider) {
        List<String> builder = new LinkedList<>();
        String line;

        try (BufferedReader reader = new BufferedReader(new StringReader(src))) {
            while ((line = reader.readLine()) != null) {
                if (line.startsWith("#import")) {
                    builder.addAll(resolveImport(line, sourceProvider));
                } else {
                    builder.add(line);
                }
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to read shader sources", e);
        }

        return builder;
    }

    private static final Pattern IMPORT_PATTERN = Pattern.compile("#import <(?<namespace>.*):(?<path>.*)>");

    private static List<String> resolveImport(String line, Function<String, String> sourceProvider) {
        Matcher matcher = IMPORT_PATTERN.matcher(line);

        if (!matcher.matches()) {
            throw new IllegalArgumentException("Malformed import statement (expected format: " + IMPORT_PATTERN + ")");
        }

        String namespace = matcher.group("namespace");
        String path = matcher.group("path");

        String source = sourceProvider.apply(namespace + ":" + path);

        return ShaderParser.parseShader(source, sourceProvider);
    }
}
