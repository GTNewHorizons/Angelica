package com.gtnewhorizons.angelica.debug.screen;

import java.util.Arrays;

public final class DebugGroupContents {

    public static final int TEXT = 1;
    public static final int SIZE = 2;

    public static final int ROW_HEIGHT = 9;
    private static final int SHRINK_HOLD_TICKS = 40;
    public static final int MARGIN = 3;
    static final int TITLE_PADDING = 3;
    static final int FACT_PADDING = 5;
    static final int FACT_LINE_GAP = 2;
    static final int MIN_VALUE_BUDGET = 32;

    static final int TITLE_BAR_COLOR = 0x90303030;
    static final int BODY_COLOR = 0x90505050;
    static final int TITLE_COLOR = 0xFFFFFFFF;
    static final int NAME_COLOR = 0xFFE0E0E0;
    static final int VALUE_COLOR = 0xFFD0D0D0;
    static final int LINE_COLOR = 0xFFE0E0E0;

    private static final Slot[] NO_SLOTS = new Slot[0];

    public static final class Slot {

        StringBuilder text = new StringBuilder();
        private StringBuilder previous = new StringBuilder();
        final StringBuilder wrapped = new StringBuilder();
        int[] rowStart = new int[1];
        int[] rowEnd = new int[1];
        int rowCount;
        int width;
        String name;
        String label;
        int nameWidth;
        private int wrapBudget = -1;
        private boolean isWrapped;
        private boolean dirty;

        void begin() {
            final StringBuilder swap = previous;
            previous = text;
            text = swap;
            text.setLength(0);
            dirty = true;
        }

        private boolean sameAsPrevious() {
            return CharSequence.compare(text, previous) == 0;
        }

        boolean measure(DebugText renderer, int budget, boolean force) {
            boolean textChanged = false;
            if (dirty) {
                dirty = false;
                textChanged = !sameAsPrevious();
            }
            if (!force && !textChanged && budget == wrapBudget) return false;
            wrapBudget = budget;
            rowCount = 0;
            width = 0;
            final int len = text.length();
            final float full = renderer.measure(text, 0, len);
            if (full <= budget) {
                isWrapped = false;
                addRow(0, len, full);
            } else {
                isWrapped = true;
                renderer.wrap(this, budget);
            }
            return true;
        }

        void addRow(int start, int end, float rowWidth) {
            if (rowCount == rowStart.length) {
                final int n = rowCount * 2;
                rowStart = Arrays.copyOf(rowStart, n);
                rowEnd = Arrays.copyOf(rowEnd, n);
            }
            rowStart[rowCount] = start;
            rowEnd[rowCount] = end;
            rowCount++;
            final int w = (int) Math.ceil(rowWidth);
            if (w > width) width = w;
        }

        CharSequence rowText() {
            return isWrapped ? wrapped : text;
        }
    }

    public final DebugGroup group;
    private Slot[] facts = NO_SLOTS;
    private int factCount;
    private Slot[] lines = NO_SLOTS;
    private int lineCount;
    private int measuredFactCount;
    private int measuredLineCount;
    private String title = "";
    private int titleWidth;
    private boolean titleDirty;
    private int factNameWidth;
    private int fullWidth;
    private int fullHeight;
    private boolean wasEmpty = true;
    private int widthHeldSince;
    private int heightHeldSince;
    private boolean holdReleased;

    public DebugGroupContents(DebugGroup group) {
        this.group = group;
    }

    public void reset() {
        factCount = 0;
        lineCount = 0;
    }

    public boolean isEmpty() {
        return factCount == 0 && lineCount == 0;
    }

    public int lineCount() {
        return lineCount;
    }

    public int factCount() {
        return factCount;
    }

    public Slot fact(int i) {
        return facts[i];
    }

    public Slot line(int i) {
        return lines[i];
    }

    public int fullWidth() {
        return fullWidth;
    }

    public int fullHeight() {
        return fullHeight;
    }

    void releaseSizeHold() {
        holdReleased = true;
    }

    void setTitle(String title, int width) {
        if (this.title.equals(title) && titleWidth == width) return;
        this.title = title;
        this.titleWidth = width;
        titleDirty = true;
    }

    private static Slot[] grow(Slot[] slots, int count) {
        if (count < slots.length) return slots;
        final int n = Math.max(4, count * 2);
        final Slot[] grown = Arrays.copyOf(slots, n);
        for (int i = count; i < n; i++) grown[i] = new Slot();
        return grown;
    }

    Slot addFact(String name) {
        facts = grow(facts, factCount);
        final Slot slot = facts[factCount++];
        if (slot.name != name) {
            slot.name = name;
            slot.label = name.concat(":");
            slot.nameWidth = -1;
        }
        slot.begin();
        return slot;
    }

