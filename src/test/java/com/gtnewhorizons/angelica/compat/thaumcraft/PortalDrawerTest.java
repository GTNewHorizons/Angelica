package com.gtnewhorizons.angelica.compat.thaumcraft;

import com.gtnewhorizons.angelica.glsm.GLCoreTest;
import com.gtnewhorizons.angelica.glsm.GLStateManager;
import com.gtnewhorizons.angelica.glsm.ffp.FfpFixture;
import com.gtnewhorizons.angelica.glsm.ffp.ShaderManager;
import net.coderbot.iris.gl.framebuffer.GlFramebuffer;
import org.joml.Matrix4f;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL14;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL30;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static com.gtnewhorizons.angelica.compat.thaumcraft.PortalRenderer.AXIS_X;
import static com.gtnewhorizons.angelica.compat.thaumcraft.PortalRenderer.AXIS_Y;
import static com.gtnewhorizons.angelica.compat.thaumcraft.PortalRenderer.AXIS_Z;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Checks that the single-pass portal shader looks like Thaumcraft's 16-layer draw.
 */
@GLCoreTest
class PortalDrawerTest {
    private static final int SIZE = 512;
    private static final int BACKGROUND = 0xFF1E140A;
    private static final float TIME = 0.4567F;
    // A camera far from the origin, where TC's fixed-function texture coordinates lose the most precision
    private static final float CAMERA_X = -814.3F, CAMERA_Y = 86.75F, CAMERA_Z = -246.2F;
    private static final float VIEW_X = 0.05F, VIEW_Y = -0.12F, VIEW_Z = 0.2F;

    private final List<double[]> faces = new ArrayList<>();
    private final PortalDrawer drawer = new PortalDrawer();
    private GlFramebuffer framebuffer;
    private int color, depth, tunnel, field, lightmap;

    private static void quad(double[] f, int red, int green, int blue) {
        final ByteBuffer vertices = BufferUtils.createByteBuffer(4 * 20);
        final double[][] corners = {{f[3], f[4], f[5]}, {f[6], f[7], f[8]}, {f[6] + f[9] - f[3], f[7] + f[10] - f[4], f[8] + f[11] - f[5]}, {f[9], f[10], f[11]}};
        for (double[] corner : corners) {
            vertices.putFloat((float) corner[0]).putFloat((float) corner[1]).putFloat((float) corner[2]);
            vertices.put((byte) red).put((byte) green).put((byte) blue).put((byte) 255);
            vertices.putShort((short) 180).putShort((short) 0);
        }
        vertices.flip();
        final int vao = GLStateManager.glGenVertexArrays();
        final int vbo = GLStateManager.glGenBuffers();
        try {
            GLStateManager.glBindVertexArray(vao);
            GLStateManager.glBindBuffer(GL15.GL_ARRAY_BUFFER, vbo);
            GLStateManager.glBufferData(GL15.GL_ARRAY_BUFFER, vertices, GL15.GL_STATIC_DRAW);
            GLStateManager.glVertexPointer(3, GL11.GL_FLOAT, 20, 0L);
            GLStateManager.glColorPointer(4, GL11.GL_UNSIGNED_BYTE, 20, 12L);
            GLStateManager.glClientActiveTexture(GL13.GL_TEXTURE1);
            GLStateManager.glTexCoordPointer(2, GL11.GL_SHORT, 20, 16L);
            GLStateManager.glEnableClientState(GL11.GL_TEXTURE_COORD_ARRAY);
            GLStateManager.glClientActiveTexture(GL13.GL_TEXTURE0);
            GLStateManager.glEnableClientState(GL11.GL_VERTEX_ARRAY);
            GLStateManager.glEnableClientState(GL11.GL_COLOR_ARRAY);
            GLStateManager.glDrawArrays(GL11.GL_QUADS, 0, 4);
        } finally {
            GLStateManager.glDisableClientState(GL11.GL_COLOR_ARRAY);
            GLStateManager.glClientActiveTexture(GL13.GL_TEXTURE1);
            GLStateManager.glDisableClientState(GL11.GL_TEXTURE_COORD_ARRAY);
            GLStateManager.glClientActiveTexture(GL13.GL_TEXTURE0);
            GLStateManager.glBindVertexArray(0);
            GLStateManager.glBindBuffer(GL15.GL_ARRAY_BUFFER, 0);
            GLStateManager.glDeleteBuffers(vbo);
            GLStateManager.glDeleteVertexArrays(vao);
        }
    }

