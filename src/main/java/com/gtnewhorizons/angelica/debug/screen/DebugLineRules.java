package com.gtnewhorizons.angelica.debug.screen;

import com.gtnewhorizons.angelica.AngelicaMod;

import java.util.ArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class DebugLineRules {

    private static final int MAX_PREFIX = 64;
    private static final Pattern RULE = Pattern.compile("\\s*(drop|fact)\\s+(left|right)\\s+\"([^\"]*)\"((?:\\s+\\w+=(?:\"[^\"]*\"|[^\\s\"]+))*)\\s*");
    private static final Pattern OPTION = Pattern.compile("(\\w+)=(?:\"([^\"]*)\"|([^\\s\"]+))");

    static final String[] DEFAULTS = {
        "drop right \"Allocation rate: \" when=minecraft:memory",
        "drop right \"Java: \" when=minecraft:system_specs",
        "drop right \"GPU: \" when=minecraft:system_specs",
        "drop right \"OpenGL: \" when=minecraft:system_specs",
        "drop right \"CPU Cores: \" when=minecraft:system_specs",
        "fact right \"IntCache: \" group=angelica:memory when=minecraft:memory",
        "fact right \"OS: \" group=angelica:system_specs when=minecraft:system_specs",
        "fact right \"LWJGL: \" group=angelica:system_specs when=minecraft:system_specs",
        "fact left \"Cube X: \" group=angelica:position when=minecraft:player_position",
        "fact left \"Cube Y: \" group=angelica:position when=minecraft:player_position",
        "fact left \"Cube Z: \" group=angelica:position when=minecraft:player_position",
        "fact left \"Cube: \" group=angelica:position when=minecraft:player_position",
        "fact left \"hunger: \" group=angelica:position when=minecraft:player_position name=\"Hunger\"",
        "fact right \"HydroEnergy GPU RAM: \" group=angelica:memory when=minecraft:memory",
        "fact right \"renderDebugMode: \" group=angelica:rendering when=minecraft:chunk_render_stats name=\"Render Debug Mode\"",
    };

    public record Rule(String prefix, DebugEntryDef when, DebugGroup group, String name) {

        int payloadStart(String line) {
            int i = matchPrefix(line, skipCodes(line, 0), prefix);
            while (i < line.length() && line.charAt(i) == ' ') i++;
            return i;
        }
    }

    private final Rule[][] rules;

    private DebugLineRules(Rule[][] rules) {
        this.rules = rules;
    }

    static DebugLineRules compile(DebugRegistry registry, String[] configRules) {
        final ArrayList<Rule> left = new ArrayList<>();
        final ArrayList<Rule> right = new ArrayList<>();
        if (configRules != null) {
            for (String text : configRules) add(registry, text, left, right);
        }
        for (String text : DEFAULTS) add(registry, text, left, right);
        return new DebugLineRules(new Rule[][] { left.toArray(new Rule[0]), right.toArray(new Rule[0]) });
    }

    private static void add(DebugRegistry registry, String text, ArrayList<Rule> left, ArrayList<Rule> right) {
        final Matcher matcher = text == null ? null : RULE.matcher(text);
        final Rule rule = matcher != null && matcher.matches() ? parse(registry, matcher) : null;
        if (rule == null) {
            AngelicaMod.LOGGER.warn("invalid debug line rule: {}", text);
        } else {
            (matcher.group(2).equals("left") ? left : right).add(rule);
        }
    }

    private static Rule parse(DebugRegistry registry, Matcher rule) {
        final String prefix = stripCodes(rule.group(3));
        if (prefix.isEmpty() || prefix.length() > MAX_PREFIX) return null;
        String groupId = null;
        String name = null;
        String whenId = null;
        final Matcher option = OPTION.matcher(rule.group(4));
        while (option.find()) {
            final String value = option.group(2) != null ? option.group(2) : option.group(3);
            switch (option.group(1)) {
                case "group" -> groupId = value;
                case "name" -> name = value;
                case "when" -> whenId = value;
                default -> {
                    return null;
                }
            }
        }
        final DebugEntryDef when = whenId == null ? null : registry.entryById(whenId);
        if (whenId != null && when == null) return null;
        if (rule.group(1).equals("drop")) return groupId == null && name == null ? new Rule(prefix, when, null, null) : null;
        final DebugGroup group = groupId == null ? null : registry.groupById(groupId);
        if (name == null) name = defaultName(prefix);
        return group == null || name.isEmpty() ? null : new Rule(prefix, when, group, name);
    }

    private static String stripCodes(String s) {
        if (s.indexOf(DebugText.FORMAT) < 0) return s;
        final StringBuilder sb = new StringBuilder(s.length());
        DebugText.appendStripped(sb, s);
        return sb.toString();
    }

    private static String defaultName(String prefix) {
        int end = prefix.length();
        while (end > 0 && prefix.charAt(end - 1) == ' ') end--;
        if (end > 0 && prefix.charAt(end - 1) == ':') end--;
        return prefix.substring(0, end);
    }

    static int skipCodes(String s, int from) {
        final int last = s.length() - 1;
        int i = from;
        while (i < last && s.charAt(i) == DebugText.FORMAT) i += 2;
        return i;
    }

    static int matchPrefix(String line, int rawStart, String prefix) {
        final int length = line.length();
        int i = rawStart;
        for (int p = 0, n = prefix.length(); p < n; p++) {
            i = skipCodes(line, i);
            if (i >= length || line.charAt(i) != prefix.charAt(p)) return -1;
            i++;
        }
        return i;
    }

    static boolean isBlank(String line) {
        final int length = line.length();
        for (int i = skipCodes(line, 0); i < length; i = skipCodes(line, i + 1)) {
            if (line.charAt(i) > ' ') return false;
        }
        return true;
    }

    public Rule match(int side, String line) {
        final int start = skipCodes(line, 0);
        for (Rule rule : rules[side]) {
            if ((rule.when == null || rule.when.isEnabled()) && matchPrefix(line, start, rule.prefix) >= 0) return rule;
        }
        return null;
    }
}
