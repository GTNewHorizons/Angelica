package me.jellysquid.mods.sodium.client.gl.shader;

import me.jellysquid.mods.sodium.client.gl.device.RenderDevice;
import net.minecraft.util.ResourceLocation;
import org.apache.commons.io.IOUtils;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

public class ShaderLoader {
    /**
     * Creates an OpenGL shader from GLSL sources. The GLSL source file should be made available on the classpath at the
     * path of `/assets/{namespace}/shaders/{path}`. User defines can be used to declare variables in the shader source
     * after the version header, allowing for conditional compilation with macro code.
     *
     *
     * @param device
     * @param type The type of shader to create
     * @param name The ResourceLocation used to locate the shader source file
     * @param constants A list of constants for shader specialization
     * @return An OpenGL shader object compiled with the given user defines
     */
    public static GlShader loadShader(RenderDevice device, ShaderType type, ResourceLocation name, ShaderConstants constants) {
        return new GlShader(device, type, name, getShaderSource(getShaderPath(name, type)), constants);
    }

    /**
     * Use {@link ShaderLoader#loadShader(RenderDevice, ShaderType, ResourceLocation, ShaderConstants)} instead. This will be removed.
     */
    @Deprecated
    public static GlShader loadShader(RenderDevice device, ShaderType type, ResourceLocation name, List<String> constants) {
        return new GlShader(device, type, name, getShaderSource(getShaderPath(name, type)), ShaderConstants.fromStringList(constants));
    }

    private static String getShaderPath(ResourceLocation name, ShaderType type) {
        return String.format("/assets/%s/shaders/%s.%s.glsl", name.getResourceDomain(), name.getResourcePath(), type == ShaderType.VERTEX ? "v" : "f");
    }

    private static String getShaderSource(String path) {
        try (InputStream in = ShaderLoader.class.getResourceAsStream(path)) {
            if (in == null) {
                throw new RuntimeException("Shader not found: " + path);
            }

            return IOUtils.toString(in, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new RuntimeException("Could not read shader sources", e);
        }
    }
}
