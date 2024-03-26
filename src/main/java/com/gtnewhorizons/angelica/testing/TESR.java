package com.gtnewhorizons.angelica.testing;

import com.gtnewhorizons.angelica.client.renderer.CapturingTessellator;
import com.gtnewhorizons.angelica.compat.mojang.DefaultVertexFormat;
import com.gtnewhorizons.angelica.compat.mojang.VertexBuffer;
import com.gtnewhorizons.angelica.glsm.TessellatorManager;
import com.gtnewhorizons.angelica.rendering.RenderingState;
import cpw.mods.fml.common.FMLLog;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.client.renderer.tileentity.TileEntitySpecialRenderer;
import net.minecraft.tileentity.TileEntity;
import org.apache.logging.log4j.Level;
import org.joml.Math;
import org.joml.Matrix4fStack;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.FloatBuffer;

import static com.gtnewhorizons.angelica.loading.AngelicaTweaker.LOGGER;

public class TESR extends TileEntitySpecialRenderer  {
    private static final float LONG_DISTANCE = (float) (1.0f + Math.sqrt(2.0f)) / 5.4f;
    /** Distance from center to end of parallel side */
    private static final float SHORT_DISTANCE = 1.0f / 5.4f;

    private static final float[] edgeX = { LONG_DISTANCE, LONG_DISTANCE, SHORT_DISTANCE, -SHORT_DISTANCE,
        -LONG_DISTANCE, -LONG_DISTANCE, -SHORT_DISTANCE, SHORT_DISTANCE };
    /** Z edges of the helix */
    private static final float[] edgeZ = { SHORT_DISTANCE, -SHORT_DISTANCE, -LONG_DISTANCE, -LONG_DISTANCE,
        -SHORT_DISTANCE, SHORT_DISTANCE, LONG_DISTANCE, LONG_DISTANCE };
    private static final float[] xOffsets = {0.0f, 0.0f, 1.0f, 1.0f};
    private static final float[] zOffsets = {0.0f, 1.0f, 1.0f, 0.0f};
    boolean isInitialized = false;
    VertexBuffer vbo1;

    private static String readFileAsString(String filename) throws Exception {
        StringBuilder source = new StringBuilder();
        InputStream in = TESR.class.getResourceAsStream(filename);
        Exception exception = null;
        BufferedReader reader;

        if(in == null)
            return "";

        try {
            reader = new BufferedReader(new InputStreamReader(in, "UTF-8"));

            Exception innerExc= null;
            try {
                String line;
                while((line = reader.readLine()) != null)
                    source.append(line).append('\n');
            } catch(Exception exc) {
                exception = exc;
            } finally {
                try {
                    reader.close();
                } catch(Exception exc) {
                    if(innerExc == null)
                        innerExc = exc;
                    else exc.printStackTrace();
                }
            }

            if(innerExc != null)
                throw innerExc;
        } catch(Exception exc) {
            exception = exc;
        } finally {
            try {
                in.close();
            } catch(Exception exc) {
                if(exception == null)
                    exception = exc;
                else exc.printStackTrace();
            }

            if(exception != null)
                throw exception;
        }

        return source.toString();
    }

    private static String getLogInfo(int obj) {
        return GL20.glGetShaderInfoLog(obj, GL20.glGetShaderi(obj, GL20.GL_INFO_LOG_LENGTH));
    }

    private static int createProgram(String vert, String frag) {
        int vertId = 0, fragId = 0, program;
        if(vert != null) vertId = createShader(vert, GL20.GL_VERTEX_SHADER);
        if(frag != null) fragId = createShader(frag, GL20.GL_FRAGMENT_SHADER);

        program = GL20.glCreateProgram();
        if(program == 0) return 0;

        if(vert != null) GL20.glAttachShader(program, vertId);
        if(frag != null) GL20.glAttachShader(program, fragId);

        GL20.glLinkProgram(program);
        if(GL20.glGetProgrami(program, GL20.GL_LINK_STATUS) == GL11.GL_FALSE) {
            FMLLog.log(Level.ERROR, getLogInfo(program));
            return 0;
        }

        GL20.glValidateProgram(program);
        if (GL20.glGetProgrami(program, GL20.GL_LINK_STATUS) == GL11.GL_FALSE) {
            FMLLog.log(Level.ERROR, getLogInfo(program));
            return 0;
        }

        return program;
    }

    private static int createShader(String filename, int shaderType){
        int shader = 0;
        try {
            shader = GL20.glCreateShader(shaderType);

            if(shader == 0)
                return 0;

            GL20.glShaderSource(shader, readFileAsString(filename));
            GL20.glCompileShader(shader);

            if (GL20.glGetShaderi(shader, GL20.GL_COMPILE_STATUS) == GL11.GL_FALSE)
                throw new RuntimeException("Error creating shader: " + getLogInfo(shader));

            return shader;
        }
        catch(Exception e) {
            GL20.glDeleteProgram(shader);
            e.printStackTrace();
            return -1;
        }
    }

    private static int vao;
    private static int cableProgram;
    private static int uModelProjectionMatrix;
    private static int uBlockTex;
    private static int uSectionHeight;
    private static int uTime;
    private static int uBaseY;
    private static int uGlowU;
    private static int uGlowV;

    private static final FloatBuffer bufModelViewProjection = BufferUtils.createFloatBuffer(16);
    private static final Matrix4fStack modelProjection = new Matrix4fStack(2);

    private static final double CABLE_HEIGHT = 512.0;
    private static final double side = 2.0 / 5.4;
    private static final double sectionHeight = 8 * side;
    private static final int sections = (int) Math.ceil(CABLE_HEIGHT / sectionHeight);

