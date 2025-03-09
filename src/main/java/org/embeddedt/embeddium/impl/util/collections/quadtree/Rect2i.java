package org.embeddedt.embeddium.impl.util.collections.quadtree;

import java.util.Objects;

public class Rect2i {
    protected final int x;
    protected final int y;
    protected final int width;
    protected final int height;

    public Rect2i(int x, int y, int width, int height) {
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
    }

    public Rect2i(Rect2i other) {
        this(other.x(), other.y(), other.width(), other.height());
    }

    public boolean contains(int x, int y) {
        return x >= this.x && y >= this.y && x < this.x + this.width && y < this.y + this.height;
    }

    public boolean contains(Rect2i r2)
    {
        Rect2i r1 = this;
        return r1.contains(r2.x(), r2.y()) && r1.contains(r2.x() + r2.width(), r2.y() + r2.height());
    }

    public int x() {
        return x;
    }

    public int y() {
        return y;
    }

    public int width() {
        return width;
    }

    public int height() {
        return height;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == this) return true;
        if (obj == null || obj.getClass() != this.getClass()) return false;
        var that = (Rect2i) obj;
        return this.x == that.x &&
                this.y == that.y &&
                this.width == that.width &&
                this.height == that.height;
    }

    @Override
    public int hashCode() {
        return Objects.hash(x, y, width, height);
    }

    @Override
    public String toString() {
        return "Rect2i[" +
                "x=" + x + ", " +
                "y=" + y + ", " +
                "width=" + width + ", " +
                "height=" + height + ']';
    }

}
