package com.gtnewhorizons.angelica.compat.thaumcraft;

import com.gtnewhorizons.angelica.client.rendering.GlUniformFloat2v;
import com.gtnewhorizons.angelica.client.rendering.GlUniformFloat3Array;
import com.gtnewhorizons.angelica.client.rendering.GlUniformFloat4Array;
import com.gtnewhorizons.angelica.glsm.GLSMAttribStack;
import com.gtnewhorizons.angelica.glsm.GLSMBlend;
import com.gtnewhorizons.angelica.glsm.GLSMBuffers;
import com.gtnewhorizons.angelica.glsm.GLSMDraws;
import com.gtnewhorizons.angelica.glsm.GLSMFog;
import com.gtnewhorizons.angelica.glsm.GLSMPrograms;
import com.gtnewhorizons.angelica.glsm.GLSMQueries;
import com.gtnewhorizons.angelica.glsm.GLSMTextures;
import com.gtnewhorizons.angelica.glsm.GLSMVertexInput;
import com.gtnewhorizons.angelica.glsm.GLStateManager;
import com.gtnewhorizons.angelica.glsm.StateSet;
import com.gtnewhorizons.angelica.glsm.ffp.FragmentKey;
import com.gtnewhorizons.angelica.glsm.states.FogState;
import com.gtnewhorizons.angelica.glsm.states.TextureUnitArray;
import net.coderbot.iris.gl.framebuffer.GlFramebuffer;
import org.embeddedt.embeddium.impl.gl.shader.GlProgram;
import org.embeddedt.embeddium.impl.gl.shader.GlShader;
import org.embeddedt.embeddium.impl.gl.shader.ShaderBindingContext;
import org.embeddedt.embeddium.impl.gl.shader.ShaderConstants;
import org.embeddedt.embeddium.impl.gl.shader.ShaderType;
import org.embeddedt.embeddium.impl.gl.shader.uniform.GlUniformFloat;
import org.embeddedt.embeddium.impl.gl.shader.uniform.GlUniformFloat3v;
import org.embeddedt.embeddium.impl.gl.shader.uniform.GlUniformFloat4v;
import org.embeddedt.embeddium.impl.gl.shader.uniform.GlUniformInt;
import org.embeddedt.embeddium.impl.gl.shader.uniform.GlUniformMatrix4f;
import org.embeddedt.embeddium.impl.render.shader.ShaderLoader;
import org.joml.Matrix4f;
import org.joml.Vector4f;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL20;

import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.util.Arrays;
import java.util.Random;

import static com.gtnewhorizons.angelica.compat.thaumcraft.PortalRenderer.AXIS_X;
import static com.gtnewhorizons.angelica.compat.thaumcraft.PortalRenderer.AXIS_Y;

/**
 * GL side of the Thaumcraft portal renderer.
 */
final class PortalDrawer {
    static final int LAYERS = 16;
    static final int FLOATS_PER_FACE = 16;
    static final int ATLAS_SIZE = 2048;
    static final int MIN_SLOT = 16;
    static final int MAX_SLOT = 512;
    private static final int FLOATS_PER_PLANE = LAYERS * 3;
    private static final int TUNNEL_UNIT = 0;
    private static final int LIGHTMAP_UNIT = 1;
    private static final int FIELD_UNIT = 2;
    private static final int FACE_BYTES = FLOATS_PER_FACE * Float.BYTES;
    private static final float[] LAYER_DEPTH = new float[LAYERS];
    private static final float[] LAYER_SCALE = new float[LAYERS];
    private static final float[] LAYER_ANGLE = new float[LAYERS];
    private static final float[] FOG_PARAMS = new float[4];
    private static int maxPlanes;

    static {
        for (int i = 0; i < LAYERS; i++) {
            LAYER_DEPTH[i] = i == 0 ? 65.0F : 16 - i;
            LAYER_SCALE[i] = i == 0 ? 0.125F : i == 1 ? 0.5F : 0.0625F;
            // GLSMMatrices.glRotatef's float radians
            LAYER_ANGLE[i] = (float) Math.toRadians((float) (i * i * 4321 + i * 9) * 2.0F);
        }
    }

    private GlProgram<Uniforms> direct;
    private GlProgram<Uniforms> directFogged;
    private GlProgram<Uniforms> capture;
    private FloatBuffer upload;
    private FloatBuffer planeUpload;
    private int vao, vbo, ebo;
    private GlFramebuffer atlasFramebuffer;
    private int atlasTexture;

