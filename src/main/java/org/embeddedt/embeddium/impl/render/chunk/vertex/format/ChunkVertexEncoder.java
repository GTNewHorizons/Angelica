package org.embeddedt.embeddium.impl.render.chunk.vertex.format;

import org.embeddedt.embeddium.impl.render.chunk.terrain.material.Material;

public interface ChunkVertexEncoder {
    long write(long ptr, Material material, Vertex vertex, int sectionIndex);

    class Vertex {
        public float x;
        public float y;
        public float z;
        public int color;
        public float u;
        public float v;
        public int light;
        /**
         * The normal that vanilla would output for this quad. Unused by Embeddium's built-in shaders, but might be used
         * by a core shader.
         */
        public int vanillaNormal;
        /**
         * The actual normal vector of this quad computed off the geometry.
         */
        public int trueNormal;

        public static Vertex[] uninitializedQuad() {
            Vertex[] vertices = new Vertex[4];

            for (int i = 0; i < 4; i++) {
                vertices[i] = new Vertex();
            }

            return vertices;
        }
    }
}
