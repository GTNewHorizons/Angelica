package com.gtnewhorizons.angelica.glsm.ffp;

import com.gtnewhorizons.angelica.glsm.GLStateManager;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL15;

import java.nio.FloatBuffer;

public final class LogicOpFixture {

    public static final int[] OPS = {
        GL11.GL_CLEAR, GL11.GL_AND, GL11.GL_AND_REVERSE, GL11.GL_COPY,
        GL11.GL_AND_INVERTED, GL11.GL_NOOP, GL11.GL_XOR, GL11.GL_OR,
        GL11.GL_NOR, GL11.GL_EQUIV, GL11.GL_INVERT, GL11.GL_OR_REVERSE,
        GL11.GL_COPY_INVERTED, GL11.GL_OR_INVERTED, GL11.GL_NAND, GL11.GL_SET
    };

    public static final int DST_R = 0x3C;
    public static final int DST_G = 0xA5;
    public static final int DST_B = 0xF0;
    public static final int DST_A = 0x5A;

    public static final int[][] SOURCES = {
        { 0x00, 0x00, 0xFF, 0xFF },
        { 0x33, 0x99, 0xE6, 0x80 },
        { 0xFF, 0xFF, 0xFF, 0xFF },
    };

    private static int vao;
    private static int vbo;

    private LogicOpFixture() {}

    public static int expected(int op, int s, int d) {
        final int r = switch (op) {
            case GL11.GL_CLEAR -> 0;
            case GL11.GL_AND -> s & d;
            case GL11.GL_AND_REVERSE -> s & ~d;
            case GL11.GL_COPY -> s;
            case GL11.GL_AND_INVERTED -> ~s & d;
            case GL11.GL_NOOP -> d;
            case GL11.GL_XOR -> s ^ d;
            case GL11.GL_OR -> s | d;
            case GL11.GL_NOR -> ~(s | d);
            case GL11.GL_EQUIV -> ~(s ^ d);
            case GL11.GL_INVERT -> ~d;
            case GL11.GL_OR_REVERSE -> s | ~d;
            case GL11.GL_COPY_INVERTED -> ~s;
            case GL11.GL_OR_INVERTED -> ~s | d;
            case GL11.GL_NAND -> ~(s & d);
            case GL11.GL_SET -> 0xFF;
            default -> throw new IllegalArgumentException("op 0x" + Integer.toHexString(op));
        };
        return r & 0xFF;
    }

    public static void clearToDst() {
        GLStateManager.glClearColor(DST_R / 255f, DST_G / 255f, DST_B / 255f, DST_A / 255f);
        GLStateManager.glClear(GL11.GL_COLOR_BUFFER_BIT | GL11.GL_DEPTH_BUFFER_BIT);
    }

    public static void drawQuad(int op, int[] src) {
        GLStateManager.glEnable(GL11.GL_COLOR_LOGIC_OP);
        GLStateManager.glLogicOp(op);
        quad(src);
        GLStateManager.glDisable(GL11.GL_COLOR_LOGIC_OP);
        GLStateManager.glLogicOp(GL11.GL_COPY);
    }

    public static void quad(int[] src) {
        if (vao == 0) {
            vao = GLStateManager.glGenVertexArrays();
            GLStateManager.glBindVertexArray(vao);
            vbo = GLStateManager.glGenBuffers();
            GLStateManager.glBindBuffer(GL15.GL_ARRAY_BUFFER, vbo);
            final FloatBuffer positions = BufferUtils.createFloatBuffer(12);
            positions.put(new float[] { -1f, -1f, 0f, 1f, -1f, 0f, 1f, 1f, 0f, -1f, 1f, 0f }).flip();
            GLStateManager.glBufferData(GL15.GL_ARRAY_BUFFER, positions, GL15.GL_STATIC_DRAW);
            FfpFixture.attrib(0, 3, GL11.GL_FLOAT, false, 12, 0);
            GLStateManager.glBindBuffer(GL15.GL_ARRAY_BUFFER, 0);
        }
        GLStateManager.glBindVertexArray(vao);
        VAOManager.setCurrentVertexFlags(0);
        GLStateManager.glColor4f(src[0] / 255f, src[1] / 255f, src[2] / 255f, src[3] / 255f);
        GLStateManager.glDrawArrays(GL11.GL_QUADS, 0, 4);
        GLStateManager.glBindVertexArray(0);
        GLStateManager.glColor4f(1.0f, 1.0f, 1.0f, 1.0f);
    }

    public static void deleteResources() {
        if (vbo != 0) { GLStateManager.glDeleteBuffers(vbo); vbo = 0; }
        if (vao != 0) { GLStateManager.glDeleteVertexArrays(vao); vao = 0; }
    }

    public static String label(int op, int[] src) {
        return "op=0x" + Integer.toHexString(op) + " src=" + src[0] + "," + src[1] + "," + src[2] + "," + src[3];
    }
}