    static int maxPlanes() {
        if (maxPlanes == 0) {
            final int vectors = GLSMQueries.glGetInteger(GL20.GL_MAX_VERTEX_UNIFORM_COMPONENTS) / 4;
            maxPlanes = Math.clamp((vectors - 128) / 12, 1, 64);
        }
        return maxPlanes;
    }

    static void putFace(float[] faces, int index, int axis, int plane, double x0, double y0, double z0, double x1, double y1, double z1, double x3, double y3, double z3) {
        int i = index * FLOATS_PER_FACE;
        faces[i++] = (float) x0;
        faces[i++] = (float) y0;
        faces[i++] = (float) z0;
        faces[i++] = axis + 4 * plane;
        faces[i++] = (float) x1;
        faces[i++] = (float) y1;
        faces[i++] = (float) z1;
        faces[i++] = 0.0F;
        faces[i++] = (float) (x1 + x3 - x0);
        faces[i++] = (float) (y1 + y3 - y0);
        faces[i++] = (float) (z1 + z3 - z0);
        faces[i++] = 0.0F;
        faces[i++] = (float) x3;
        faces[i++] = (float) y3;
        faces[i++] = (float) z3;
        faces[i] = 0.0F;
    }

    private static void uploadFog(Uniforms uniforms, int fogMode) {
        final FogState fog = GLSMFog.getFogState();
        FOG_PARAMS[0] = fog.linearScale();
        FOG_PARAMS[1] = fog.linearBias();
        FOG_PARAMS[2] = fog.expScale();
        FOG_PARAMS[3] = fog.exp2Scale();
        uniforms.fogParams.set(FOG_PARAMS);
        uniforms.fogColor.set((float) fog.getFogColor().x, (float) fog.getFogColor().y, (float) fog.getFogColor().z);
        uniforms.fogMode.setInt(fogMode);
        uniforms.fogDistanceMode.setInt(fog.getFogDistanceMode());
    }

    private static void instanced(int attribute, int size, int floatOffset) {
        GLSMVertexInput.glEnableVertexAttribArray(attribute);
        GLSMVertexInput.glVertexAttribPointer(attribute, size, GL11.GL_FLOAT, false, FACE_BYTES, (long) floatOffset * Float.BYTES);
        GLSMVertexInput.glVertexAttribDivisor(attribute, 1);
    }

    private static GlProgram<Uniforms> buildProgram(boolean capturing, boolean fogged) {
        final ShaderConstants.Builder defines = ShaderConstants.builder().add("MAX_PLANES", Integer.toString(maxPlanes()));
        if (capturing) defines.add("CAPTURE");
        if (fogged) defines.add("FOG");
        final ShaderConstants constants = defines.build();
        final GlShader vert = ShaderLoader.loadShader(ShaderType.VERTEX, "angelica:thaumcraft_portal.vert", constants);
        final GlShader frag = ShaderLoader.loadShader(ShaderType.FRAGMENT, "angelica:thaumcraft_portal.frag", constants);
        final GlProgram<Uniforms> program;
        try {
            program = GlProgram.builder("angelica:thaumcraft_portal").attachShader(vert).attachShader(frag).link(Uniforms::new);
        } finally {
            vert.delete();
            frag.delete();
        }

        final int previous = GLSMPrograms.getActiveProgram();
        final int handle = program.handle();
        GLSMPrograms.glUseProgram(handle);
        GLSMPrograms.glUniform1i(GLSMPrograms.glGetUniformLocation(handle, "u_Tunnel"), TUNNEL_UNIT);
        GLSMPrograms.glUniform1i(GLSMPrograms.glGetUniformLocation(handle, "u_Lightmap"), LIGHTMAP_UNIT);
        GLSMPrograms.glUniform1i(GLSMPrograms.glGetUniformLocation(handle, "u_Field"), FIELD_UNIT);
        GLSMPrograms.glUniform4f(GLSMPrograms.glGetUniformLocation(handle, "u_ObjectPlaneR"), 0.0F, 0.0F, 0.0F, 1.0F);
        GLSMPrograms.glUniform3(GLSMPrograms.glGetUniformLocation(handle, "u_LayerColor"), floatBuffer(layerColors()));
        GLSMPrograms.glUseProgram(previous);
        return program;
    }

