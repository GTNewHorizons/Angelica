package me.jellysquid.mods.sodium.client.gl.shader;

import com.gtnewhorizons.angelica.config.AngelicaConfig;
import com.gtnewhorizons.angelica.glsm.GLDebug;
import me.jellysquid.mods.sodium.client.gl.GlObject;
import me.jellysquid.mods.sodium.client.gl.device.RenderDevice;
import net.minecraft.util.ResourceLocation;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.KHRDebug;

/**
 * An OpenGL shader program.
 */
public abstract class GlProgram extends GlObject {
    private static final Logger LOGGER = LogManager.getLogger(GlProgram.class);

    private final ResourceLocation name;

    protected GlProgram(RenderDevice owner, ResourceLocation name, int program) {
        super(owner);

        this.name = name;
        this.setHandle(program);
    }

    public static Builder builder(ResourceLocation identifier) {
        return new Builder(identifier);
    }

    public void bind() {
        GL20.glUseProgram(this.handle());
    }

    public void unbind() {
    	GL20.glUseProgram(0);
    }

    public ResourceLocation getName() {
        return this.name;
    }

    /**
     * Retrieves the index of the uniform with the given name.
     * @param name The name of the uniform to find the index of
     * @return The uniform's index
     * @throws NullPointerException If no uniform exists with the given name
     */
    public int getUniformLocation(String name) {
        final int index = GL20.glGetUniformLocation(this.handle(), name);

        if (index < 0) {
            throw new NullPointerException("No uniform exists with name: " + name);
        }

        return index;
    }

    public void delete() {
    	GL20.glDeleteProgram(this.handle());

        this.invalidateHandle();
    }

    public static class Builder {
        private final ResourceLocation name;
        private final int program;

        public Builder(ResourceLocation name) {
            this.name = name;
            this.program = GL20.glCreateProgram();
            GLDebug.nameObject(KHRDebug.GL_PROGRAM, program, name.toString());
        }

        public Builder attachShader(GlShader shader) {
        	GL20.glAttachShader(this.program, shader.handle());

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
        	GL20.glLinkProgram(this.program);

            final String log = GL20.glGetProgramInfoLog(this.program, GL20.GL_INFO_LOG_LENGTH);

            final int result = GL20.glGetProgrami(this.program, GL20.GL_LINK_STATUS);

            if ((AngelicaConfig.enableDebugLogging || result != GL11.GL_TRUE) && !log.isEmpty()) {
                LOGGER.warn("Program link log for " + this.name + ": " + log);
            }

            if (result != GL11.GL_TRUE) {
                throw new RuntimeException("Shader program linking failed, see log for details");
            }

            return factory.create(this.name, this.program);
        }

        public Builder bindAttribute(String name, ShaderBindingPoint binding) {
            GL20.glBindAttribLocation(this.program, binding.getGenericAttributeIndex(), name);

            return this;
        }
    }

    public interface ProgramFactory<P extends GlProgram> {
        P create(ResourceLocation name, int handle);
    }
}
