package com.gtnewhorizons.angelica.rendering.tesr;

import com.gtnewhorizons.angelica.glsm.ffp.CubeParams;
import com.gtnewhorizons.angelica.glsm.ffp.UnitCubeMesh;
import net.coderbot.iris.vertices.IrisQuadView;
import net.coderbot.iris.vertices.NormalHelper;
import net.minecraft.client.model.ModelBase;
import net.minecraft.client.model.ModelBox;
import net.minecraft.client.model.ModelRenderer;
import net.minecraft.client.model.PositionTextureVertex;
import net.minecraft.client.model.TexturedQuad;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.lang.reflect.Field;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

import static com.gtnewhorizon.gtnhlib.bytebuf.MemoryUtilities.memAddress0;
import static com.gtnewhorizon.gtnhlib.bytebuf.MemoryUtilities.memAlloc;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

class ModelBoxCaptureTest {

    private static final float EPSILON = 1.0e-4f;

    private static final ByteBuffer CUBE = writeCube();

    private static ByteBuffer writeCube() {
        final ByteBuffer buffer = memAlloc(UnitCubeMesh.VERTEX_COUNT * UnitCubeMesh.VERTEX_STRIDE);
        UnitCubeMesh.write(memAddress0(buffer));
        return buffer;
    }

    private static float cube(int vertex, int offset) {
        return CUBE.getFloat(vertex * UnitCubeMesh.VERTEX_STRIDE + offset);
    }

    private static ModelRenderer part(int texWidth, int texHeight, boolean mirror) {
        final ModelBase base = new ModelBase() {};
        base.textureWidth = texWidth;
        base.textureHeight = texHeight;
        final ModelRenderer part = new ModelRenderer(base, "test");
        part.mirror = mirror;
        return part;
    }

    private static TexturedQuad[] quadsOf(ModelBox box) {
        try {
            final Field field = ModelBox.class.getDeclaredField("quadList");
            field.setAccessible(true);
            return (TexturedQuad[]) field.get(box);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError(e);
        }
    }

    private static CubeParams capture(ModelRenderer part, int texU, int texV, float x, float y, float z, int sizeX, int sizeY, int sizeZ, float inflate) {
        final ModelBox box = new ModelBox(part, texU, texV, x, y, z, sizeX, sizeY, sizeZ, inflate);
        return ModelBoxCapture.capture(quadsOf(box), part.textureWidth, part.textureHeight, part.mirror, texU, texV, x, y, z, sizeX, sizeY, sizeZ, inflate);
    }

    @ParameterizedTest
    @CsvSource({
        "64, 32, false, 0, 0, -4, -8, -2, 8, 12, 4, 0.0",
        "64, 32, true, 0, 0, -4, -8, -2, 8, 12, 4, 0.0",
        "128, 64, false, 33, 17, 0.5, 1.5, -3.5, 5, 7, 11, 0.5",
        "128, 64, true, 33, 17, 0.5, 1.5, -3.5, 5, 7, 11, 0.5",
        "64, 32, false, 0, 0, -8, 0, -8, 16, 0, 16, 0.125",
        "64, 32, true, 20, 4, -2, -2, 0, 4, 4, 0, 0.0625",
    })
    void unitCubeReproducesVanillaBox(int texWidth, int texHeight, boolean mirror, int texU, int texV, float x, float y, float z, int sizeX, int sizeY, int sizeZ, float inflate) {
        final ModelRenderer part = part(texWidth, texHeight, mirror);
        final ModelBox box = new ModelBox(part, texU, texV, x, y, z, sizeX, sizeY, sizeZ, inflate);
        final CubeParams params = ModelBoxCapture.capture(quadsOf(box), part.textureWidth, part.textureHeight, mirror, texU, texV, x, y, z, sizeX, sizeY, sizeZ, inflate);
        assertNotNull(params, "vanilla box must verify");

        final List<float[]> vanilla = new ArrayList<>();
        for (TexturedQuad quad : quadsOf(box)) {
            for (PositionTextureVertex vertex : quad.vertexPositions) {
                vanilla.add(new float[] { (float) vertex.vector3D.xCoord, (float) vertex.vector3D.yCoord, (float) vertex.vector3D.zCoord, vertex.texturePositionX, vertex.texturePositionY });
            }
        }

        for (int v = 0; v < UnitCubeMesh.VERTEX_COUNT; v++) {
            final float[] expanded = expand(params, v);
            if (!consume(vanilla, expanded)) {
                fail("unit cube vertex " + v + " has no vanilla counterpart: pos=" + expanded[0] + "," + expanded[1] + "," + expanded[2] + " uv=" + expanded[3] + "," + expanded[4]);
            }
        }
        assertTrue(vanilla.isEmpty(), "vanilla vertices left unmatched: " + vanilla.size());
    }

