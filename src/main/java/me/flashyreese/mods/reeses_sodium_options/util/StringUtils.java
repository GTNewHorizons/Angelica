package me.flashyreese.mods.reeses_sodium_options.util;

import me.jellysquid.mods.sodium.client.gui.options.Option;
import me.jellysquid.mods.sodium.client.gui.options.OptionPage;

import java.util.ArrayList;
import java.util.List;

public class StringUtils {
    // Levenshtein distance: Calculates the number of edits (insertion, deletion, substitution) needed to transform one string into another.
    public static int levenshteinDistance(String s1, String s2) {
        int m = s1.length();
        int n = s2.length();

        int[][] dp = new int[m + 1][n + 1];

        for (int i = 0; i <= m; i++) {
            dp[i][0] = i;
        }

        for (int j = 0; j <= n; j++) {
            dp[0][j] = j;
        }

        for (int i = 1; i <= m; i++) {
            for (int j = 1; j <= n; j++) {
                int cost = s1.charAt(i - 1) == s2.charAt(j - 1) ? 0 : 1;
                dp[i][j] = Math.min(dp[i - 1][j] + 1, Math.min(dp[i][j - 1] + 1, dp[i - 1][j - 1] + cost));
            }
        }

        return dp[m][n];
    }

    public static List<Option<?>> fuzzySearch(List<OptionPage> pages, String userInput, int maxDistance) {
        List<Option<?>> result = new ArrayList<>();
        String[] targetWords = userInput.toLowerCase().split("\\s+");

        for (OptionPage page : pages) {
            for (Option<?> option : page.getOptions()) {
                String sentence = (option.getName()/*  +
                        " " +
                        option.getTooltip().getString()+
                        " " +
                        option.getImpact().toString()*/).toLowerCase();

                boolean containsAllWords = true;
                for (String word : targetWords) {
                    boolean containsWord = false;
                    for (String sentenceWord : sentence.toLowerCase().split("\\s+")) {
                        int distance = levenshteinDistance(word, sentenceWord);
                        if (distance <= maxDistance) {
                            containsWord = true;
                            break;
                        }
                        // Starts with match
                        if (sentenceWord.startsWith(word)) {
                            containsWord = true;
                            break;
                        }
                    }
                    if (!containsWord) {
                        containsAllWords = false;
                        break;
                    }
                }
                if (containsAllWords) {
                    result.add(option);
                }
            }
        }

        return result;
    }
}
