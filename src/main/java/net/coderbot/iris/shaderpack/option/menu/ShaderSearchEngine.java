package net.coderbot.iris.shaderpack.option.menu;

import lombok.Getter;
import net.coderbot.iris.Iris;
import net.coderbot.iris.shaderpack.ShaderPack;
import net.minecraft.client.resources.I18n;
import org.jspecify.annotations.NonNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Utility class for searching and scoring shader option elements based on a query string.
 * <br>By SpacEagle17
 */
public class ShaderSearchEngine {
	private static final String WHOLE_WORD_REGEX  = "(?<=^|[^a-zA-Z0-9])%s(?=$|[^a-zA-Z0-9])";
	private static final String STARTS_WITH_REGEX = "(?<=^|[^a-zA-Z0-9])%s";
	private static final Pattern COLOR_CODE_PATTERN = Pattern.compile("§.");

	/**
	 * Bitmask of bits from readable-name (translated + default) comparisons.
	 * Used by the comparator to separate name-match quality from rawId/comment noise.
	 */
	private static final int READABLE_NAME_BITS =
		(1 << 13) | (1 << 12) | (1 << 11) | (1 << 10) | (1 << 9) | (1 << 8) | (1 << 3) | (1 << 2);


	private static String strippedLanguageString(String key) {
		String translated = I18n.format(key);
		if (translated.equals(key)) return "";
		return COLOR_CODE_PATTERN.matcher(translated).replaceAll("");
	}

	/** Translated display name for a sub-screen ID (looks up {@code "screen.<screenId>"}). */
	public static String getDisplaySettingsName(String screenId) {
		return strippedLanguageString("screen." + screenId);
	}

	/** Lowercase Minecraft-locale translation of an option's human-readable name. */
	public static String getReadableTranslatedName(String optionId) {
		return strippedLanguageString("option." + optionId).toLowerCase(Locale.ROOT);
	}

	/**
	 * Lowercase default (en_us from the shader pack's lang folder) translation.
	 * Returns {@code ""} when the current pack has no en_us lang file or the key is missing.
	 */
	public static String getReadableDefaultName(String optionId) {
		try {
			Optional<ShaderPack> packOpt = Iris.getCurrentPack();
			if (packOpt.isEmpty()) return "";
			Map<String, String> translations = packOpt.get().getLanguageMap().getTranslations("en_us");
			if (translations == null) return "";
			String value = translations.get("option." + optionId);
			if (value == null) return "";
			return COLOR_CODE_PATTERN.matcher(value).replaceAll("").toLowerCase(Locale.ROOT);
		} catch (Throwable t) {
			return "";
		}
	}

	private static boolean isOnlyAscii(String s) {
		for (int i = 0; i < s.length(); i++) {
			if (s.charAt(i) > 127) return false;
		}
		return true;
	}

	/**
	 * Computes a 14-bit bitmask score for how well {@code optionId} matches {@code query}.
	 * Returns 0 when there is no match at all.
	 * <br> Read code to know what each bit means lmao
	 */
	public static int computeMatchTier(String optionId, String query) {
		try {
			if (optionId == null || query == null) return 0;
			String q = query.toLowerCase(Locale.ROOT).trim();
			if (q.isEmpty()) return 0;

			String translatedName = getReadableTranslatedName(optionId);
			String defaultName    = getReadableDefaultName(optionId);

			// Single ASCII char: only match when a readable name begins with it
			if (q.length() == 1 && isOnlyAscii(q)) {
				return (!translatedName.isEmpty() && translatedName.startsWith(q)) ? 1 : 0;
			}

			String  escaped    = Pattern.quote(q);
			Pattern wholeWord  = Pattern.compile(String.format(WHOLE_WORD_REGEX,  escaped));
			Pattern startsWith = Pattern.compile(String.format(STARTS_WITH_REGEX, escaped));

			String rawId       = optionId.toLowerCase(Locale.ROOT);
			String commentText = strippedLanguageString("option." + optionId + ".comment").toLowerCase(Locale.ROOT);

			int score = 0;
			if (!translatedName.isEmpty() && translatedName.equals(q))                   score |= (1 << 13);
			if (!defaultName.isEmpty()    && defaultName.equals(q))                      score |= (1 << 12);
			if (!translatedName.isEmpty() && wholeWord.matcher(translatedName).find())   score |= (1 << 11);
			if (!defaultName.isEmpty()    && wholeWord.matcher(defaultName).find())      score |= (1 << 10);
			if (!translatedName.isEmpty() && startsWith.matcher(translatedName).find())  score |= (1 << 9);
			if (!defaultName.isEmpty()    && startsWith.matcher(defaultName).find())     score |= (1 << 8);
			if (wholeWord.matcher(rawId).find())                                         score |= (1 << 7);
			if (!commentText.isEmpty()    && wholeWord.matcher(commentText).find())      score |= (1 << 6);
			if (startsWith.matcher(rawId).find())                                        score |= (1 << 5);
			if (!commentText.isEmpty()    && startsWith.matcher(commentText).find())     score |= (1 << 4);
			if (!translatedName.isEmpty() && translatedName.contains(q))                 score |= (1 << 3);
			if (!defaultName.isEmpty()    && defaultName.contains(q))                    score |= (1 << 2);
			if (rawId.contains(q))                                                       score |= (1 << 1);
			if (!commentText.isEmpty()    && commentText.contains(q))                    score |= 1;

			return score;
		} catch (Exception e) {
			return 0;
		}
	}