    private static float[] expand(CubeParams params, int vertex) {
        final float mirror = params.uScaleX() < 0.0f ? 1.0f : 0.0f;
        final float normalX = cube(vertex, UnitCubeMesh.OFFSET_NORMAL);
        final float midUK = cube(vertex, UnitCubeMesh.OFFSET_MID) - mirror * normalX;
        final float midUM = cube(vertex, UnitCubeMesh.OFFSET_MID + 4) - mirror * normalX;
        final float sign = 1.0f - 2.0f * mirror;
        final float uK = midUK + sign * cube(vertex, UnitCubeMesh.OFFSET_DELTA);
        final float uM = midUM + sign * cube(vertex, UnitCubeMesh.OFFSET_DELTA + 4);
        final float vL = cube(vertex, UnitCubeMesh.OFFSET_MID + 8) + cube(vertex, UnitCubeMesh.OFFSET_DELTA + 8);
        final float vM = cube(vertex, UnitCubeMesh.OFFSET_MID + 12) + cube(vertex, UnitCubeMesh.OFFSET_DELTA + 12);
        return new float[] {
            params.minX() + cube(vertex, UnitCubeMesh.OFFSET_POSITION) * params.spanX(),
            params.minY() + cube(vertex, UnitCubeMesh.OFFSET_POSITION + 4) * params.spanY(),
            params.minZ() + cube(vertex, UnitCubeMesh.OFFSET_POSITION + 8) * params.spanZ(),
            params.u0() + uK * Math.abs(params.uScaleX()) + uM * params.uScaleZ(),
            params.v0() + vL * params.vScaleY() + vM * params.vScaleZ(),
        };
    }

    private static boolean consume(List<float[]> pool, float[] wanted) {
        for (int i = 0; i < pool.size(); i++) {
            final float[] candidate = pool.get(i);
            boolean match = true;
            for (int c = 0; c < 5; c++) {
                if (Math.abs(candidate[c] - wanted[c]) >= EPSILON) {
                    match = false;
                    break;
                }
            }
            if (match) {
                pool.remove(i);
                return true;
            }
        }
        return false;
    }

    @Test
    void mutatedQuadListIsRejected() {
        final ModelRenderer part = part(64, 32, false);
        final ModelBox box = new ModelBox(part, 0, 0, -4, -8, -2, 8, 12, 4, 0.0f);
        final TexturedQuad[] quads = quadsOf(box);
        quads[3].vertexPositions[2] = quads[3].vertexPositions[2].setTexturePosition(0.75f, 0.75f);

        assertNull(ModelBoxCapture.capture(quads, part.textureWidth, part.textureHeight, false, 0, 0, -4, -8, -2, 8, 12, 4, 0.0f), "a rewritten uv must not verify");

        final TexturedQuad[] replaced = quadsOf(new ModelBox(part, 0, 0, -4, -8, -2, 8, 12, 4, 0.0f));
        replaced[0] = new TexturedQuad(new PositionTextureVertex[] {
            new PositionTextureVertex(0, 0, 0, 0, 0), new PositionTextureVertex(1, 0, 0, 1, 0),
            new PositionTextureVertex(1, 1, 0, 1, 1), new PositionTextureVertex(0, 1, 0, 0, 1) });

        assertNull(ModelBoxCapture.capture(replaced, part.textureWidth, part.textureHeight, false, 0, 0, -4, -8, -2, 8, 12, 4, 0.0f), "a replaced face must not verify");
    }

    @Test
    void degenerateBoxIsRejected() {
        final ModelRenderer part = part(64, 32, false);
        assertNull(capture(part, 0, 0, -4, 0, -2, 8, 0, 4, 0.0f), "a flat box has no face normal to reproduce");

        final ModelRenderer mirrored = part(64, 32, true);
        assertNull(capture(mirrored, 0, 0, 0, -8, -2, 0, 12, 4, 0.25f), "the mirror flag needs a nonzero width to ride on");
    }

    private record QuadView(TexturedQuad quad) implements IrisQuadView {

        @Override
        public float x(int index) {
            return (float) quad.vertexPositions[index].vector3D.xCoord;
        }

        @Override
        public float y(int index) {
            return (float) quad.vertexPositions[index].vector3D.yCoord;
        }

        @Override
        public float z(int index) {
            return (float) quad.vertexPositions[index].vector3D.zCoord;
        }

        @Override
        public float u(int index) {
            return quad.vertexPositions[index].texturePositionX;
        }

        @Override
        public float v(int index) {
            return quad.vertexPositions[index].texturePositionY;
        }
    }

    @ParameterizedTest
    @CsvSource({ "false", "true" })
    void cubeTangentsMatchVanillaQuads(boolean mirror) {
        final ModelRenderer part = part(64, 32, mirror);
        final ModelBox box = new ModelBox(part, 0, 0, -4, -8, -2, 8, 12, 4, 0.0f);
        final float s = mirror ? -1.0f : 1.0f;
        final Vector3f normal = new Vector3f();

        for (TexturedQuad quad : quadsOf(box)) {
            final QuadView view = new QuadView(quad);
            NormalHelper.computeFaceNormal(normal, view);
            final int face = faceOf(normal);
            final float[] tangent = UnitCubeMesh.FACES[face].tangent();
            assertEquals(NormalHelper.packNormal(s * tangent[0], s * tangent[1], s * tangent[2], s * tangent[3]), NormalHelper.computeTangent(normal.x, normal.y, normal.z, view), "face " + face + " tangent");
        }
    }

    private static int faceOf(Vector3f normal) {
        int found = -1;
        for (int face = 0; face < UnitCubeMesh.FACE_COUNT; face++) {
            final float[] expected = UnitCubeMesh.FACES[face].normal();
            if (Math.abs(normal.x - expected[0]) < EPSILON && Math.abs(normal.y - expected[1]) < EPSILON && Math.abs(normal.z - expected[2]) < EPSILON) {
                assertEquals(-1, found, "normal " + normal + " matches more than one cube face");
                found = face;
            }
        }
        assertTrue(found >= 0, "no cube face has normal " + normal);
        return found;
    }
}
