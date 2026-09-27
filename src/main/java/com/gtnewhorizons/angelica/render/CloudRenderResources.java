package com.gtnewhorizons.angelica.render;

import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Map;

import com.gtnewhorizons.angelica.glsm.GLStateManager;
import net.minecraft.client.Minecraft;
import net.minecraft.util.ResourceLocation;
import org.embeddedt.embeddium.impl.gl.shader.GlProgram;
import org.embeddedt.embeddium.impl.gl.shader.GlShader;
import org.embeddedt.embeddium.impl.gl.shader.ShaderConstants;
import org.embeddedt.embeddium.impl.gl.shader.ShaderType;
import org.embeddedt.embeddium.impl.render.shader.ShaderLoader;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL13;

final class CloudRenderResources {

    private final Map<ResourceLocation, Texture> textures = new HashMap<>();
    private final Map<Integer, GlProgram<CloudUniforms>> programs = new HashMap<>(4);

    Texture texture(ResourceLocation location) {
        return textures.computeIfAbsent(location, Texture::new);
    }

    GlProgram<CloudUniforms> program(boolean faces, boolean untextured) {
        int key = (faces ? 2 : 0) | (untextured ? 1 : 0);
        return programs.computeIfAbsent(key, ignored -> buildProgram(faces, untextured));
    }

    void clear() {
        for (GlProgram<CloudUniforms> program : programs.values()) program.delete();
        programs.clear();
        textures.clear();
    }

    private static GlProgram<CloudUniforms> buildProgram(boolean faces, boolean untextured) {
        final ShaderConstants.Builder builder = ShaderConstants.builder();
        if (untextured) builder.add("UNTEXTURED");
        final ShaderConstants constants = builder.build();
        final String vertName = faces ? "angelica:cloud_faces.vert" : "angelica:cloud.vert";
        final String fragName = faces ? "angelica:cloud_faces.frag" : "angelica:cloud.frag";
        final GlShader vert = ShaderLoader.loadShader(ShaderType.VERTEX, vertName, constants);
        final GlShader frag = ShaderLoader.loadShader(ShaderType.FRAGMENT, fragName, constants);
        final GlProgram<CloudUniforms> built;
        try {
            built = GlProgram.builder("angelica:cloud")
                .attachShader(vert)
                .attachShader(frag)
                .link(CloudUniforms::new);
        } finally {
            vert.delete();
            frag.delete();
        }

        built.bind();
        if (!untextured) built.getInterface().textureUnit.setInt(0);
        built.unbind();
        return built;
    }

    static final class Texture {

        final ResourceLocation location;
        CloudShape shape;
        int generation;
        int width;

        private final Minecraft mc = Minecraft.getMinecraft();
        private int mipmappedTexId = -1;
        private int cloudTexId = -1;
        private ByteBuffer texelStagingBuffer;

        private Texture(ResourceLocation location) {
            this.location = location;
        }

        void bind() {
            if (mc.renderEngine == null) return;
            GLStateManager.glActiveTexture(GL13.GL_TEXTURE0);
            mc.renderEngine.bindTexture(location);
            final int boundTexId = GLStateManager.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
            if (boundTexId != mipmappedTexId) {
                GLStateManager.glTexParameteri(GL11.GL_TEXTURE_2D, GL12.GL_TEXTURE_MAX_LEVEL, 4);
                GLStateManager.glGenerateMipmap(GL11.GL_TEXTURE_2D);
                GLStateManager.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_NEAREST_MIPMAP_LINEAR);
                GLStateManager.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_NEAREST);
                GLStateManager.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL11.GL_REPEAT);
                GLStateManager.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL11.GL_REPEAT);
                mipmappedTexId = boundTexId;
            }
            if (boundTexId != cloudTexId || shape == null) {
                extractCloudShape();
                cloudTexId = boundTexId;
            }
        }

        private void extractCloudShape() {
            final int texWidth = GLStateManager.glGetTexLevelParameteri(GL11.GL_TEXTURE_2D, 0, GL11.GL_TEXTURE_WIDTH);
            final int texHeight = GLStateManager.glGetTexLevelParameteri(GL11.GL_TEXTURE_2D, 0, GL11.GL_TEXTURE_HEIGHT);
            if (texWidth <= 0 || texHeight <= 0) return;
            final int neededBytes = texWidth * texHeight * 4;
            if (texelStagingBuffer == null || texelStagingBuffer.capacity() < neededBytes) {
                texelStagingBuffer = BufferUtils.createByteBuffer(neededBytes);
            } else {
                texelStagingBuffer.clear();
            }
            GLStateManager.glGetTexImage(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE,
                texelStagingBuffer);
            width = texWidth;
            shape = new CloudShape(texWidth, texHeight, texelStagingBuffer);
            generation++;
        }
    }
}