    private static FloatBuffer floatBuffer(float[] values) {
        final FloatBuffer buffer = BufferUtils.createFloatBuffer(values.length);
        buffer.put(values).flip();
        return buffer;
    }

    static float[] layerColors() {
        final float[] colors = new float[LAYERS * 3];
        final Random random = new Random(31100L);
        for (int i = 0; i < LAYERS; i++) {
            final float f7 = i == 0 ? 0.1F : 1.0F / ((float) (16 - i) + 1.0F);
            float red = random.nextFloat() * 0.5F + 0.1F;
            float green = random.nextFloat() * 0.5F + 0.4F;
            float blue = random.nextFloat() * 0.5F + 0.5F;
            if (i == 0) red = green = blue = 1.0F;
            colors[i * 3] = (int) (red * f7 * 255.0F) / 255.0F;
            colors[i * 3 + 1] = (int) (green * f7 * 255.0F) / 255.0F;
            colors[i * 3 + 2] = (int) (blue * f7 * 255.0F) / 255.0F;
        }
        return colors;
    }

    void drawDirect(float[] faces, int count, int tunnelTexture, int fieldTexture, Frame frame, boolean fogged) {
        final int fogMode = fogged ? FragmentKey.activeFogMode(GLStateManager.ctx()) : FragmentKey.FOG_NONE;
        final GlProgram<Uniforms> program;
        if (fogMode == FragmentKey.FOG_NONE) {
            if (direct == null) direct = buildProgram(false, false);
            program = direct;
        } else {
            if (directFogged == null) directFogged = buildProgram(false, true);
            program = directFogged;
        }
        draw(program, faces, 0, count, tunnelTexture, fieldTexture, frame, fogMode);
    }

    void drawCapture(float[] faces, int first, int count, int tunnelTexture, int fieldTexture, Frame frame) {
        if (capture == null) capture = buildProgram(true, false);
        draw(capture, faces, first, count, tunnelTexture, fieldTexture, frame, 0);
    }

