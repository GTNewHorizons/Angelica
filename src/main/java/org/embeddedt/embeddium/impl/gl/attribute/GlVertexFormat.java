package org.embeddedt.embeddium.impl.gl.attribute;

import it.unimi.dsi.fastutil.objects.Reference2ReferenceLinkedOpenHashMap;
import lombok.AllArgsConstructor;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;

/**
 * Provides a generic vertex format which contains attributes. Other code can then retrieve
 * the attributes and work with encoded data in a generic manner without needing to rely on a specific format.
 */
@AllArgsConstructor
public class GlVertexFormat {
    /**
     * Magic value that will have GlVertexFormat calculate the next pointer to use.
     */
    public static final int NEXT_ALIGNED_POINTER = -1;
    /**
     * The required alignment of attributes, must be a power of two.
     */
    private static final int ATTRIBUTE_ALIGNMENT = 4;

    private final Reference2ReferenceLinkedOpenHashMap<String, GlVertexAttribute> attributesKeyed;

    private final int stride;

    public static Builder builder(int stride) {
        return new Builder(stride);
    }

    /**
     * Returns the {@link GlVertexAttribute} of this vertex format bound to the type {@param name}.
     * @throws NullPointerException If the attribute does not exist in this format
     */
    public GlVertexAttribute getAttribute(String name) {
        GlVertexAttribute attr = this.attributesKeyed.get(name);

        if (attr == null) {
            throw new NullPointerException("No attribute exists for " + name);
        }

        return attr;
    }

    public Collection<GlVertexAttribute> getAttributes() {
        return Collections.unmodifiableCollection(this.attributesKeyed.values());
    }

    /**
     * @return The stride (or the size of) the vertex format in bytes
     */
    public int getStride() {
        return this.stride;
    }

    @Override
    public String toString() {
        return String.format("GlVertexFormat{attributes=%d,stride=%d}",
                this.attributesKeyed.size(), this.stride);
    }

    public static class Builder {
        private final Reference2ReferenceLinkedOpenHashMap<String, GlVertexAttribute> attributes;
        private final int stride;

        public Builder(int stride) {
            this.attributes = new Reference2ReferenceLinkedOpenHashMap<>();
            this.stride = stride;
        }

        public Builder addAllElements(GlVertexFormat otherFormat) {
            for (var attribute : otherFormat.getAttributes()) {
                this.addElement(new GlVertexAttribute(attribute.getFormat(), attribute.getName(), attribute.getCount(), attribute.isNormalized(), attribute.getPointer(), this.stride, attribute.isIntType()));
            }
            return this;
        }

        private int findNextPointer() {
            int nextPtr = this.attributes.values().stream().mapToInt(a -> a.getPointer() + a.getSize()).max().orElse(0);
            return (nextPtr + (ATTRIBUTE_ALIGNMENT - 1)) & ~(ATTRIBUTE_ALIGNMENT - 1);
        }

        public Builder addElement(String name, int pointer, GlVertexAttributeFormat format, int count, boolean normalized, boolean intType) {
            if (pointer == NEXT_ALIGNED_POINTER) {
                pointer = this.findNextPointer();
            }
            return this.addElement(new GlVertexAttribute(format, name, count, normalized, pointer, this.stride, intType));
        }

        /**
         * Adds an vertex attribute which will be bound to the given generic attribute type.
         *
         * @param attribute The attribute to bind
         * @throws IllegalStateException If an attribute is already bound to the generic type
         */
        private Builder addElement(GlVertexAttribute attribute) {
            if (attribute.getPointer() >= this.stride) {
                throw new IllegalArgumentException("Element " + attribute.getName() + " starts outside vertex format (" + attribute.getPointer() + ", stride is " + this.stride + ")");
            }

            if (attribute.getPointer() + attribute.getSize() > this.stride) {
                throw new IllegalArgumentException("Element extends outside vertex format");
            }

            if (this.attributes.put(attribute.getName(), attribute) != null) {
                throw new IllegalStateException("Generic attribute " + attribute.getName() + " already defined in vertex format");
            }

            return this;
        }

        /**
         * Creates a {@link GlVertexFormat} from the current builder.
         */
        public GlVertexFormat build() {
            if (this.attributes.isEmpty()) {
                throw new IllegalArgumentException("Attempted to construct vertex format with no attributes");
            }

            ArrayList<GlVertexAttribute> allAttributes = new ArrayList<>(this.attributes.values());

            allAttributes.sort(Comparator.comparingInt(GlVertexAttribute::getPointer));

            // Check that no attributes overlap

            GlVertexAttribute prevAttribute = null;

            for (var attribute : allAttributes) {
                if (prevAttribute != null && attribute.getPointer() < (prevAttribute.getPointer() + prevAttribute.getSize())) {
                    throw new IllegalArgumentException("Attribute " + attribute.getName() + " overlaps with " + prevAttribute.getName());
                }
                prevAttribute = attribute;
            }

            var lastAttribute = allAttributes.get(allAttributes.size() - 1);

            // The stride must be large enough to cover all attributes. This still allows for additional padding
            // to be added to the end of the vertex to accommodate alignment restrictions.
            if (this.stride < (lastAttribute.getPointer() + lastAttribute.getSize())) {
                throw new IllegalArgumentException("Stride is too small");
            }

            return new GlVertexFormat(this.attributes, this.stride);
        }
    }
}
