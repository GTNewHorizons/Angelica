package com.gtnewhorizons.angelica.client.gui;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

public record DynamicLightsEntityFilter(List<String> modPrefixes, List<String> nameTerms, DynamicLightsEntityFilter.State state) {

    public enum State {
        ANY,
        ENABLED,
        DISABLED
    }

    private static final Pattern WHITESPACE = Pattern.compile("\\s+");
    private static final DynamicLightsEntityFilter MATCH_ALL = new DynamicLightsEntityFilter(List.of(), List.of(), State.ANY);

    public DynamicLightsEntityFilter {
        modPrefixes = List.copyOf(modPrefixes);
        nameTerms = List.copyOf(nameTerms);
    }

    public static DynamicLightsEntityFilter parse(String query) {
        if (query == null) {
            return MATCH_ALL;
        }

        final var normalized = query.strip().toLowerCase(Locale.ROOT);
        if (normalized.isEmpty()) {
            return MATCH_ALL;
        }

        final var modPrefixes = new ArrayList<String>();
        final var nameTerms = new ArrayList<String>();
        var wantEnabled = false;
        var wantDisabled = false;

        for (final var token : WHITESPACE.split(normalized)) {
            switch (token) {
                case "$on" -> wantEnabled = true;
                case "$off" -> wantDisabled = true;
                default -> {
                    if (token.length() > 1 && token.charAt(0) == '@') {
                        modPrefixes.add(token.substring(1));
                    } else {
                        nameTerms.add(token);
                    }
                }
            }
        }

        final var state = wantEnabled == wantDisabled ? State.ANY : wantEnabled ? State.ENABLED : State.DISABLED;
        if (modPrefixes.isEmpty() && nameTerms.isEmpty() && state == State.ANY) {
            return MATCH_ALL;
        }
        return new DynamicLightsEntityFilter(modPrefixes, nameTerms, state);
    }

    public boolean isMatchAll() {
        return modPrefixes.isEmpty() && nameTerms.isEmpty() && state == State.ANY;
    }

    public boolean matches(String lowerName, String lowerModId, boolean enabled) {
        return matchesState(enabled) && matchesAnyMod(lowerModId) && containsAllTerms(lowerName);
    }

    private boolean matchesState(boolean enabled) {
        return switch (state) {
            case ANY -> true;
            case ENABLED -> enabled;
            case DISABLED -> !enabled;
        };
    }

    private boolean matchesAnyMod(String lowerModId) {
        if (modPrefixes.isEmpty()) {
            return true;
        }
        for (final var prefix : modPrefixes) {
            if (lowerModId.startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }

    private boolean containsAllTerms(String lowerName) {
        for (final var term : nameTerms) {
            if (!lowerName.contains(term)) {
                return false;
            }
        }
        return true;
    }
}
