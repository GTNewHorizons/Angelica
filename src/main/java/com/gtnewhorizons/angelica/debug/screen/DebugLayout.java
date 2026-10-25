package com.gtnewhorizons.angelica.debug.screen;

public final class DebugLayout {

    public static final int MARGIN_TOP = 2;
    public static final int GROUP_SPACING = 9;

    public final int[] height;
    public final int[] side;
    public final int[] top;
    public final int[] columnHeight = new int[2];

    private final int[] maxHeight = new int[2];
    private final int[] homeSide;
    private final int count;
    private final int priority;

    public DebugLayout(DebugRegistry registry) {
        count = registry.groupCount();
        height = new int[count];
        side = new int[count];
        top = new int[count];
        homeSide = new int[count];
        for (int g = 0; g < count; g++) {
            homeSide[g] = registry.groupAt(g).side;
        }
        final DebugGroup priorityGroup = registry.groupById(DebugGroup.PRIORITY_LEFT_ID);
        priority = priorityGroup == null ? -1 : priorityGroup.index;
    }

    private void place(int group, int column) {
        top[group] = columnHeight[column];
        side[group] = column;
        columnHeight[column] += height[group] + GROUP_SPACING;
    }

    private boolean isFull(int column) {
        return columnHeight[column] >= maxHeight[column];
    }

    private boolean fits(int group, int column) {
        return columnHeight[column] + height[group] <= maxHeight[column];
    }

    public void assign(int top, int leftMaxHeight, int rightMaxHeight) {
        columnHeight[DebugGroup.LEFT] = top;
        columnHeight[DebugGroup.RIGHT] = top;
        maxHeight[DebugGroup.LEFT] = leftMaxHeight;
        maxHeight[DebugGroup.RIGHT] = rightMaxHeight;
        for (int g = 0; g < count; g++) {
            side[g] = -1;
        }
        if (priority >= 0 && height[priority] > 0) place(priority, homeSide[priority]);
        for (int g = 0; g < count; g++) {
            if (height[g] <= 0 || side[g] >= 0) continue;
            final int column = homeSide[g];
            if (fits(g, column)) place(g, column);
        }
        for (int g = 0; g < count; g++) {
            if (height[g] <= 0 || side[g] >= 0) continue;
            final int column = 1 - homeSide[g];
            if (fits(g, column)) place(g, column);
        }
        for (int g = 0; g < count; g++) {
            if (height[g] <= 0 || side[g] >= 0) continue;
            final int column = homeSide[g];
            if (!isFull(column)) place(g, column);
            else if (!isFull(1 - column)) place(g, 1 - column);
        }
    }

    public void assignSingleColumn(int column, int columnTop, int columnMaxHeight) {
        columnHeight[column] = columnTop;
        maxHeight[column] = columnMaxHeight;
        for (int g = 0; g < count; g++) {
            side[g] = -1;
        }
        if (priority >= 0 && height[priority] > 0) place(priority, column);
        for (int g = 0; g < count; g++) {
            if (height[g] <= 0 || side[g] >= 0) continue;
            if (!isFull(column)) place(g, column);
        }
    }
}
