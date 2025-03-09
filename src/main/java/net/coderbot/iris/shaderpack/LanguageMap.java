package net.coderbot.iris.shaderpack;

import com.google.common.collect.ImmutableMap;
import net.coderbot.iris.Iris;

import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.stream.Stream;

public class LanguageMap {
	private final Map<String, Map<String, String>> translationMaps;

	public LanguageMap(Path root) throws IOException {
		this.translationMaps = new HashMap<>();

		if (!Files.exists(root)) {
			return;
		}

		// We are using a max depth of one to ensure we only get the surface level *files* without going deeper
		// we also want to avoid any directories while filtering
		// Basically, we want the immediate files nested in the path for the langFolder
		// There is also Files.list which can be used for similar behavior
		try (Stream<Path> stream = Files.list(root)) {
			stream.filter(path -> !Files.isDirectory(path)).forEach(path -> {
				// Also note that OptiFine uses a property scheme for loading language entries to keep parity with other
				// OptiFine features
				final String currentFileName = path.getFileName().toString();

				if (!currentFileName.endsWith(".lang")) {
					// This file lacks a .lang file extension and should be ignored.
					return;
				}

				final String currentLangCode = currentFileName.substring(0, currentFileName.lastIndexOf("."));
				final Properties properties = new Properties();

				// Use InputStreamReader to avoid the default charset of ISO-8859-1.
				// This is needed since shader language files are specified to be in UTF-8.
				try (InputStreamReader isr = new InputStreamReader(Files.newInputStream(path), StandardCharsets.UTF_8)) {
					properties.load(isr);
				} catch (IOException e) {
					Iris.logger.error("Failed to parse shader pack language file " + path, e);
				}

				final ImmutableMap.Builder<String, String> builder = ImmutableMap.builder();

				properties.forEach((key, value) -> builder.put(key.toString(), value.toString()));

				translationMaps.put(currentLangCode, builder.build());
			});
		}
	}

	public Set<String> getLanguages() {
		// Ensure that the caller can't mess with the language map.
		return Collections.unmodifiableSet(translationMaps.keySet());
	}

	public Map<String, String> getTranslations(String language) {
		// We're returning an immutable map, so the caller can't modify it.
		return translationMaps.get(language);
	}
}