    private static void clear() {
        GLStateManager.glClearColor(10 / 255.0F, 20 / 255.0F, 30 / 255.0F, 1.0F);
        GLStateManager.glClearDepth(1.0);
        GLStateManager.glClear(GL11.GL_COLOR_BUFFER_BIT | GL11.GL_DEPTH_BUFFER_BIT);
    }

    private static int channelDifference(int a, int b) {
        int worst = 0;
        for (int shift = 0; shift <= 16; shift += 8) {
            worst = Math.max(worst, Math.abs(((a >> shift) & 0xFF) - ((b >> shift) & 0xFF)));
        }
        return worst;
    }

    private static int thaumcraftTexture(String name) {
        try (InputStream in = PortalDrawerTest.class.getResourceAsStream("/assets/thaumcraft/textures/misc/" + name + ".png")) {
            if (in == null) return 0;
            final BufferedImage image = ImageIO.read(in);
            final int width = image.getWidth(), height = image.getHeight();
            final ByteBuffer texels = BufferUtils.createByteBuffer(width * height * 4);
            for (int y = 0; y < height; y++) {
                for (int x = 0; x < width; x++) {
                    final int argb = image.getRGB(x, y);
                    texels.put((byte) (argb >> 16)).put((byte) (argb >> 8)).put((byte) argb).put((byte) (argb >>> 24));
                }
            }
            texels.flip();
            final int id = texture(width, height, GL11.GL_RGBA8, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, texels);
            GLStateManager.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL11.GL_REPEAT);
            GLStateManager.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL11.GL_REPEAT);
            return id;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static int noiseTexture(int size, long seed, int filter) {
        final ByteBuffer texels = BufferUtils.createByteBuffer(size * size * 4);
        final Random random = new Random(seed);
        for (int i = 0; i < size * size; i++) {
            texels.put((byte) random.nextInt(256)).put((byte) random.nextInt(256)).put((byte) random.nextInt(256)).put((byte) 255);
        }
        texels.flip();
        final int id = texture(size, size, GL11.GL_RGBA8, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, texels);
        GLStateManager.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, filter);
        GLStateManager.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, filter);
        final int wrap = filter == GL11.GL_LINEAR ? GL12.GL_CLAMP_TO_EDGE : GL11.GL_REPEAT;
        GLStateManager.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, wrap);
        GLStateManager.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, wrap);
        return id;
    }

    private static int texture(int width, int height, int internalFormat, int format, int type, ByteBuffer data) {
        final int id = GLStateManager.glGenTextures();
        GLStateManager.glActiveTexture(GL13.GL_TEXTURE0);
        GLStateManager.glBindTexture(GL11.GL_TEXTURE_2D, id);
        GLStateManager.glTexImage2D(GL11.GL_TEXTURE_2D, 0, internalFormat, width, height, 0, format, type, data);
        GLStateManager.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_NEAREST);
        GLStateManager.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_NEAREST);
        return id;
    }

    private static FloatBuffer floats(float... values) {
        final FloatBuffer buffer = BufferUtils.createFloatBuffer(values.length);
        buffer.put(values).flip();
        return buffer;
    }

    @BeforeEach
    void setup() {
        FfpFixture.resetFfpState();
        GLStateManager.glUseProgram(0);
        ShaderManager.enable();
        GLStateManager.disableFog();
        GLStateManager.disableLighting();
        GLStateManager.disableCull();
        GLStateManager.disableBlend();
        GLStateManager.enableDepthTest();
        GLStateManager.glDepthFunc(GL11.GL_LEQUAL);
        GLStateManager.glDepthMask(true);

        color = texture(SIZE, SIZE, GL11.GL_RGBA8, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, null);
        depth = texture(SIZE, SIZE, GL14.GL_DEPTH_COMPONENT24, GL11.GL_DEPTH_COMPONENT, GL11.GL_FLOAT, null);
        framebuffer = new GlFramebuffer();
        framebuffer.addColorAttachment(0, color);
        framebuffer.addDepthAttachment(depth);
        framebuffer.drawBuffers(new int[]{0});
        framebuffer.readBuffer(0);
        framebuffer.bind();
        GLStateManager.glViewport(0, 0, SIZE, SIZE);

        lightmap = noiseTexture(16, 3, GL11.GL_LINEAR);
        GLStateManager.glActiveTexture(GL13.GL_TEXTURE1);
        GLStateManager.glBindTexture(GL11.GL_TEXTURE_2D, lightmap);
        GLStateManager.glEnable(GL11.GL_TEXTURE_2D);
        // EntityRenderer.enableLightmap's texture matrix
        GLStateManager.glMatrixMode(GL11.GL_TEXTURE);
        GLStateManager.glLoadIdentity();
        GLStateManager.glScalef(0.00390625F, 0.00390625F, 0.00390625F);
        GLStateManager.glTranslatef(8.0F, 8.0F, 8.0F);
        GLStateManager.glMatrixMode(GL11.GL_MODELVIEW);
        GLStateManager.glActiveTexture(GL13.GL_TEXTURE0);
        GLStateManager.enableTexture();

        GLStateManager.setProjectionMatrix(new Matrix4f().perspective((float) Math.toRadians(70), 1.0F, 0.05F, 256.0F));
        GLStateManager.setModelViewMatrix(new Matrix4f().rotateX(0.2F).rotateY(0.25F));
        addFaces();
    }

    @AfterEach
    void cleanup() {
        drawer.destroy();
        framebuffer.destroy();
        GLStateManager.glBindFramebuffer(GL30.GL_FRAMEBUFFER, 0);
        GLStateManager.glDeleteTextures(color);
        GLStateManager.glDeleteTextures(depth);
        if (tunnel != 0) GLStateManager.glDeleteTextures(tunnel);
        if (field != 0) GLStateManager.glDeleteTextures(field);
        GLStateManager.glDeleteTextures(lightmap);
        FfpFixture.resetFfpState();
    }

    @Test
    void matchesThaumcraftsLayersWithItsTextures() {
        tunnel = thaumcraftTexture("tunnel");
        field = thaumcraftTexture("particlefield");
        assumeTrue(tunnel != 0 && field != 0, "Thaumcraft's textures are not on the test classpath");
        final Parity parity = compare();
        assertTrue(parity.exact >= parity.covered * 0.99, "single pass differs from TC's layers: " + parity);
    }

    @Test
    void staysWithinOneStepOnNoiseTextures() {
        tunnel = noiseTexture(64, 1, GL11.GL_NEAREST);
        field = noiseTexture(256, 2, GL11.GL_NEAREST);
        final Parity parity = compare();
        assertTrue(parity.withinOne >= parity.covered * 0.97, "single pass drifts from TC's layers: " + parity);
    }

    @Test
    void fogsEachLayerLikeTheMirror() {
        tunnel = noiseTexture(64, 1, GL11.GL_NEAREST);
        field = noiseTexture(256, 2, GL11.GL_NEAREST);
        GLStateManager.enableFog();
        GLStateManager.glFog(GL11.GL_FOG_COLOR, floats(0.05F, 0.08F, 0.15F, 1.0F));
        // Minecraft's distance fog, then its underwater fog
        GLStateManager.glFogi(GL11.GL_FOG_MODE, GL11.GL_LINEAR);
        GLStateManager.glFogf(GL11.GL_FOG_START, 2.0F);
        GLStateManager.glFogf(GL11.GL_FOG_END, 40.0F);
        final Parity linear = compare(true);
        assertTrue(linear.withinOne >= linear.covered * 0.97, "linear fog drifts from TC's layers: " + linear);
        GLStateManager.glFogi(GL11.GL_FOG_MODE, GL11.GL_EXP);
        GLStateManager.glFogf(GL11.GL_FOG_DENSITY, 0.02F);
        final Parity exp = compare(true);
        assertTrue(exp.withinOne >= exp.covered * 0.97, "exp fog drifts from TC's layers: " + exp);
    }

    private Parity compare() {
        return compare(false);
    }

    private Parity compare(boolean fogged) {
        clear();
        for (double[] face : faces) referenceFace(face);
        final int[] reference = FfpFixture.readRegion(SIZE);

        clear();
        final PortalDrawer.Frame frame = new PortalDrawer.Frame();
        GLStateManager.getProjectionMatrix().mul(GLStateManager.getModelViewMatrix(), frame.modelViewProjection);
        frame.modelView.set(GLStateManager.getModelViewMatrix());
        frame.cameraX = CAMERA_X;
        frame.cameraY = CAMERA_Y;
        frame.cameraZ = CAMERA_Z;
        frame.viewX = VIEW_X;
        frame.viewY = VIEW_Y;
        frame.viewZ = VIEW_Z;
        frame.time = TIME;
        frame.prepareLayers();
        frame.readLightmap(180);
        final float[] packed = new float[faces.size() * PortalDrawer.FLOATS_PER_FACE];
        for (int i = 0; i < faces.size(); i++) {
            final double[] f = faces.get(i);
            final int plane = frame.plane((int) f[0], f[1] != 0, f[2]);
            PortalDrawer.putFace(packed, i, (int) f[0], plane, f[3], f[4], f[5], f[6], f[7], f[8], f[9], f[10], f[11]);
        }
        drawer.drawDirect(packed, faces.size(), tunnel, field, frame, fogged);
        final int[] candidate = FfpFixture.readRegion(SIZE);

        int covered = 0, exact = 0, withinOne = 0, worst = 0;
        for (int i = 0; i < reference.length; i++) {
            if (reference[i] == BACKGROUND && candidate[i] == BACKGROUND) continue;
            covered++;
            final int difference = channelDifference(reference[i], candidate[i]);
            if (difference == 0) exact++;
            if (difference <= 1) withinOne++;
            worst = Math.max(worst, difference);
        }
        final Parity parity = new Parity(covered, exact, withinOne, worst);
        System.out.println("PortalDrawerTest: " + parity);
        assertTrue(covered > reference.length / 10, "faces covered too little of the frame: " + parity);
        return parity;
    }

    private void addFaces() {
        for (int x = -2; x <= 1; x++) {
            for (int y = -1; y <= 0; y++) {
                face(AXIS_Z, false, -6.0, x, y, -6.0, x, y + 1.0, -6.0, x + 1.0, y, -6.0);
            }
        }
        for (int y = -1; y <= 0; y++) {
            face(AXIS_Z, true, -7.0, 2.0, y + 1.0, -7.0, 2.0, y, -7.0, 3.0, y + 1.0, -7.0);
        }
        for (int z = -7; z <= -6; z++) {
            face(AXIS_X, false, -4.0, -4.0, -1.0, z, -4.0, -1.0, z + 1.0, -4.0, 0.0, z);
            final double plane = 3.0 + 0.001F;
            face(AXIS_X, true, plane, plane, 1.0, z, plane, 1.0, z + 1.0, plane, 0.0, z);
        }
        for (int x = -1; x <= 0; x++) {
            for (int z = -5; z <= -4; z++) {
                face(AXIS_Y, false, -2.0, x, -2.0, z + 1.0, x, -2.0, z, x + 1.0, -2.0, z + 1.0);
                final double plane = 2.0 + 0.001F;
                face(AXIS_Y, true, plane, x, plane, z, x, plane, z + 1.0, x + 1.0, plane, z);
            }
        }
        final double obelisk = -9.0 + 0.01F;
        face(AXIS_Z, false, obelisk, -3.0, -1.0, obelisk, -3.0, 2.0, obelisk, -2.0, -1.0, obelisk);
        final double lock = -12.0 + 0.5F;
        face(AXIS_Z, true, lock, 1.0, 4.0, lock, 1.0, -1.0, lock, 6.0, 4.0, lock);
    }

    private void face(int axis, boolean negative, double plane, double x0, double y0, double z0, double x1, double y1, double z1, double x3, double y3, double z3) {
        faces.add(new double[]{axis, negative ? 1 : 0, plane, x0, y0, z0, x1, y1, z1, x3, y3, z3});
    }

    private void referenceFace(double[] f) {
        final int axis = (int) f[0];
        final boolean negative = f[1] != 0;
        final double plane = f[2];
        final float px = CAMERA_X, py = CAMERA_Y, pz = CAMERA_Z;
        final float view = axis == AXIS_X ? VIEW_X : axis == AXIS_Y ? VIEW_Y : VIEW_Z;
        final Random random = new Random(31100L);
        for (int i = 0; i < 16; ++i) {
            GLStateManager.glPushMatrix();
            float f5 = (float) (16 - i);
            float f6 = 0.0625F;
            float f7 = 1.0F / (f5 + 1.0F);
            if (i == 0) {
                GLStateManager.glBindTexture(GL11.GL_TEXTURE_2D, tunnel);
                f7 = 0.1F;
                f5 = 65.0F;
                f6 = 0.125F;
                GLStateManager.enableBlend();
                GLStateManager.glBlendFunc(770, 771);
            }
            if (i == 1) {
                GLStateManager.glBindTexture(GL11.GL_TEXTURE_2D, field);
                GLStateManager.enableBlend();
                GLStateManager.glBlendFunc(1, 1);
                f6 = 0.5F;
            }
            final float f8 = (float) (negative ? -plane : plane);
            final float f9 = negative ? f8 + view : f8 - view;
            final float f10 = negative ? f8 + f5 + view : f8 + f5 - view;
            float f11 = f9 / f10;
            f11 += (float) plane;
            switch (axis) {
                case AXIS_X -> GLStateManager.glTranslatef(f11, py, pz);
                case AXIS_Y -> GLStateManager.glTranslatef(px, f11, pz);
                default -> GLStateManager.glTranslatef(px, py, f11);
            }
            GLStateManager.glTexGeni(GL11.GL_S, GL11.GL_TEXTURE_GEN_MODE, GL11.GL_OBJECT_LINEAR);
            GLStateManager.glTexGeni(GL11.GL_T, GL11.GL_TEXTURE_GEN_MODE, GL11.GL_OBJECT_LINEAR);
            GLStateManager.glTexGeni(GL11.GL_R, GL11.GL_TEXTURE_GEN_MODE, GL11.GL_OBJECT_LINEAR);
            GLStateManager.glTexGeni(GL11.GL_Q, GL11.GL_TEXTURE_GEN_MODE, GL11.GL_EYE_LINEAR);
            switch (axis) {
                case AXIS_X -> {
                    GLStateManager.glTexGen(GL11.GL_T, GL11.GL_OBJECT_PLANE, floats(0, 1, 0, 0));
                    GLStateManager.glTexGen(GL11.GL_S, GL11.GL_OBJECT_PLANE, floats(0, 0, 1, 0));
                    GLStateManager.glTexGen(GL11.GL_R, GL11.GL_OBJECT_PLANE, floats(0, 0, 0, 1));
                    GLStateManager.glTexGen(GL11.GL_Q, GL11.GL_EYE_PLANE, floats(1, 0, 0, 0));
                }
                case AXIS_Y -> {
                    GLStateManager.glTexGen(GL11.GL_S, GL11.GL_OBJECT_PLANE, floats(1, 0, 0, 0));
                    GLStateManager.glTexGen(GL11.GL_T, GL11.GL_OBJECT_PLANE, floats(0, 0, 1, 0));
                    GLStateManager.glTexGen(GL11.GL_R, GL11.GL_OBJECT_PLANE, floats(0, 0, 0, 1));
                    GLStateManager.glTexGen(GL11.GL_Q, GL11.GL_EYE_PLANE, floats(0, 1, 0, 0));
                }
                default -> {
                    GLStateManager.glTexGen(GL11.GL_S, GL11.GL_OBJECT_PLANE, floats(1, 0, 0, 0));
                    GLStateManager.glTexGen(GL11.GL_T, GL11.GL_OBJECT_PLANE, floats(0, 1, 0, 0));
                    GLStateManager.glTexGen(GL11.GL_R, GL11.GL_OBJECT_PLANE, floats(0, 0, 0, 1));
                    GLStateManager.glTexGen(GL11.GL_Q, GL11.GL_EYE_PLANE, floats(0, 0, 1, 0));
                }
            }
            GLStateManager.glEnable(GL11.GL_TEXTURE_GEN_S);
            GLStateManager.glEnable(GL11.GL_TEXTURE_GEN_T);
            GLStateManager.glEnable(GL11.GL_TEXTURE_GEN_R);
            GLStateManager.glEnable(GL11.GL_TEXTURE_GEN_Q);
            GLStateManager.glPopMatrix();
            GLStateManager.glMatrixMode(GL11.GL_TEXTURE);
            GLStateManager.glPushMatrix();
            GLStateManager.glLoadIdentity();
            GLStateManager.glTranslatef(0.0F, TIME, 0.0F);
            GLStateManager.glScalef(f6, f6, f6);
            GLStateManager.glTranslatef(0.5F, 0.5F, 0.0F);
            GLStateManager.glRotatef((float) (i * i * 4321 + i * 9) * 2.0F, 0.0F, 0.0F, 1.0F);
            GLStateManager.glTranslatef(-0.5F, -0.5F, 0.0F);
            switch (axis) {
                case AXIS_X -> {
                    GLStateManager.glTranslatef(-pz, -py, -px);
                    GLStateManager.glTranslatef(VIEW_Z * f5 / f9, VIEW_Y * f5 / f9, -px);
                }
                case AXIS_Y -> {
                    GLStateManager.glTranslatef(-px, -pz, -py);
                    GLStateManager.glTranslatef(VIEW_X * f5 / f9, VIEW_Z * f5 / f9, -py);
                }
                default -> {
                    GLStateManager.glTranslatef(-px, -py, -pz);
                    GLStateManager.glTranslatef(VIEW_X * f5 / f9, VIEW_Y * f5 / f9, -pz);
                }
            }
            float red = random.nextFloat() * 0.5F + 0.1F;
            float green = random.nextFloat() * 0.5F + 0.4F;
            float blue = random.nextFloat() * 0.5F + 0.5F;
            if (i == 0) red = green = blue = 1.0F;
            // Tessellator.setColorRGBA_F stores bytes, and its brightness 180 becomes the lightmap coordinate
            quad(f, (int) (red * f7 * 255.0F), (int) (green * f7 * 255.0F), (int) (blue * f7 * 255.0F));
            GLStateManager.glPopMatrix();
            GLStateManager.glMatrixMode(GL11.GL_MODELVIEW);
        }
        GLStateManager.disableBlend();
        GLStateManager.glDisable(GL11.GL_TEXTURE_GEN_S);
        GLStateManager.glDisable(GL11.GL_TEXTURE_GEN_T);
        GLStateManager.glDisable(GL11.GL_TEXTURE_GEN_R);
        GLStateManager.glDisable(GL11.GL_TEXTURE_GEN_Q);
    }

    private record Parity(int covered, int exact, int withinOne, int worst) {
        @Override
        public String toString() {
            return String.format("covered=%d exact=%.3f%% withinOne=%.3f%% worstChannel=%d", covered, 100.0 * exact / covered, 100.0 * withinOne / covered, worst);
        }
    }
}