	/** De-duplicates and flattens the pool of registered option elements. */
	public static List<OptionMenuOptionElement> getAllOptionsFlattened(List<OptionMenuOptionElement> usedOptionElements) {
		List<OptionMenuOptionElement> flat = new ArrayList<>();
		List<String> seen = new ArrayList<>();
		for (OptionMenuOptionElement el : usedOptionElements) {
			if (el == null) continue;
			String id = el.optionId != null ? el.optionId : el.toString();
			if (!seen.contains(id)) {
				seen.add(id);
				flat.add(el);
			}
		}
		return flat;
	}

	/**
	 * A scored search result carrying the original element and all data required for sorting.
	 */
	public static final class ScoredOptionElement implements Comparable<ScoredOptionElement> {
		@Getter
        private final OptionMenuOptionElement element;
		public final String readableTranslatedName;
		public final String readableDefaultName;
		public final String path;
		public final int score;
		public final String query;

		public ScoredOptionElement(OptionMenuOptionElement element,
								   String readableTranslatedName,
								   String readableDefaultName,
								   String path,
								   int score,
								   String query) {
			this.element = element;
			this.readableTranslatedName = readableTranslatedName;
			this.readableDefaultName    = readableDefaultName;
			this.path  = path;
			this.score = score;
			this.query = query;
		}

        @Override
		public int compareTo(@NonNull ScoredOptionElement other) {
			String q = this.query != null ? this.query.toLowerCase(Locale.ROOT).trim() : "";
			int r;
			if ((r = compareByReadableNameQuality(this, other)) != 0) return r;
			if ((r = compareByMatchedWordLength(this, other, q)) != 0) return r;
			if ((r = compareByWordCount(this, other))            != 0) return r;
			if ((r = compareByFullScore(this, other))            != 0) return r;
			if ((r = compareByPrefixBoost(this, other, q))       != 0) return r;
			if ((r = compareByPathDepth(this, other))            != 0) return r;
			return compareByAlphabetical(this, other);
		}

		// 1. How well does the readable name (translated + default) match?
		private static int compareByReadableNameQuality(ScoredOptionElement a, ScoredOptionElement b) {
			return Integer.compare(b.score & READABLE_NAME_BITS, a.score & READABLE_NAME_BITS);
		}

		// 2. Shorter matched word = query covers a higher fraction of that word.
		private static int compareByMatchedWordLength(ScoredOptionElement a, ScoredOptionElement b, String q) {
			if (q.isEmpty()) return 0;
			String aw = findMatchingWord(a.readableTranslatedName, q);
			if (aw == null) aw = findMatchingWord(a.readableDefaultName, q);
			String bw = findMatchingWord(b.readableTranslatedName, q);
			if (bw == null) bw = findMatchingWord(b.readableDefaultName, q);
			int aLen = aw != null ? aw.length() : Integer.MAX_VALUE;
			int bLen = bw != null ? bw.length() : Integer.MAX_VALUE;
			return Integer.compare(aLen, bLen);
		}

		// 3. Fewer words = more precise ("Bloom" before "Bloom Strength").
		private static int compareByWordCount(ScoredOptionElement a, ScoredOptionElement b) {
			String ae = !a.readableTranslatedName.isEmpty() ? a.readableTranslatedName : a.readableDefaultName;
			String be = !b.readableTranslatedName.isEmpty() ? b.readableTranslatedName : b.readableDefaultName;
			return Integer.compare(countWords(ae), countWords(be));
		}

		// 4. Higher raw score wins.
		private static int compareByFullScore(ScoredOptionElement a, ScoredOptionElement b) {
			return Integer.compare(b.score, a.score);
		}

		// 5. Prefix boost: readable name starts with the exact query string.
		private static int compareByPrefixBoost(ScoredOptionElement a, ScoredOptionElement b, String q) {
			if (q.isEmpty()) return 0;
			boolean ap = (a.readableTranslatedName != null && a.readableTranslatedName.startsWith(q))
					  || (a.readableDefaultName    != null && a.readableDefaultName.startsWith(q));
			boolean bp = (b.readableTranslatedName != null && b.readableTranslatedName.startsWith(q))
					  || (b.readableDefaultName    != null && b.readableDefaultName.startsWith(q));
			if (ap == bp) return 0;
			return ap ? -1 : 1;
		}

		// 6. Shallower option path wins (fewer slashes = closer to root menu).
		private static int compareByPathDepth(ScoredOptionElement a, ScoredOptionElement b) {
			return Integer.compare(countSlashes(a.path), countSlashes(b.path));
		}

		// 7. Alphabetical tie-breaker.
		private static int compareByAlphabetical(ScoredOptionElement a, ScoredOptionElement b) {
			String aId = a.element != null ? a.element.optionId : null;
			String bId = b.element != null ? b.element.optionId : null;
			if (aId != null && bId != null) return aId.compareTo(bId);
			return 0;
		}

		private static String findMatchingWord(String name, String query) {
			if (name == null || query.isEmpty()) return null;
			for (String word : name.split("\\s+")) {
				if (word.startsWith(query)) return word;
			}
			return null;
		}

		private static int countWords(String s) {
			if (s == null || s.trim().isEmpty()) return 0;
			return s.trim().split("\\s+").length;
		}

		private static int countSlashes(String path) {
			if (path == null) return 0;
			int count = 0;
			for (int i = 0; i < path.length(); i++) {
				if (path.charAt(i) == '/') count++;
			}
			return count;
		}
	}
}
