package com.gtnewhorizons.angelica.client.gui.utils;

public class Rect2i {
    private final int x;
    private final int y;
    private final int width;
    private final int height;

    public Rect2i(int i, int j, int k, int l) {
        this.x = i;
        this.y = j;
        this.width = k;
        this.height = l;
    }

    public int getX() {
        return this.x;
    }

    public int getY() {
        return this.y;
    }

    public int getWidth() {
        return this.width;
    }

    public int getHeight() {
        return this.height;
    }

    public boolean contains(int x, int y) {
        return x >= this.x && x <= this.x + this.width && y >= this.y && y <= this.y + this.height;
    }
}
