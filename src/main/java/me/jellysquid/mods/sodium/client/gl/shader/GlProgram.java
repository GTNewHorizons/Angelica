package me.jellysquid.mods.sodium.client.gl.shader;

import com.mojang.blaze3d.platform.GlStateManager;
import me.jellysquid.mods.sodium.client.gl.GlObject;
import me.jellysquid.mods.sodium.client.gl.device.RenderDevice;
import net.minecraft.util.Identifier;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.lwjgl.opengl.GL20C;

/**
 * An OpenGL shader program.
 */
public abstract class GlProgram extends GlObject {
    private static final Logger LOGGER = LogManager.getLogger(GlProgram.class);

    private final Identifier name;

    protected GlProgram(RenderDevice owner, Identifier name, int program) {
        super(owner);

        this.name = name;
        this.setHandle(program);
    }

    public static Builder builder(Identifier identifier) {
        return new Builder(identifier);
    }

    public void bind() {
    	GlStateManager.useProgram(this.handle());
    }

    public void unbind() {
    	GlStateManager.useProgram(0);
    }

    public Identifier getName() {
        return this.name;
    }

    /**
     * Retrieves the index of the uniform with the given name.
     * @param name The name of the uniform to find the index of
     * @return The uniform's index
     * @throws NullPointerException If no uniform exists with the given name
     */
    public int getUniformLocation(String name) {
        int index = GlStateManager.getUniformLocation(this.handle(), name);

        if (index < 0) {
            throw new NullPointerException("No uniform exists with name: " + name);
        }

        return index;
    }

    public void delete() {
    	GlStateManager.deleteProgram(this.handle());

        this.invalidateHandle();
    }

    public static class Builder {
        private final Identifier name;
        private final int program;

        public Builder(Identifier name) {
            this.name = name;
            this.program = GlStateManager.createProgram();
        }

        public Builder attachShader(GlShader shader) {
        	GlStateManager.attachShader(this.program, shader.handle());

            return this;
        }

        /**
         * Links the attached shaders to this program and returns a user-defined container which wraps the shader
         * program. This container can, for example, provide methods for updating the specific uniforms of that shader
         * set.
         *
         * @param factory The factory which will create the shader program's container
         * @param <P> The type which should be instantiated with the new program's handle
         * @return An instantiated shader container as provided by the factory
         */
        public <P extends GlProgram> P build(ProgramFactory<P> factory) {
        	GlStateManager.linkProgram(this.program);

            String log = GL20C.glGetProgramInfoLog(this.program);

            if (!log.isEmpty()) {
                LOGGER.warn("Program link log for " + this.name + ": " + log);
            }

            int result = GlStateManager.getProgram(this.program, GL20C.GL_LINK_STATUS);

            if (result != GL20C.GL_TRUE) {
                throw new RuntimeException("Shader program linking failed, see log for details");
            }

            return factory.create(this.name, this.program);
        }

        public Builder bindAttribute(String name, ShaderBindingPoint binding) {
            GL20C.glBindAttribLocation(this.program, binding.getGenericAttributeIndex(), name);

            return this;
        }
    }

    public interface ProgramFactory<P extends GlProgram> {
        P create(Identifier name, int handle);
    }
}
