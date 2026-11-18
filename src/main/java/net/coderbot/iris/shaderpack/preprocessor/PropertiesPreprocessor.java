package net.coderbot.iris.shaderpack.preprocessor;

import com.gtnewhorizons.angelica.glsm.shader.ShaderDiskCache;
import net.coderbot.iris.Iris;
import net.coderbot.iris.shaderpack.StringPair;
import net.coderbot.iris.shaderpack.option.ShaderPackOptions;
import org.anarres.cpp.Feature;
import org.anarres.cpp.LexerException;
import org.anarres.cpp.Preprocessor;
import org.anarres.cpp.PreprocessorCommand;
import org.anarres.cpp.StringLexerSource;
import org.anarres.cpp.Token;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class PropertiesPreprocessor {
	// Derived from ShaderProcessor.glslPreprocessSource, which is derived from GlShader from Canvas, licenced under LGPL
	public static String preprocessSource(String source, ShaderPackOptions shaderPackOptions, Iterable<StringPair> environmentDefines) {
		if (source.contains(PropertyCollectingListener.PROPERTY_MARKER) || source.contains("IRIS_PASSTHROUGHBACKSLASH")) {
			throw new RuntimeException("Some shader author is trying to exploit internal Iris implementation details, stop!");
		}

		final List<String> booleanValues = getBooleanValues(shaderPackOptions);
		final Map<String, String> stringValues = getStringValues(shaderPackOptions);

		final List<StringPair> macros = new ArrayList<>();
		for (String value : booleanValues) macros.add(new StringPair(value, ""));
		for (StringPair envDefine : environmentDefines) macros.add(envDefine);
		stringValues.forEach((name, value) -> macros.add(new StringPair(name, value)));
		final ShaderDiskCache.Key cacheKey = PreprocessedSourceCache.key("properties", source, macros);
		final String cached = PreprocessedSourceCache.get(cacheKey);
		if (cached != null) return cached;

		try (Preprocessor pp = new Preprocessor()) {
			for (String value : booleanValues) {
				pp.addMacro(value);
			}

			for (StringPair envDefine : environmentDefines) {
				pp.addMacro(envDefine.getKey(), envDefine.getValue());
			}

			stringValues.forEach((name, value) -> {
				try {
					pp.addMacro(name, value);
				} catch (LexerException e) {
					e.printStackTrace();
				}
			});

			final String preprocessed = process(pp, source);
			PreprocessedSourceCache.put(cacheKey, preprocessed);
			return preprocessed;
		} catch (IOException e) {
			throw new RuntimeException("Unexpected IOException while processing macros", e);
		} catch (LexerException e) {
			throw new RuntimeException("Unexpected LexerException processing macros", e);
		}
	}

	public static String preprocessSource(String source, Iterable<StringPair> environmentDefines) {
		if (source.contains(PropertyCollectingListener.PROPERTY_MARKER)) {
			throw new RuntimeException("Some shader author is trying to exploit internal Iris implementation details, stop!");
		}

		final Preprocessor preprocessor = new Preprocessor();

		try {
			for (StringPair envDefine : environmentDefines) {
				preprocessor.addMacro(envDefine.getKey(), envDefine.getValue());
			}
		} catch (LexerException e) {
			e.printStackTrace();
		}

		return process(preprocessor, source);
	}

	private static String process(Preprocessor preprocessor, String source) {
		preprocessor.setListener(new PropertiesCommentListener());
		PropertyCollectingListener listener = new PropertyCollectingListener();
		preprocessor.setListener(listener);

		// This removes trailing whitespace on lines, fixing an issue with whitespace after
		// line continuations (see PreprocessorTest#testWeirdPropertiesLineContinuation)
		// Required for Voyager Shader
		source = cleanLines(source);
		// TODO: This is a horrible fix to trick the preprocessor into not seeing the backslashes during processing. We need a better way to do this.
		source = source.replace("\\", "IRIS_PASSTHROUGHBACKSLASH");

		preprocessor.addInput(new StringLexerSource(source, true));
		preprocessor.addFeature(Feature.KEEPCOMMENTS);

		final StringBuilder builder = new StringBuilder();

		try {
			for (;;) {
				final Token tok = preprocessor.token();
				if (tok == null) break;
				if (tok.getType() == Token.EOF) break;
				builder.append(tok.getText());
			}
		} catch (final Exception e) {
			Iris.logger.error("Properties pre-processing failed", e);
		}

		source = builder.toString();

		return (listener.collectLines() + source).replace("IRIS_PASSTHROUGHBACKSLASH", "\\");
	}

	private static final String[] DIRECTIVE_PREFIXES = Arrays.stream(PreprocessorCommand.values())
		.map(command -> "#" + command.name().replace("PP_", "").toLowerCase(Locale.ROOT))
		.toArray(String[]::new);

	// Same result as splitting on \r\n|\n|\r, trimming, dropping empty lines and joining with \n
	static String cleanLines(String source) {
		final StringBuilder out = new StringBuilder(source.length());
		boolean first = true;
		int start = 0;
		final int length = source.length();
		while (start <= length) {
			int end = start;
			while (end < length && source.charAt(end) != '\n' && source.charAt(end) != '\r') end++;
			final String line = source.substring(start, end).trim();
			if (!line.isEmpty()) {
				if (!first) out.append('\n');
				first = false;
				out.append(cleanLine(line));
			}
			if (end < length && source.charAt(end) == '\r' && end + 1 < length && source.charAt(end + 1) == '\n') end++;
			start = end + 1;
		}
		return out.append('\n').toString();
	}

	private static String cleanLine(String line) {
		if (line.startsWith("#")) {
			for (String prefix : DIRECTIVE_PREFIXES) {
				if (line.startsWith(prefix)) {
					return line;
				}
			}
			return "";
		}
		// In PropertyCollectingListener we suppress "unknown preprocessor directive errors" and
		// assume the line to be a comment, since in .properties files `#` also functions as a comment
		// marker.
		return line.indexOf('#') < 0 ? line : line.replace("#", "");
	}

	private static List<String> getBooleanValues(ShaderPackOptions shaderPackOptions) {
		List<String> booleanValues = new ArrayList<>();

		shaderPackOptions.getOptionSet().getBooleanOptions().forEach((string, value) -> {
			boolean trueValue = shaderPackOptions.getOptionValues().getBooleanValueOrDefault(string);

			if (trueValue) {
				booleanValues.add(string);
			}
		});

		return booleanValues;
	}

	private static Map<String, String> getStringValues(ShaderPackOptions shaderPackOptions) {
		Map<String, String> stringValues = new HashMap<>();

		shaderPackOptions.getOptionSet().getStringOptions().forEach(
				(optionName, value) -> stringValues.put(optionName, shaderPackOptions.getOptionValues().getStringValueOrDefault(optionName)));

		return stringValues;
	}
}
