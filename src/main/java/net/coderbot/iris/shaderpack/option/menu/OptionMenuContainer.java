package net.coderbot.iris.shaderpack.option.menu;

import com.google.common.collect.Lists;
import lombok.Getter;
import net.coderbot.iris.Iris;
import net.coderbot.iris.shaderpack.ShaderProperties;
import net.coderbot.iris.shaderpack.option.ProfileSet;
import net.coderbot.iris.shaderpack.option.ShaderPackOptions;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public class OptionMenuContainer {
	public final OptionMenuElementScreen mainScreen;
	public final Map<String, OptionMenuElementScreen> subScreens = new HashMap<>();

	private final List<OptionMenuOptionElement> usedOptionElements = new ArrayList<>();
	private final List<String> usedOptions = new ArrayList<>();
	private final List<String> unusedOptions = new ArrayList<>(); // To be used when screens contain a "*" element
	private final Map<List<OptionMenuElement>, Integer> unusedOptionDumpQueue = new HashMap<>(); // Used by screens with "*" element
	@Getter private final ProfileSet profiles;
    @Getter private final ProfileSet profiles2;
	private final List<OptionMenuElement> originalMainElements = new ArrayList<>(); // Saved snapshot of mainScreen.elements before any search filter is applied.
	private final Map<String, String> cachedOptionPaths = new HashMap<>(); // Full "root/SCREEN1/SCREEN2" path for every option ID, built once after construction

	public OptionMenuContainer(ShaderProperties shaderProperties, ShaderPackOptions shaderPackOptions, ProfileSet profiles) {
		this.profiles = profiles;
        this.profiles2 = ProfileSet.fromTree(shaderProperties.getProfiles2(), shaderPackOptions.getOptionSet());

		// note: if the Shader Pack does not provide a list of options for the main screen, then dump all options on to
		// the main screen by default.
		this.mainScreen = new OptionMenuMainElementScreen(
			this, shaderProperties, shaderPackOptions,
			shaderProperties.getMainScreenOptions().orElseGet(() -> Collections.singletonList("*")),
			shaderProperties.getMainScreenColumnCount());

		this.unusedOptions.addAll(shaderPackOptions.getOptionSet().getBooleanOptions().keySet());
		this.unusedOptions.addAll(shaderPackOptions.getOptionSet().getStringOptions().keySet());

		Map<String, Integer> subScreenColumnCounts = shaderProperties.getSubScreenColumnCount();
		shaderProperties.getSubScreenOptions().forEach((screenKey, options) -> {
			subScreens.put(screenKey, new OptionMenuSubElementScreen(
					screenKey, this, shaderProperties, shaderPackOptions, options, Optional.ofNullable(subScreenColumnCounts.get(screenKey))));
		});

		// Dump all unused options into screens containing "*"
		for (Map.Entry<List<OptionMenuElement>, Integer> entry : unusedOptionDumpQueue.entrySet()) {
			List<OptionMenuElement> elementsToInsert = new ArrayList<>();
			List<String> unusedOptionsCopy = Lists.newArrayList(this.unusedOptions);

			for (String optionId : unusedOptionsCopy) {
				try {
					OptionMenuElement element = OptionMenuElement.create(optionId, this, shaderProperties, shaderPackOptions);
					if (element != null) {
						elementsToInsert.add(element);

						if (element instanceof OptionMenuOptionElement) {
							this.notifyOptionAdded(optionId, (OptionMenuOptionElement) element);
						}
					}
				} catch (IllegalArgumentException error) {
					Iris.logger.warn(error);

					elementsToInsert.add(OptionMenuElement.EMPTY);
				}
			}

			entry.getKey().addAll(entry.getValue(), elementsToInsert);
		}

		// Save the original layout and build the path cache once the full tree is constructed
		this.originalMainElements.addAll(this.mainScreen.elements);
		generateAllPaths();
	}

	// Screens will call this when they contain a "*" element, so that the list of
	// unused options can be added after all other screens have been resolved
	public void queueForUnusedOptionDump(int index, List<OptionMenuElement> elementList) {
		this.unusedOptionDumpQueue.put(elementList, index);
	}

	public void notifyOptionAdded(String optionId, OptionMenuOptionElement option) {
		if (!usedOptions.contains(optionId)) {
			usedOptionElements.add(option);
			usedOptions.add(optionId);
		}

		unusedOptions.remove(optionId);
	}

	private void generateAllPaths() {
		cachedOptionPaths.clear();
		traverseScreen(this.mainScreen, "root", new HashSet<String>());
	}

	private void traverseScreen(OptionMenuElementScreen screen, String currentPath, Set<String> visited) {
		if (screen == null) return;
		for (OptionMenuElement element : screen.elements) {
            if (element instanceof OptionMenuOptionElement optEl) {
                if (optEl.optionId != null) {
                    cachedOptionPaths.putIfAbsent(optEl.optionId, currentPath);
                }
            } else if (element instanceof OptionMenuLinkElement link) {
                if (link.targetScreenId != null) {
                    String targetId = link.targetScreenId;
                    if (visited.add(targetId)) {
                        OptionMenuElementScreen next = this.subScreens.get(targetId);
                        if (next != null) {
                            traverseScreen(next, currentPath + "/" + targetId, visited);
                        }
                        visited.remove(targetId);
                    }
                }
            }
        }
	}

	public String getOptionPath(String optionId) {
		if (optionId == null) return "root";
		return cachedOptionPaths.getOrDefault(optionId, "root");
	}

	/**
	 * Filters and re-orders the main-screen options to match {@code query}, or restores the
	 * original layout when {@code query} is null or blank.
	 */
	public void setSearchQuery(String query) {
		if (query == null || query.trim().isEmpty()) {
			restoreOriginalLayout();
			return;
		}

		String normalizedQuery = query.toLowerCase(Locale.ROOT).trim();

		List<OptionMenuOptionElement> flatOptions = ShaderSearchEngine.getAllOptionsFlattened(usedOptionElements);
		List<ShaderSearchEngine.ScoredOptionElement> scored = new ArrayList<>();

		for (OptionMenuOptionElement el : flatOptions) {
			int score = ShaderSearchEngine.computeMatchTier(el.optionId, normalizedQuery);
			if (score > 0) {
				scored.add(new ShaderSearchEngine.ScoredOptionElement(
					el,
					ShaderSearchEngine.getReadableTranslatedName(el.optionId),
					ShaderSearchEngine.getReadableDefaultName(el.optionId),
					getOptionPath(el.optionId),
					score,
					normalizedQuery
				));
			}
		}

		Collections.sort(scored);
		applyFilteredLayout(scored);
	}

	private void applyFilteredLayout(List<ShaderSearchEngine.ScoredOptionElement> sortedElements) {
		this.mainScreen.elements.clear();
		for (ShaderSearchEngine.ScoredOptionElement scored : sortedElements) {
			this.mainScreen.elements.add(scored.getElement());
		}
	}

	private void restoreOriginalLayout() {
		this.mainScreen.elements.clear();
		this.mainScreen.elements.addAll(this.originalMainElements);
	}
}