    @Override
    public void renderTileEntityAt(TileEntity tile, double x, double y, double z, float timeSinceLastTick) {
        GL13.glActiveTexture(GL13.GL_TEXTURE0);
        this.bindTexture(TextureMap.locationBlocksTexture);


        if(!isInitialized) {
            // Draw the cable
            final double minU = BlockTESR.cableIcon.getMinU();
            final double maxU = BlockTESR.cableIcon.getMaxU();
            final double minV = BlockTESR.cableIcon.getMinV();
            final double maxV = BlockTESR.cableIcon.getMaxV();

            final float glowMinU = (float) Math.lerp(minU, maxU, 7f / 16f);
            final float glowMaxU = (float) Math.lerp(minU, maxU, 9f / 16f);
            final float glowMinV = (float) Math.lerp(minV, maxV, 7f / 16f);
            final float glowMaxV = (float) Math.lerp(minV, maxV, 9f / 16f);

            TessellatorManager.startCapturing();
            final CapturingTessellator tes = (CapturingTessellator) TessellatorManager.get();

            tes.setColorOpaque_F(1F, 1F, 1F);
            tes.startDrawingQuads();

            clockwiseHelixPart(tes, 0, 0, 0, 0, side, 0.75, minU, maxU, minV, maxV);

            vao = GL30.glGenVertexArrays();
            GL30.glBindVertexArray(vao);

            vbo1 = TessellatorManager.stopCapturingToVAO(DefaultVertexFormat.POSITION_TEXTURE);
            GL30.glBindVertexArray(0);

            cableProgram = createProgram("/assets/angelica/shaders/tesr.v.glsl", "/assets/angelica/shaders/tesr.f.glsl");
            GL20.glUseProgram(cableProgram);

            GL20.glBindAttribLocation(cableProgram, 0, "a_Pos");
            GL20.glBindAttribLocation(cableProgram, 1, "a_TexCoord");

            uModelProjectionMatrix = GL20.glGetUniformLocation(cableProgram, "u_ModelProjection");
            uBlockTex = GL20.glGetUniformLocation(cableProgram, "u_BlockTex");
            uSectionHeight = GL20.glGetUniformLocation(cableProgram, "u_SectionHeight");
            uTime = GL20.glGetUniformLocation(cableProgram, "u_Time");
            uBaseY = GL20.glGetUniformLocation(cableProgram, "u_BaseY");
            uGlowU = GL20.glGetUniformLocation(cableProgram, "u_GlowU");
            uGlowV = GL20.glGetUniformLocation(cableProgram, "u_GlowV");

            GL20.glUniform1f(uSectionHeight, (float) sectionHeight);
            GL20.glUniform1i(uBlockTex, OpenGlHelper.defaultTexUnit - GL13.GL_TEXTURE0);
            GL20.glUniform2f(uGlowU, glowMinU, glowMaxU);
            GL20.glUniform2f(uGlowV, glowMinV, glowMaxV);

            GL20.glUseProgram(0);

            isInitialized = true;
            LOGGER.info("Initialized TESR. Sections " + sections + " Shader " + cableProgram);

        }

        GL20.glUseProgram(cableProgram);
        GL20.glUniform1f(uTime, ((tile.getWorldObj().getWorldTime() % 60) + timeSinceLastTick) / 60f);
        GL20.glUniform1i(uBaseY, (int) y - 23);

        modelProjection.set(RenderingState.INSTANCE.getProjectionMatrix());
        modelProjection.mul(RenderingState.INSTANCE.getModelViewMatrix());

        GL30.glBindVertexArray(vao);
        for(int i = 0 ; i < 4 ; i ++) {

            modelProjection.pushMatrix();
            modelProjection.translate((float) x + xOffsets[i], (float) y - 23, (float) z + zOffsets[i] );
            modelProjection.rotate((float) Math.toRadians(90 * i), 0, 1, 0);
            modelProjection.get(0, bufModelViewProjection);

            GL20.glUniformMatrix4(uModelProjectionMatrix, false, bufModelViewProjection);

            vbo1.renderInstanced(sections);
            modelProjection.popMatrix();
        }
        GL30.glBindVertexArray(0);

        GL20.glUseProgram(0);

    }

    private void clockwiseHelixPart(Tessellator tes, double x, double y, double z, int offset, double side,
        double width, double minU, double maxU, double minV, double maxV) {

        // spotless:off
        for (int i = 0; i < 8; i++) {
            final int j = (i + offset) % 8;
            final int k = (i + 1 + offset) % 8;
            tes.addVertexWithUV(x + 0.5f + edgeX[k], y + side * i + side, z + 0.5f + edgeZ[k], minU, maxV);
            tes.addVertexWithUV(x + 0.5f + edgeX[k], y + side * i + (side + width), z + 0.5f + edgeZ[k], minU, minV);
            tes.addVertexWithUV(x + 0.5f + edgeX[j], y + side * i + width, z + 0.5f + edgeZ[j], maxU, minV);
            tes.addVertexWithUV(x + 0.5f + edgeX[j], y + side * i, z + 0.5f + edgeZ[j], maxU, maxV);

            // Inner side
            tes.addVertexWithUV(x + 0.5f + edgeX[j], y + side * i, z + 0.5f + edgeZ[j], maxU, maxV);
            tes.addVertexWithUV(x + 0.5f + edgeX[j], y + side * i + width, z + 0.5f + edgeZ[j], maxU, minV);
            tes.addVertexWithUV(x + 0.5f + edgeX[k], y + side * i + (side + width), z + 0.5f + edgeZ[k], minU, minV);
            tes.addVertexWithUV(x + 0.5f + edgeX[k], y + side * i + side, z + 0.5f + edgeZ[k], minU, maxV);
        }
        // spotless:on
    }


}
