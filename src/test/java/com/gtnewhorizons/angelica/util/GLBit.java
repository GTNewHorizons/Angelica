package com.gtnewhorizons.angelica.util;

import java.util.Objects;

public final class GLBit {

    private final int glEnum;
    private final String name;
    private final boolean initial;

    public GLBit(int glEnum, String name, boolean initial) {
        this.name = name;
        this.glEnum = glEnum;
        this.initial = initial;
    }

    public String name() {return name;}

    public int glEnum() {return glEnum;}

    public boolean initial() {return initial;}

    @Override
    public boolean equals(Object obj) {
        if (obj == this) return true;
        if (obj == null || obj.getClass() != this.getClass()) return false;
        var that = (GLBit) obj;
        return Objects.equals(this.name, that.name) && this.glEnum == that.glEnum && this.initial == that.initial;
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, glEnum, initial);
    }

    @Override
    public String toString() {
        return "GLBit[" + "name=" + name + ", " + "glEnum=" + glEnum + ", " + "initial=" + initial + ']';
    }

}
