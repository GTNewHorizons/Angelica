package org.embeddedt.embeddium.impl.gl.attribute;

import lombok.Getter;

public class GlVertexAttribute {
    @Getter
    private final GlVertexAttributeFormat format;
    @Getter
    private final int count;
    @Getter
    private final int pointer;
    @Getter
    private final int size;
    @Getter
    private final int stride;

    private final boolean normalized;
    private final boolean intType;

    @Getter
    private final String name;

    /**
     * @param format The format used
     * @param count The number of components in the vertex attribute
     * @param normalized Specifies whether or not fixed-point data values should be normalized (true) or used directly
 *                   as fixed-point values (false)
     * @param pointer The offset to the first component in the attribute
     */
    public GlVertexAttribute(GlVertexAttributeFormat format, String name, int count, boolean normalized, int pointer, int stride, boolean intType) {
        this(format, format.size() * count, count, name, normalized, pointer, stride, intType);
    }

    protected GlVertexAttribute(GlVertexAttributeFormat format, int size, int count, String name, boolean normalized, int pointer, int stride, boolean intType) {
        this.format = format;
        this.size = size;
        this.count = count;
        this.normalized = normalized;
        this.pointer = pointer;
        this.stride = stride;
        this.intType = intType;
        this.name = name;
    }

    public boolean isNormalized() {
        return this.normalized;
    }

    public boolean isIntType() {
        return this.intType;
    }
}
