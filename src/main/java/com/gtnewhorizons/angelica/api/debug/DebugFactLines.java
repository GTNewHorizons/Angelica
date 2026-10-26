package com.gtnewhorizons.angelica.api.debug;

import java.util.Arrays;

public final class DebugFactLines {

    private static final int MAX_LABEL = 24;
    private static final String COLON = ": ";
    private static final String DASH = " - ";

    private String[] sources = new String[8];
    private String[] names = new String[8];
    private String[] values = new String[8];
    private int cursor;
    private int size;
    private boolean open;

    public void begin() {
        cursor = 0;
        open = true;
    }

    public void end() {
        open = false;
        if (cursor < size) {
            Arrays.fill(sources, cursor, size, null);
            Arrays.fill(names, cursor, size, null);
            Arrays.fill(values, cursor, size, null);
        }
        size = cursor;
    }

    public void fact(String name, String value) {
        requireOpen();
        if (value == null) throw new IllegalArgumentException("Debug fact value is null");
        reserve();
        sources[cursor] = null;
        names[cursor] = name;
        values[cursor] = value;
        cursor++;
    }

    public void line(String line) {
        fact(null, line);
    }

    public void split(String line) {
        requireOpen();
        if (line == null) throw new IllegalArgumentException("Debug fact line is null");
        reserve();
        if (line.equals(sources[cursor])) {
            cursor++;
            return;
        }
        sources[cursor] = line;
        parse(line);
        cursor++;
    }

    public void emit(DebugScreenDisplayer displayer, DebugGroupHandle group) {
        if (open) throw new IllegalStateException("DebugFactLines.emit() called before end()");
        for (int i = 0; i < size; i++) {
            final String value = values[i];
            final String name = names[i];
            if (name == null) {
                displayer.line(group).text(value);
            } else {
                displayer.fact(group, name).value(value);
            }
        }
    }

    private void parse(String line) {
        int at = labelEnd(line, COLON);
        if (at >= 0) {
            names[cursor] = name(line, at);
            values[cursor] = line.substring(at + COLON.length());
            return;
        }
        at = labelEnd(line, DASH);
        if (at >= 0) {
            names[cursor] = name(line, at);
            values[cursor] = line.substring(at + DASH.length());
            return;
        }
        names[cursor] = null;
        values[cursor] = line;
    }

    private String name(String line, int end) {
        final String previous = names[cursor];
        return previous != null && previous.length() == end && line.startsWith(previous) ? previous : line.substring(0, end);
    }

    private void requireOpen() {
        if (!open) throw new IllegalStateException("DebugFactLines is not open: call begin() first");
    }

    private void reserve() {
        if (cursor == sources.length) {
            final int capacity = cursor * 2;
            sources = Arrays.copyOf(sources, capacity);
            names = Arrays.copyOf(names, capacity);
            values = Arrays.copyOf(values, capacity);
        }
    }

    static int labelEnd(String line, String separator) {
        final int at = line.indexOf(separator);
        if (at <= 0 || at > MAX_LABEL) return -1;
        for (int i = 0; i < at; i++) {
            final char c = line.charAt(i);
            if (c == '=' || c == '(') return -1;
        }
        return at;
    }
}
