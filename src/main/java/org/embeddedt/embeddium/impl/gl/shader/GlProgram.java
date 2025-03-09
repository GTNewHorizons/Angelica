package org.embeddedt.embeddium.impl.gl.shader;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.embeddedt.embeddium.impl.gl.GlObject;
import org.embeddedt.embeddium.impl.gl.debug.GLDebug;
import org.embeddedt.embeddium.impl.gl.shader.uniform.GlUniform;
import org.embeddedt.embeddium.impl.gl.shader.uniform.GlUniformBlock;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.opengl.GL20C;
import org.lwjgl.opengl.GL30C;
import org.lwjgl.opengl.GL32C;
import org.lwjgl.opengl.GL43C;

import java.util.function.Function;
import java.util.function.IntFunction;

/**
 * An OpenGL shader program.
 */
public class GlProgram<T> extends GlObject implements ShaderBindingContext {
    private static final Logger LOGGER = LogManager.getLogger(GlProgram.class);

    private final T shaderInterface;

    protected GlProgram(int program, Function<ShaderBindingContext, T> interfaceFactory) {
        this.setHandle(program);
        this.shaderInterface = interfaceFactory.apply(this);
    }

    public T getInterface() {
        return this.shaderInterface;
    }

    public static Builder builder(String identifier) {
        return new Builder(identifier);
    }

    public void bind() {
        GL20C.glUseProgram(this.handle());
    }

    public void unbind() {
        GL20C.glUseProgram(0);
    }

    public void delete() {
        GL20C.glDeleteProgram(this.handle());

        this.invalidateHandle();
    }

    @Override
    public <U extends GlUniform<?>> @Nullable U bindUniformIfPresent(String name, IntFunction<U> factory) {
        int index = GL20C.glGetUniformLocation(this.handle(), name);

        if (index < 0) {
            return null;
        }

        return factory.apply(index);
    }

    @Override
    public @Nullable GlUniformBlock bindUniformBlockIfPresent(String name, int bindingPoint) {
        int index = GL32C.glGetUniformBlockIndex(this.handle(), name);

        if (index < 0) {
            return null;
        }

        GL32C.glUniformBlockBinding(this.handle(), index, bindingPoint);

        return new GlUniformBlock(bindingPoint);
    }

    public static class Builder {
        private final String name;
        private final int program;

        public Builder(String name) {
            this.name = name;
            this.program = GL20C.glCreateProgram();
        }

        public Builder attachShader(GlShader shader) {
            GL20C.glAttachShader(this.program, shader.handle());

            return this;
        }

        /**
         * Links the attached shaders to this program and returns a user-defined container which wraps the shader
         * program. This container can, for example, provide methods for updating the specific uniforms of that shader
         * set.
         *
         * @param factory The factory which will create the shader program's interface
         * @param <U> The interface type for the shader program
         * @return An instantiated shader container as provided by the factory
         */
        public <U> GlProgram<U> link(Function<ShaderBindingContext, U> factory) {
            GL20C.glLinkProgram(this.program);

            String log = GL20C.glGetProgramInfoLog(this.program);

            if (!log.isEmpty()) {
                LOGGER.warn("Program link log for " + this.name + ": " + log);
            }

            int result = GL20C.glGetProgrami(this.program, GL20C.GL_LINK_STATUS);

            if (result != GL20C.GL_TRUE) {
                throw new RuntimeException("Shader program linking failed, see log for details");
            }

            GLDebug.nameObject(GL43C.GL_PROGRAM, this.program, this.name);

            return new GlProgram<>(this.program, factory);
        }

        public Builder bindAttribute(String name, int index) {
            GL20C.glBindAttribLocation(this.program, index, name);

            return this;
        }

        public Builder bindFragmentData(String name, int index) {
            GL30C.glBindFragDataLocation(this.program, index, name);

            return this;
        }
    }
}