    Slot addLine() {
        lines = grow(lines, lineCount);
        final Slot slot = lines[lineCount++];
        slot.begin();
        return slot;
    }

    int measure(DebugText renderer, int lineBudget, boolean force, int tick) {
        boolean text = force || titleDirty || factCount != measuredFactCount || lineCount != measuredLineCount;
        titleDirty = false;
        measuredFactCount = factCount;
        measuredLineCount = lineCount;
        int nameWidth = 0;
        for (int i = 0; i < factCount; i++) {
            final Slot slot = facts[i];
            if (force || slot.nameWidth < 0) {
                slot.nameWidth = renderer.width(slot.name, 0, slot.name.length());
                text = true;
            }
            if (slot.nameWidth > nameWidth) nameWidth = slot.nameWidth;
        }
        if (nameWidth != factNameWidth) text = true;
        factNameWidth = nameWidth;
        final int valueBudget = Math.max(lineBudget - nameWidth - FACT_PADDING, MIN_VALUE_BUDGET);
        int rows = 0;
        int width = 0;
        for (int i = 0; i < factCount; i++) {
            final Slot slot = facts[i];
            text |= slot.measure(renderer, valueBudget, force);
            rows += slot.rowCount;
            final int w = nameWidth + FACT_PADDING + slot.width;
            if (w > width) width = w;
        }
        for (int i = 0; i < lineCount; i++) {
            final Slot slot = lines[i];
            text |= slot.measure(renderer, lineBudget, force);
            rows += slot.rowCount;
            if (slot.width > width) width = slot.width;
        }
        if (titleWidth > 0 && titleWidth + TITLE_PADDING > width) width = titleWidth + TITLE_PADDING;
        int height = rows * ROW_HEIGHT;
        if (titleWidth > 0) height += ROW_HEIGHT;
        if (factCount > 0 && lineCount > 0) height += FACT_LINE_GAP;
        final boolean empty = isEmpty();
        final boolean free = force || holdReleased;
        holdReleased = false;
        if (free || empty || width >= fullWidth || tick - widthHeldSince >= SHRINK_HOLD_TICKS) {
            widthHeldSince = tick;
        } else {
            width = fullWidth;
        }
        if (free || empty || height >= fullHeight || tick - heightHeldSince >= SHRINK_HOLD_TICKS) {
            heightHeldSince = tick;
        } else {
            height = fullHeight;
        }
        int flags = text ? TEXT : 0;
        if (width != fullWidth || height != fullHeight || empty != wasEmpty) flags |= SIZE;
        fullWidth = width;
        fullHeight = height;
        wasEmpty = empty;
        return flags;
    }

    void emitRects(DebugText renderer, int left, int top, int side, int screenWidth) {
        int y = top;
        if (titleWidth > 0) {
            renderer.rect(left - 1, top - 1, left + fullWidth + 1, top + ROW_HEIGHT - 1, TITLE_BAR_COLOR);
            y = top + ROW_HEIGHT;
        }
        renderer.rect(left - 1, y - 1, left + fullWidth + 1, top + fullHeight + 1, BODY_COLOR);
        if (group.hasAccent()) {
            final int x = side == DebugGroup.LEFT ? 0 : screenWidth - 1;
            renderer.rect(x, top - 1, x + 1, top + fullHeight + 1, 0xFF000000 | group.accentColor);
        }
    }

    void drawText(DebugText renderer, int left, int top) {
        int y = top;
        if (titleWidth > 0) {
            renderer.draw(title, 0, title.length(), left + TITLE_PADDING, top, TITLE_COLOR);
            y += ROW_HEIGHT;
        }
        final int valueX = left + factNameWidth + FACT_PADDING;
        for (int i = 0; i < factCount; i++) {
            final Slot slot = facts[i];
            renderer.draw(slot.label, 0, slot.label.length(), left + (factNameWidth - slot.nameWidth), y, NAME_COLOR);
            y = drawRows(renderer, slot, valueX, y, VALUE_COLOR);
        }
        if (factCount > 0 && lineCount > 0) y += FACT_LINE_GAP;
        for (int i = 0; i < lineCount; i++) {
            y = drawRows(renderer, lines[i], left, y, LINE_COLOR);
        }
    }

    private static int drawRows(DebugText renderer, Slot slot, int x, int y, int color) {
        final CharSequence text = slot.rowText();
        for (int row = 0; row < slot.rowCount; row++) {
            final int start = slot.rowStart[row];
            final int end = slot.rowEnd[row];
            if (end > start) renderer.draw(text, start, end, x, y, color);
            y += ROW_HEIGHT;
        }
        return y;
    }
}
