package me.jellysquid.mods.sodium.client.gl.func;

import org.lwjgl.opengl.ARBVertexArrayObject;
import org.lwjgl.opengl.ContextCapabilities;
import org.lwjgl.opengl.GL30;

/**
 * Requires OpenGL 3.0+ or the ARB_vertex_array_object extension.
 */
public enum GlVertexArrayFunctions {
    BASE {
        @Override
        public void glBindVertexArray(int id) {
            GL30.glBindVertexArray(id);
        }

        @Override
        public int glGenVertexArrays() {
            return GL30.glGenVertexArrays();
        }

        @Override
        public void glDeleteVertexArrays(int id) {
            GL30.glDeleteVertexArrays(id);
        }
    },
    ARB {
        @Override
        public void glBindVertexArray(int id) {
            ARBVertexArrayObject.glBindVertexArray(id);
        }

        @Override
        public int glGenVertexArrays() {
            return ARBVertexArrayObject.glGenVertexArrays();
        }

        @Override
        public void glDeleteVertexArrays(int id) {
            ARBVertexArrayObject.glDeleteVertexArrays(id);
        }
    },
    UNSUPPORTED {
        @Override
        public void glBindVertexArray(int id) {
            throw new UnsupportedOperationException();
        }

        @Override
        public int glGenVertexArrays() {
            throw new UnsupportedOperationException();
        }

        @Override
        public void glDeleteVertexArrays(int id) {
            throw new UnsupportedOperationException();
        }
    };

    static GlVertexArrayFunctions load(ContextCapabilities capabilities) {
        if (capabilities.OpenGL30) {
            return BASE;
        } else if (capabilities.GL_ARB_vertex_array_object) {
            return ARB;
        }

        return UNSUPPORTED;
    }

    public abstract void glBindVertexArray(int id);

    public abstract int glGenVertexArrays();

    public abstract void glDeleteVertexArrays(int id);
}
