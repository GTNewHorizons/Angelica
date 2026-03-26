// This file is based on code from Sodium by JellySquid, licensed under the LGPLv3 license.

package net.coderbot.iris.gl.shader;

import com.gtnewhorizons.angelica.glsm.GLDebug;
import com.gtnewhorizons.angelica.glsm.GLStateManager;
import com.gtnewhorizons.angelica.glsm.RenderSystem;
import net.coderbot.iris.gl.GlResource;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.KHRDebug;

import java.util.Locale;

/**
 * A compiled OpenGL shader object.
 */
public class GlShader extends GlResource {
    private static final Logger LOGGER = LogManager.getLogger("GlShader");

    private final String name;

    public GlShader(ShaderType type, String name, String src) {
    	super(createShader(type, name, src));

        this.name = name;
    }

    private static int createShader(ShaderType type, String name, String src) {
		int handle = GLStateManager.glCreateShader(type.id);
        // TODO: Iris
//		ShaderWorkarounds.safeShaderSource(handle, src);
        // TODO: ShaderWorkaround
        GLStateManager.glShaderSource(handle, src + '\0');
		GLStateManager.glCompileShader(handle);

		GLDebug.nameObject(KHRDebug.GL_SHADER, handle, name + "(" + type.name().toLowerCase(Locale.ROOT) + ")");

		String log = RenderSystem.getShaderInfoLog(handle);

		if (!log.isEmpty()) {
			LOGGER.warn("Shader compilation log for " + name + ": " + log);
		}

		int result = GLStateManager.glGetShaderi(handle, GL20.GL_COMPILE_STATUS);

		if (result != GL11.GL_TRUE) {
			throw new RuntimeException("Shader compilation failed, see log for details");
		}

		return handle;
	}

    public String getName() {
        return this.name;
    }

    public int getHandle() {
    	return this.getGlId();
	}

    @Override
	protected void destroyInternal() {
        GLStateManager.glDeleteShader(this.getGlId());
	}
}
