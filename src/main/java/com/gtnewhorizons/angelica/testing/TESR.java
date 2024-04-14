package com.gtnewhorizons.angelica.testing;

import com.gtnewhorizons.angelica.rendering.RenderingState;
import cpw.mods.fml.common.FMLLog;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.client.renderer.tileentity.TileEntitySpecialRenderer;
import net.minecraft.tileentity.TileEntity;
import org.apache.logging.log4j.Level;
import org.joml.Math;
import org.joml.Matrix4fStack;
import org.lwjgl.BufferUtils;
import org.lwjgl.MemoryUtil;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL20;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;

import static com.gtnewhorizons.angelica.loading.AngelicaTweaker.LOGGER;

public class TESR extends TileEntitySpecialRenderer  {
    boolean isInitialized = false;

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

    private static int cableProgram;
    private static int uModelProjectionMatrix;
    private static int uBlockTex;
    private static int uSectionHeight;
    private static int uTime;
    private static int uBaseY;
    private static int uGlowU;
    private static int uGlowV;
    private static int uUV;
    private static int aVertexID = -1;
    private static int vertexIDBuffer = -1;

    private static final FloatBuffer bufModelViewProjection = BufferUtils.createFloatBuffer(16);
    private static final Matrix4fStack modelProjection = new Matrix4fStack(2);

    private static final float CABLE_HEIGHT = 512.0f;
    private static final float SIDE = 2.0f / 5.4f;
    private static final float SECTION_HEIGHT = 8 * SIDE;
    private static final int SECTIONS = (int) Math.ceil(CABLE_HEIGHT / SECTION_HEIGHT);
    private static final int VERTEX_COUNT = 48 * 4 * SECTIONS;

    @Override
    public void renderTileEntityAt(TileEntity tile, double x, double y, double z, float timeSinceLastTick) {
        GL13.glActiveTexture(GL13.GL_TEXTURE0);
        this.bindTexture(TextureMap.locationBlocksTexture);

        if(!isInitialized) {
            // Draw the cable
            final float minU = BlockTESR.cableIcon.getMinU();
            final float maxU = BlockTESR.cableIcon.getMaxU();
            final float minV = BlockTESR.cableIcon.getMinV();
            final float maxV = BlockTESR.cableIcon.getMaxV();

            final float glowMinU = Math.lerp(minU, maxU, 7f / 16f);
            final float glowMaxU = Math.lerp(minU, maxU, 9f / 16f);
            final float glowMinV = Math.lerp(minV, maxV, 7f / 16f);
            final float glowMaxV = Math.lerp(minV, maxV, 9f / 16f);

            cableProgram = createProgram("/assets/angelica/shaders/tesr.vert.glsl", "/assets/angelica/shaders/tesr.frag.glsl");
            GL20.glUseProgram(cableProgram);

            aVertexID = GL20.glGetAttribLocation(cableProgram, "vertexId");

            uModelProjectionMatrix = GL20.glGetUniformLocation(cableProgram, "u_ModelProjection");
            uBlockTex = GL20.glGetUniformLocation(cableProgram, "u_BlockTex");
            uSectionHeight = GL20.glGetUniformLocation(cableProgram, "u_SectionHeight");
            uTime = GL20.glGetUniformLocation(cableProgram, "u_Time");
            uBaseY = GL20.glGetUniformLocation(cableProgram, "u_BaseY");
            uGlowU = GL20.glGetUniformLocation(cableProgram, "u_GlowU");
            uGlowV = GL20.glGetUniformLocation(cableProgram, "u_GlowV");
            uUV = GL20.glGetUniformLocation(cableProgram, "u_UV");

            vertexIDBuffer = GL15.glGenBuffers();
            GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, vertexIDBuffer);
            final ByteBuffer vertexIDData = BufferUtils.createByteBuffer(VERTEX_COUNT * 4);
            for (int i = 0; i < VERTEX_COUNT; i++) {
                vertexIDData.putInt(i * 4, i);
            }
            GL15.glBufferData(GL15.GL_ARRAY_BUFFER, vertexIDData, GL15.GL_STATIC_DRAW);
            GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, 0);

            final FloatBuffer uvBuffer = BufferUtils.createFloatBuffer(4);
            uvBuffer.put(0, minU);
            uvBuffer.put(1, minV);
            uvBuffer.put(2, maxU);
            uvBuffer.put(3, maxV);

            GL20.glUniform1f(uSectionHeight, SECTION_HEIGHT);
            GL20.glUniform1i(uBlockTex, OpenGlHelper.defaultTexUnit - GL13.GL_TEXTURE0);
            GL20.glUniform2f(uGlowU, glowMinU, glowMaxU);
            GL20.glUniform2f(uGlowV, glowMinV, glowMaxV);
            GL20.glUniform2(uUV, uvBuffer);

            GL20.glUseProgram(0);

            isInitialized = true;
            LOGGER.info("Initialized TESR. Sections " + SECTIONS + " Shader " + cableProgram);

        }

        GL20.glUseProgram(cableProgram);
        GL20.glUniform1f(uTime, ((tile.getWorldObj().getWorldInfo().getWorldTotalTime() % 60) + timeSinceLastTick) / 60f);
        GL20.glUniform1i(uBaseY, (int) y - 23);

        modelProjection.set(RenderingState.INSTANCE.getProjectionMatrix());
        modelProjection.mul(RenderingState.INSTANCE.getModelViewMatrix());
        modelProjection.translate((float) x, (float) (y - 23), (float) z);

        GL11.glDisable(GL11.GL_CULL_FACE);
        modelProjection.get(0, bufModelViewProjection);
        GL20.glUniformMatrix4(uModelProjectionMatrix, false, bufModelViewProjection);
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, vertexIDBuffer);
        GL20.glEnableVertexAttribArray(aVertexID);
        GL20.glVertexAttribPointer(aVertexID, 1, GL11.GL_INT, false, 0, 0);

        GL11.glDrawArrays(GL11.GL_TRIANGLES, 0, VERTEX_COUNT);

        GL20.glDisableVertexAttribArray(aVertexID);
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, 0);
        GL11.glEnable(GL11.GL_CULL_FACE);
        GL20.glUseProgram(0);
    }

}
