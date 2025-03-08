package me.jellysquid.mods.sodium.client.render.chunk.shader;

import me.jellysquid.mods.sodium.client.gl.compat.FogHelper;
import org.lwjgl.opengl.GL20;

/**
 * These shader implementations try to remain compatible with the deprecated fixed function pipeline by manually
 * copying the state into each shader's uniforms. The shader code itself is a straight-forward implementation of the
 * fog functions themselves from the fixed-function pipeline, except that they use the distance from the camera
 * rather than the z-buffer to produce better looking fog that doesn't move with the player's view angle.
 *
 * Minecraft itself will actually try to enable distance-based fog by using the proprietary NV_fog_distance extension,
 * but as the name implies, this only works on graphics cards produced by NVIDIA. The shader implementation however does
 * not depend on any vendor-specific extensions and is written using very simple GLSL code.
 */
public abstract class ChunkShaderFogComponent {
    public abstract void setup();

    public static class None extends ChunkShaderFogComponent {
        public None(ChunkProgram program) {

        }

        @Override
        public void setup() {

        }
    }

    public static class Exp2 extends ChunkShaderFogComponent {
        private final int uFogColor;
        private final int uFogDensity;

        public Exp2(ChunkProgram program) {
            this.uFogColor = program.getUniformLocation("u_FogColor");
            this.uFogDensity = program.getUniformLocation("u_FogDensity");
        }

        @Override
        public void setup() {
            final float[] fc = FogHelper.getFogColor();
            GL20.glUniform4f(this.uFogColor, fc[0], fc[1], fc[2], fc[3]);
            GL20.glUniform1f(this.uFogDensity, FogHelper.getFogDensity());
        }
    }

    public static class Linear extends ChunkShaderFogComponent {
        private final int uFogColor;
        private final int uFogLength;
        private final int uFogEnd;

        public Linear(ChunkProgram program) {
            this.uFogColor = program.getUniformLocation("u_FogColor");
            this.uFogLength = program.getUniformLocation("u_FogLength");
            this.uFogEnd = program.getUniformLocation("u_FogEnd");
        }

        @Override
        public void setup() {
            float end = FogHelper.getFogEnd();
            float start = FogHelper.getFogStart();
            float[] color = FogHelper.getFogColor();

            GL20.glUniform4f(this.uFogColor, color[0], color[1], color[2], color[3]);
            GL20.glUniform1f(this.uFogLength, end - start);
            GL20.glUniform1f(this.uFogEnd, end);
        }
    }

}
