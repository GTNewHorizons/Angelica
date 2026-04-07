package com.prupe.mcpatcher.ctm;

import net.minecraft.util.IIcon;

public class SpriteSheetIcon implements IIcon {

    private final IIcon parent;
    private final int cols;
    private final int rows;
    private final int col;
    private final int row;

    public SpriteSheetIcon(IIcon parent, int cols, int rows, int col, int row) {
        this.parent = parent;
        this.cols = cols;
        this.rows = rows;
        this.col = col;
        this.row = row;
    }

    @Override
    public int getIconWidth() {
        return parent.getIconWidth() / cols;
    }

    @Override
    public int getIconHeight() {
        return parent.getIconHeight() / rows;
    }

    @Override
    public float getMinU() {
        return parent.getMinU() + (parent.getMaxU() - parent.getMinU()) * col / cols;
    }

    @Override
    public float getMaxU() {
        return parent.getMinU() + (parent.getMaxU() - parent.getMinU()) * (col + 1) / cols;
    }

    @Override
    public float getMinV() {
        return parent.getMinV() + (parent.getMaxV() - parent.getMinV()) * row / rows;
    }

    @Override
    public float getMaxV() {
        return parent.getMinV() + (parent.getMaxV() - parent.getMinV()) * (row + 1) / rows;
    }

    @Override
    public float getInterpolatedU(double u) {
        return getMinU() + (getMaxU() - getMinU()) * (float) (u / 16.0);
    }

    @Override
    public float getInterpolatedV(double v) {
        return getMinV() + (getMaxV() - getMinV()) * (float) (v / 16.0);
    }

    @Override
    public String getIconName() {
        return parent.getIconName();
    }
}