    GlFramebuffer atlasFramebuffer() {
        if (atlasFramebuffer == null) {
            atlasTexture = GLSMTextures.glGenTextures();
            GLSMTextures.glBindTexture(GL11.GL_TEXTURE_2D, atlasTexture);
            GLSMTextures.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA8, ATLAS_SIZE, ATLAS_SIZE, 0, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, (ByteBuffer) null);
            GLSMTextures.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
            GLSMTextures.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
            GLSMTextures.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE);
            GLSMTextures.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE);
            atlasFramebuffer = new GlFramebuffer();
            atlasFramebuffer.addColorAttachment(0, atlasTexture);
            atlasFramebuffer.drawBuffers(new int[]{0});
            atlasFramebuffer.readBuffer(0);
            if (!atlasFramebuffer.isComplete()) {
                atlasFramebuffer.destroy();
                GLSMTextures.glDeleteTextures(atlasTexture);
                atlasFramebuffer = null;
                atlasTexture = 0;
                throw new IllegalStateException("Incomplete Thaumcraft portal atlas framebuffer");
            }
        }
        return atlasFramebuffer;
    }

    int atlasTexture() {
        return atlasTexture;
    }

    void destroy() {
        if (direct != null) direct.delete();
        if (directFogged != null) directFogged.delete();
        if (capture != null) capture.delete();
        direct = directFogged = capture = null;
        if (vao != 0) GLSMVertexInput.glDeleteVertexArrays(vao);
        if (vbo != 0) GLSMBuffers.glDeleteBuffers(vbo);
        if (ebo != 0) GLSMBuffers.glDeleteBuffers(ebo);
        vao = vbo = ebo = 0;
        if (atlasFramebuffer != null) atlasFramebuffer.destroy();
        if (atlasTexture != 0) GLSMTextures.glDeleteTextures(atlasTexture);
        atlasFramebuffer = null;
        atlasTexture = 0;
    }

    private void draw(GlProgram<Uniforms> program, float[] faces, int first, int count, int tunnelTexture, int fieldTexture, Frame frame, int fogMode) {
        final int previousProgram = GLSMPrograms.getActiveProgram();
        final int previousVao = GLSMVertexInput.getBoundVAO();
        final int depth = GLSMAttribStack.pushState(StateSet.forMask(GL11.GL_ENABLE_BIT | GL11.GL_COLOR_BUFFER_BIT | GL11.GL_TEXTURE_BIT));
        try {
            GLSMBlend.disableBlend();
            GLSMPrograms.glUseProgram(program.handle());
            final Uniforms uniforms = program.getInterface();
            if (uniforms.modelViewProjection != null) uniforms.modelViewProjection.set(frame.modelViewProjection);
            if (uniforms.uploadedFrame != frame || uniforms.uploadedVersion != frame.version) {
                uniforms.modelView.set(frame.modelView);
                uniforms.eyePlane.set(frame.eyePlane);
                uniforms.rowS.set(frame.rowS);
                uniforms.rowT.set(frame.rowT);
                uniforms.uploadedFrame = frame;
                uniforms.uploadedVersion = frame.version;
                uniforms.uploadedPlanes = 0;
            }
            if (uniforms.uploadedPlanes != frame.planeCount) {
                final int floats = frame.planeCount * FLOATS_PER_PLANE;
                if (planeUpload == null || planeUpload.capacity() < floats) planeUpload = BufferUtils.createFloatBuffer(maxPlanes() * FLOATS_PER_PLANE);
                planeUpload.clear();
                planeUpload.put(frame.planeData, 0, floats).flip();
                uniforms.planes.set(planeUpload);
                uniforms.uploadedPlanes = frame.planeCount;
            }
            uniforms.lightmapUv.set(frame.lightmapU, frame.lightmapV);
            uniforms.lightmapEnabled.setFloat(frame.lightmapEnabled ? 1.0F : 0.0F);
            if (uniforms.atlasSize != null) uniforms.atlasSize.setFloat(ATLAS_SIZE);
            if (fogMode != FragmentKey.FOG_NONE) uploadFog(uniforms, fogMode);

            GLSMTextures.glActiveTexture(GL13.GL_TEXTURE0 + FIELD_UNIT);
            GLSMTextures.glBindTexture(GL11.GL_TEXTURE_2D, fieldTexture);
            GLSMTextures.glActiveTexture(GL13.GL_TEXTURE0 + TUNNEL_UNIT);
            GLSMTextures.glBindTexture(GL11.GL_TEXTURE_2D, tunnelTexture);

            uploadFaces(faces, first, count);
            GLSMDraws.glDrawElementsInstanced(GL11.GL_TRIANGLES, 6, GL11.GL_UNSIGNED_INT, 0L, count);
        } finally {
            GLSMVertexInput.glBindVertexArray(previousVao);
            GLSMBuffers.glBindBuffer(GL15.GL_ARRAY_BUFFER, 0);
            GLSMPrograms.glUseProgram(previousProgram);
            GLSMAttribStack.popStateTo(depth);
        }
    }

    private void uploadFaces(float[] faces, int first, int count) {
        if (upload == null || upload.capacity() < count * FLOATS_PER_FACE) {
            upload = BufferUtils.createFloatBuffer(Math.max(count, 64) * FLOATS_PER_FACE);
        }
        upload.clear();
        upload.put(faces, first * FLOATS_PER_FACE, count * FLOATS_PER_FACE).flip();

        if (vao == 0) createBuffers();
        GLSMVertexInput.glBindVertexArray(vao);
        GLSMBuffers.glBindBuffer(GL15.GL_ARRAY_BUFFER, vbo);
        GLSMBuffers.glBufferData(GL15.GL_ARRAY_BUFFER, upload, GL15.GL_STREAM_DRAW);
    }

    private void createBuffers() {
        vao = GLSMVertexInput.glGenVertexArrays();
        vbo = GLSMBuffers.glGenBuffers();
        ebo = GLSMBuffers.glGenBuffers();
        GLSMVertexInput.glBindVertexArray(vao);
        GLSMBuffers.glBindBuffer(GL15.GL_ARRAY_BUFFER, vbo);
        for (int corner = 0; corner < 4; corner++) {
            instanced(corner, 4, corner * 4);
        }
        final IntBuffer indices = BufferUtils.createIntBuffer(6);
        indices.put(0).put(1).put(2).put(0).put(2).put(3).flip();
        GLSMBuffers.glBindBuffer(GL15.GL_ELEMENT_ARRAY_BUFFER, ebo);
        GLSMBuffers.glBufferData(GL15.GL_ELEMENT_ARRAY_BUFFER, indices, GL15.GL_STATIC_DRAW);
    }

    static final class Frame {
        final Matrix4f modelViewProjection = new Matrix4f();
        final Matrix4f modelView = new Matrix4f();
        private final Matrix4f[] layerBase = new Matrix4f[3 * LAYERS];
        private final FloatBuffer rowS = BufferUtils.createFloatBuffer(3 * LAYERS * 3);
        private final FloatBuffer rowT = BufferUtils.createFloatBuffer(3 * LAYERS * 3);
        private final FloatBuffer eyePlane = BufferUtils.createFloatBuffer(3 * 3);
        private final Matrix4f translated = new Matrix4f();
        private final Matrix4f texGenModelView = new Matrix4f();
        private final Matrix4f inverse = new Matrix4f();
        private final Vector4f lightmapCoord = new Vector4f();
        private final Matrix4f preparedModelView = new Matrix4f();
        float cameraX, cameraY, cameraZ;
        float viewX, viewY, viewZ;
        float time;
        float lightmapU, lightmapV;
        boolean lightmapEnabled;
        private int[] planeAxis = new int[16];
        private boolean[] planeNegative = new boolean[16];
        private double[] planeValue = new double[16];
        private float[] planeData = new float[16 * FLOATS_PER_PLANE];
        private int planeCount;
        private int version;
        private float preparedCameraX, preparedCameraY, preparedCameraZ, preparedViewX, preparedViewY, preparedViewZ, preparedTime;

        Frame() {
            for (int i = 0; i < layerBase.length; i++) layerBase[i] = new Matrix4f();
        }

        void prepareLayers() {
            if (version != 0 && preparedCameraX == cameraX && preparedCameraY == cameraY && preparedCameraZ == cameraZ && preparedViewX == viewX && preparedViewY == viewY && preparedViewZ == viewZ && preparedTime == time && preparedModelView.equals(modelView))
                return;
            preparedCameraX = cameraX;
            preparedCameraY = cameraY;
            preparedCameraZ = cameraZ;
            preparedViewX = viewX;
            preparedViewY = viewY;
            preparedViewZ = viewZ;
            preparedTime = time;
            preparedModelView.set(modelView);
            version++;
            planeCount = 0;
            for (int axis = 0; axis < 3; axis++) {
                final float cameraS = axis == AXIS_X ? cameraZ : cameraX;
                final float cameraT = axis == AXIS_Y ? cameraZ : cameraY;
                final float cameraAxis = axis == AXIS_X ? cameraX : axis == AXIS_Y ? cameraY : cameraZ;
                for (int i = 0; i < LAYERS; i++) {
                    final float scale = LAYER_SCALE[i];
                    final Matrix4f base = layerBase[axis * LAYERS + i].identity().translate(0.0F, time, 0.0F).scale(scale, scale, scale).translate(0.5F, 0.5F, 0.0F).rotate(LAYER_ANGLE[i], 0.0F, 0.0F, 1.0F).translate(-0.5F, -0.5F, 0.0F).translate(-cameraS, -cameraT, -cameraAxis);
                    final int row = (axis * LAYERS + i) * 3;
                    rowS.put(row, base.m00()).put(row + 1, base.m10()).put(row + 2, base.m20());
                    rowT.put(row, base.m01()).put(row + 1, base.m11()).put(row + 2, base.m21());
                }
            }
            texGenModelView.set(modelView).translate(cameraX, cameraY, cameraZ).invert(inverse);
            eyePlane.put(0, inverse.m00()).put(1, inverse.m10()).put(2, inverse.m20());
            eyePlane.put(3, inverse.m01()).put(4, inverse.m11()).put(5, inverse.m21());
            eyePlane.put(6, inverse.m02()).put(7, inverse.m12()).put(8, inverse.m22());
        }

        void readLightmap(int blockLight) {
            final TextureUnitArray textures = GLSMTextures.getTextures();
            lightmapEnabled = textures.getTextureUnitStates(LIGHTMAP_UNIT).isEnabled() && textures.getTextureUnitBindings(LIGHTMAP_UNIT).getBinding() != 0;
            textures.getTextureUnitMatrix(LIGHTMAP_UNIT).transform(lightmapCoord.set(blockLight, 0.0F, 0.0F, 1.0F));
            lightmapU = lightmapCoord.x / lightmapCoord.w;
            lightmapV = lightmapCoord.y / lightmapCoord.w;
        }

        int plane(int axis, boolean negative, double plane) {
            for (int p = 0; p < planeCount; p++) {
                if (planeAxis[p] == axis && planeNegative[p] == negative && planeValue[p] == plane) return p;
            }
            if (planeCount == maxPlanes()) return -1;
            if (planeCount == planeAxis.length) {
                planeAxis = Arrays.copyOf(planeAxis, planeCount * 2);
                planeNegative = Arrays.copyOf(planeNegative, planeCount * 2);
                planeValue = Arrays.copyOf(planeValue, planeCount * 2);
                planeData = Arrays.copyOf(planeData, planeCount * 2 * FLOATS_PER_PLANE);
            }
            final int p = planeCount++;
            planeAxis[p] = axis;
            planeNegative[p] = negative;
            planeValue[p] = plane;

            final float viewS = axis == AXIS_X ? viewZ : viewX;
            final float viewT = axis == AXIS_Y ? viewZ : viewY;
            final float viewAxis = axis == AXIS_X ? viewX : axis == AXIS_Y ? viewY : viewZ;
            final float cameraAxis = axis == AXIS_X ? cameraX : axis == AXIS_Y ? cameraY : cameraZ;
            // TC's drawPlane* arithmetic, in its float order
            final float f8 = (float) (negative ? -plane : plane);
            final float f9 = negative ? f8 + viewAxis : f8 - viewAxis;
            final int base = p * FLOATS_PER_PLANE;
            for (int layer = 0; layer < LAYERS; layer++) {
                final float f5 = LAYER_DEPTH[layer];
                layerBase[axis * LAYERS + layer].translate(viewS * f5 / f9, viewT * f5 / f9, -cameraAxis, translated);
                planeData[base + layer * 2] = translated.m30();
                planeData[base + layer * 2 + 1] = translated.m31();

                final float f10 = negative ? f8 + f5 + viewAxis : f8 + f5 - viewAxis;
                float f11 = f9 / f10;
                f11 += (float) plane;
                // glTexGen stores the Q plane through the inverse of the modelview TC translated just before
                texGenModelView.set(modelView).translate(axis == AXIS_X ? f11 : cameraX, axis == AXIS_Y ? f11 : cameraY, axis == AXIS_X || axis == AXIS_Y ? cameraZ : f11).invert(inverse);
                planeData[base + LAYERS * 2 + layer] = axis == AXIS_X ? inverse.m30() : axis == AXIS_Y ? inverse.m31() : inverse.m32();
            }
            return p;
        }

        void clearPlanes() {
            planeCount = 0;
            version++;
        }
    }

    private static final class Uniforms {
        final GlUniformMatrix4f modelViewProjection;
        final GlUniformMatrix4f modelView;
        final GlUniformFloat3Array eyePlane;
        final GlUniformFloat3Array rowS;
        final GlUniformFloat3Array rowT;
        final GlUniformFloat4Array planes;
        final GlUniformFloat2v lightmapUv;
        final GlUniformFloat lightmapEnabled;
        final GlUniformFloat atlasSize;
        final GlUniformFloat4v fogParams;
        final GlUniformFloat3v fogColor;
        final GlUniformInt fogMode;
        final GlUniformInt fogDistanceMode;
        Frame uploadedFrame;
        int uploadedVersion;
        int uploadedPlanes;

        Uniforms(ShaderBindingContext context) {
            modelViewProjection = context.bindUniformIfPresent("u_ModelViewProjection", GlUniformMatrix4f::new);
            modelView = context.bindUniform("u_ModelView", GlUniformMatrix4f::new);
            eyePlane = context.bindUniform("u_EyePlane", GlUniformFloat3Array::new);
            rowS = context.bindUniform("u_RowS", GlUniformFloat3Array::new);
            rowT = context.bindUniform("u_RowT", GlUniformFloat3Array::new);
            planes = context.bindUniform("u_Planes", GlUniformFloat4Array::new);
            lightmapUv = context.bindUniform("u_LightmapUv", GlUniformFloat2v::new);
            lightmapEnabled = context.bindUniform("u_LightmapEnabled", GlUniformFloat::new);
            atlasSize = context.bindUniformIfPresent("u_AtlasSize", GlUniformFloat::new);
            fogParams = context.bindUniformIfPresent("u_FogParams", GlUniformFloat4v::new);
            fogColor = context.bindUniformIfPresent("u_FogColor", GlUniformFloat3v::new);
            fogMode = context.bindUniformIfPresent("u_FogMode", GlUniformInt::new);
            fogDistanceMode = context.bindUniformIfPresent("u_FogDistanceMode", GlUniformInt::new);
        }
    }
}
