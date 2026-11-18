package com.gtnewhorizons.angelica.sdlgpu.resource;

import com.gtnewhorizons.angelica.glsm.GLSMPrograms;
import com.gtnewhorizons.angelica.glsm.GLSMQueries;
import com.gtnewhorizons.angelica.glsm.GLSMTextures;
import com.gtnewhorizons.angelica.glsm.states.ImageUnitBinding;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.embeddedt.embeddium.impl.render.shader.ShaderLoader;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL43;

public final class Image3DClear {

    private static final Logger LOG = LogManager.getLogger("Angelica-SDLGPU");
    private static final String SOURCE = "angelica:sdlgpu/clear_image3d.csh";
    private static final int LOCAL_SIZE = 4;

    private int program;
    private int extentLocation = -1;
    private boolean failed;

    private boolean ensureProgram() {
        if (program != 0) return true;
        if (failed) return false;
        failed = true;

        final int shader = GLSMPrograms.glCreateShader(GL43.GL_COMPUTE_SHADER);
        if (shader == 0) return false;
        try {
            GLSMPrograms.glShaderSource(shader, ShaderLoader.getShaderSource(SOURCE));
            GLSMPrograms.glCompileShader(shader);

            final int created = GLSMPrograms.glCreateProgram();
            if (created == 0) return false;
            GLSMPrograms.glAttachShader(created, shader);
            GLSMPrograms.glLinkProgram(created);
            if (GLSMPrograms.glGetProgrami(created, GL20.GL_LINK_STATUS) != GL11.GL_TRUE) {
                LOG.error("3D image clear program failed to link: {}", GLSMPrograms.glGetProgramInfoLog(created, 4096));
                GLSMPrograms.glDeleteProgram(created);
                return false;
            }
            program = created;
            extentLocation = GLSMPrograms.glGetUniformLocation(created, "u_extent");
            failed = false;
            return true;
        } catch (RuntimeException e) {
            LOG.error("3D image clear program could not be built; 3D images will not be cleared", e);
            return false;
        } finally {
            GLSMPrograms.glDeleteShader(shader);
        }
    }

    public boolean clear(int glTexture, int width, int height, int depth) {
        if (width <= 0 || height <= 0 || depth <= 0) return true;
        if (!ensureProgram()) return false;

        final int previousProgram = GLSMQueries.glGetInteger(GL20.GL_CURRENT_PROGRAM);
        final ImageUnitBinding previousImage = GLSMTextures.getImageUnitBinding(0);
        final ImageUnitBinding savedImage = previousImage == null ? null : previousImage.copy();
        GLSMPrograms.glUseProgram(program);
        try {
            GLSMTextures.glBindImageTexture(0, glTexture, 0, true, 0, GL15.GL_WRITE_ONLY, 0);
            if (extentLocation >= 0) GLSMPrograms.glUniform3i(extentLocation, width, height, depth);
            GLSMPrograms.glDispatchCompute(groups(width), groups(height), groups(depth));
        } finally {
            if (savedImage == null) {
                GLSMTextures.glBindImageTexture(0, 0, 0, true, 0, GL15.GL_WRITE_ONLY, 0);
            } else {
                GLSMTextures.glBindImageTexture(0, savedImage.getTexture(), savedImage.getLevel(), savedImage.isLayered(), savedImage.getLayer(), savedImage.getAccess(), savedImage.getFormat());
            }
            GLSMPrograms.glUseProgram(previousProgram);
        }
        return true;
    }

    private static int groups(int extent) {
        return (extent + LOCAL_SIZE - 1) / LOCAL_SIZE;
    }

    public void delete() {
        if (program != 0) {
            GLSMPrograms.glDeleteProgram(program);
            program = 0;
        }
        extentLocation = -1;
        failed = false;
    }
}
