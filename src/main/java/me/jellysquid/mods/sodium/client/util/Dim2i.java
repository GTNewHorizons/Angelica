package me.jellysquid.mods.sodium.client.util;

public class Dim2i {
    private int x;
    private int y;
    private int width;
    private int height;

    public Dim2i(int x, int y, int width, int height) {
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
    }

    public int getOriginX() {
        return this.x;
    }

    public int getOriginY() {
        return this.y;
    }

    public int getWidth() {
        return this.width;
    }

    public int getHeight() {
        return this.height;
    }

    public int getLimitX() {
        return this.x + this.width;
    }

    public int getLimitY() {
        return this.y + this.height;
    }

    public boolean containsCursor(double x, double y) {
        return x >= this.x && x < this.getLimitX() && y >= this.y && y < this.getLimitY();
    }

    public int getCenterX() {
        return this.x + (this.width / 2);
    }

    public int getCenterY() {
        return this.y + (this.height / 2);
    }

    public void setX(int x) {
        this.x = x;
    }

    public void setY(int y) {
        this.y = y;
    }

    public void setWidth(int width) {
        this.width = width;
    }

    public void setHeight(int height) {
        this.height = height;
    }

    public boolean canFitDimension(Dim2i anotherDim) {
        return this.x <= anotherDim.getOriginX() && this.y <= anotherDim.getOriginY() && this.getLimitX() >= anotherDim.getLimitX() && this.getLimitY() >= anotherDim.getLimitY();
    }

    public boolean overlapWith(Dim2i other) {
        return this.x < other.getLimitX() && this.getLimitX() > other.getOriginX() && this.y < other.getLimitY() && this.getLimitY() > other.getOriginY();
    }
}
