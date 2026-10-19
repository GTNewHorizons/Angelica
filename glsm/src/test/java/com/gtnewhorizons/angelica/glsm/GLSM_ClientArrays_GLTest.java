package com.gtnewhorizons.angelica.glsm;

import com.gtnewhorizon.gtnhlib.bytebuf.MemoryUtilities;
import com.gtnewhorizon.gtnhlib.client.renderer.vertex.VertexFlags;
import com.gtnewhorizon.gtnhlib.client.renderer.vertex.VertexFormatElement.Usage;
import com.gtnewhorizons.angelica.glsm.ffp.VAOManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL14;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;
import org.lwjgl.opengl.GL33;

import java.nio.FloatBuffer;
import java.nio.IntBuffer;

import static com.gtnewhorizons.angelica.glsm.LegacyQueryFixture.CLIENT_ARRAY_CAPS;
import static com.gtnewhorizons.angelica.glsm.LegacyQueryFixture.assertEnableRoundTrip;
import static com.gtnewhorizons.angelica.glsm.LegacyQueryFixture.drainClientAttribStack;
import static com.gtnewhorizons.angelica.glsm.LegacyQueryFixture.hex;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeFalse;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/** Conventional client arrays and the client attrib stack, emulated on a core-profile vertex array object. */
@GLCoreTest
public class GLSM_ClientArrays_GLTest {

    private int savedVao;
    private int vao;
    private int vbo;

    @BeforeEach
    void bindFreshVertexArray() {
        savedVao = GLStateManager.getBoundVAO();
        vao = GLStateManager.glGenVertexArrays();
        vbo = GLStateManager.glGenBuffers();
        GLStateManager.glBindVertexArray(vao);
    }

    @AfterEach
    void deleteVertexArray() {
        GLStateManager.glBindVertexArray(savedVao);
        GLStateManager.glDeleteVertexArrays(vao);
        GLStateManager.glDeleteBuffers(vbo);
    }

    private static boolean attribHasSize3(Usage usage) {
        final VAOManager.Attrib attrib = GLStateManager.ctx().vaos.attrib(usage.getAttributeLocation());
        return attrib != null && attrib.size == 3;
    }

    private static boolean positionArrayEnabled() {
        final VAOManager.Attrib position = GLStateManager.ctx().vaos.attrib(Usage.POSITION.getAttributeLocation());
        return position != null && position.enabled;
    }

    private static boolean colorArrayEnabled() {
        return (GLStateManager.ctx().vaos.getVertexFlags() & VertexFlags.COLOR_BIT) != 0;
    }

    private static int driverAttrib(int index, int pname) {
        final IntBuffer out = BufferUtils.createIntBuffer(16);
        GL20.glGetVertexAttrib(index, pname, out);
        return out.get(0);
    }

    private static void assertClientArray(String name, int sizePname, int size, int typePname, int type, int stridePname, int stride, int bindingPname, int binding) {
        if (sizePname != 0) assertEquals(size, GLStateManager.glGetInteger(sizePname), name + " size");
        if (typePname != 0) assertEquals(type, GLStateManager.glGetInteger(typePname), name + " type");
        assertEquals(stride, GLStateManager.glGetInteger(stridePname), name + " stride");
        assertEquals(binding, GLStateManager.glGetInteger(bindingPname), name + " buffer binding");
    }

    private static void assertVertexArray(String name, int size, int type, int stride, int binding) {
        assertClientArray(name, GL11.GL_VERTEX_ARRAY_SIZE, size, GL11.GL_VERTEX_ARRAY_TYPE, type, GL11.GL_VERTEX_ARRAY_STRIDE, stride, GL15.GL_VERTEX_ARRAY_BUFFER_BINDING, binding);
    }

    private static void assertTexCoordArray(String name, int size, int type, int stride, int binding) {
        assertClientArray(name, GL11.GL_TEXTURE_COORD_ARRAY_SIZE, size, GL11.GL_TEXTURE_COORD_ARRAY_TYPE, type, GL11.GL_TEXTURE_COORD_ARRAY_STRIDE, stride,
            GL15.GL_TEXTURE_COORD_ARRAY_BUFFER_BINDING, binding);
    }

    @Test
    void vertexArrayStateRestoredOnlyWithVertexArrayBit() {
        final int position = Usage.POSITION.getAttributeLocation();
        try {
            GLStateManager.glClientActiveTexture(GL13.GL_TEXTURE0);
            final int flagsBefore = GLStateManager.ctx().vaos.getVertexFlags();
            assertFalse(positionArrayEnabled());
            assertFalse(colorArrayEnabled());

            GLStateManager.glPushClientAttrib(GL11.GL_CLIENT_VERTEX_ARRAY_BIT);
            GLStateManager.glEnableClientState(GL11.GL_VERTEX_ARRAY);
            GLStateManager.glEnableClientState(GL11.GL_COLOR_ARRAY);
            GLStateManager.glClientActiveTexture(GL13.GL_TEXTURE1);
            assertTrue(positionArrayEnabled());
            assertTrue(colorArrayEnabled());
            GLStateManager.glPopClientAttrib();
            assertFalse(positionArrayEnabled(), "pop disables an array enabled inside the push");
            assertEquals(GL11.GL_FALSE, driverAttrib(position, GL20.GL_VERTEX_ATTRIB_ARRAY_ENABLED));
            assertFalse(colorArrayEnabled());
            assertEquals(flagsBefore, GLStateManager.ctx().vaos.getVertexFlags());
            assertEquals(0, GLStateManager.getClientActiveTextureUnit());

            GLStateManager.glEnableClientState(GL11.GL_VERTEX_ARRAY);
            GLStateManager.glPushClientAttrib(GL11.GL_CLIENT_VERTEX_ARRAY_BIT);
            GLStateManager.glDisableClientState(GL11.GL_VERTEX_ARRAY);
            GLStateManager.glPopClientAttrib();
            assertTrue(positionArrayEnabled(), "pop re-enables an array disabled inside the push");
            assertEquals(GL11.GL_TRUE, driverAttrib(position, GL20.GL_VERTEX_ATTRIB_ARRAY_ENABLED));
            GLStateManager.glDisableClientState(GL11.GL_VERTEX_ARRAY);

            GLStateManager.glPushClientAttrib(GL11.GL_CLIENT_PIXEL_STORE_BIT);
            GLStateManager.glEnableClientState(GL11.GL_VERTEX_ARRAY);
            GLStateManager.glEnableClientState(GL11.GL_COLOR_ARRAY);
            GLStateManager.glClientActiveTexture(GL13.GL_TEXTURE1);
            GLStateManager.glPopClientAttrib();
            assertTrue(positionArrayEnabled(), "a pixel-store push must not restore array enables");
            assertTrue(colorArrayEnabled());
            assertEquals(1, GLStateManager.getClientActiveTextureUnit());
        } finally {
            drainClientAttribStack();
            GLStateManager.glClientActiveTexture(GL13.GL_TEXTURE0);
            GLStateManager.glDisableClientState(GL11.GL_VERTEX_ARRAY);
            GLStateManager.glDisableClientState(GL11.GL_COLOR_ARRAY);
        }
    }

    @Test
    void clientActiveTextureRoundTrips() {
        try {
            GLStateManager.glBindBuffer(GL15.GL_ARRAY_BUFFER, vbo);
            assertEquals(GLStateManager.MAX_TEXTURE_COORDS, GLStateManager.glGetInteger(GL20.GL_MAX_TEXTURE_COORDS));

            GLStateManager.glClientActiveTexture(GL13.GL_TEXTURE1);
            assertEquals(GL13.GL_TEXTURE1, GLStateManager.glGetInteger(GL13.GL_CLIENT_ACTIVE_TEXTURE));
            GLStateManager.glClientActiveTexture(0);
            assertEquals(GL13.GL_TEXTURE1, GLStateManager.glGetInteger(GL13.GL_CLIENT_ACTIVE_TEXTURE));
            GLStateManager.glClientActiveTexture(GL13.GL_TEXTURE0 + GLStateManager.MAX_TEXTURE_UNITS);
            assertEquals(GL13.GL_TEXTURE1, GLStateManager.glGetInteger(GL13.GL_CLIENT_ACTIVE_TEXTURE));
            GLStateManager.glClientActiveTexture(GL13.GL_TEXTURE0 + GLStateManager.MAX_TEXTURE_COORDS);
            assertEquals(GL13.GL_TEXTURE1, GLStateManager.glGetInteger(GL13.GL_CLIENT_ACTIVE_TEXTURE), "units past GL_MAX_TEXTURE_COORDS are rejected");
            assertEquals(1, GLStateManager.getClientActiveTextureUnit());

            GLStateManager.glTexCoordPointer(2, GL11.GL_FLOAT, 0, 0L);
            final VAOManager.Attrib attrib = GLStateManager.ctx().vaos.attrib(Usage.SECONDARY_UV.getAttributeLocation());
            assertNotNull(attrib);
            assertEquals(2, attrib.size);

            assumeTrue(GLStateManager.MAX_TEXTURE_COORDS > 5);
            GLStateManager.glClientActiveTexture(GL13.GL_TEXTURE5);
            assertEquals(GL13.GL_TEXTURE5, GLStateManager.glGetInteger(GL13.GL_CLIENT_ACTIVE_TEXTURE));
            GLStateManager.glTexCoordPointer(3, GL11.GL_FLOAT, 0, 0L);
            assertEquals(3, GLStateManager.glGetInteger(GL11.GL_TEXTURE_COORD_ARRAY_SIZE));
            assertFalse(attribHasSize3(Usage.PRIMARY_UV));
            assertFalse(attribHasSize3(Usage.SECONDARY_UV));
        } finally {
            GLStateManager.glClientActiveTexture(GL13.GL_TEXTURE0);
            GLStateManager.glBindBuffer(GL15.GL_ARRAY_BUFFER, 0);
        }
    }

    @Test
    void conventionalPointerSurvivesGenericOverride() {
        final int position = Usage.POSITION.getAttributeLocation();
        try {
            GLStateManager.glBindBuffer(GL15.GL_ARRAY_BUFFER, vbo);

            GLStateManager.glVertexPointer(3, GL11.GL_FLOAT, 12, 0L);
            GLStateManager.glEnableClientState(GL11.GL_VERTEX_ARRAY);
            GLStateManager.glVertexAttribPointer(position, 2, GL11.GL_FLOAT, false, 8, 4L);
            assertEquals(3, VAOManager.get(position).size, "disabled generic pointer must not displace an enabled conventional array");
            assertEquals(3, driverAttrib(position, GL20.GL_VERTEX_ATTRIB_ARRAY_SIZE));

            GLStateManager.glEnableVertexAttribArray(position);
            assertEquals(2, VAOManager.get(position).size, "enabled generic attribute wins");
            assertTrue(VAOManager.get(position).genericPointer);
            assertEquals(2, driverAttrib(position, GL20.GL_VERTEX_ATTRIB_ARRAY_SIZE));
            assertEquals(8, driverAttrib(position, GL20.GL_VERTEX_ATTRIB_ARRAY_STRIDE));

            GLStateManager.glBindBuffer(GL15.GL_ARRAY_BUFFER, 0);
            GLStateManager.glDisableVertexAttribArray(position);
            assertEquals(3, VAOManager.get(position).size, "conventional pointer must come back when the generic attribute is disabled");
            assertEquals(12, VAOManager.get(position).stride);
            assertFalse(VAOManager.get(position).genericPointer);
            assertEquals(3, driverAttrib(position, GL20.GL_VERTEX_ATTRIB_ARRAY_SIZE));
            assertEquals(12, driverAttrib(position, GL20.GL_VERTEX_ATTRIB_ARRAY_STRIDE));
            assertEquals(vbo, driverAttrib(position, GL15.GL_VERTEX_ATTRIB_ARRAY_BUFFER_BINDING));
            assertEquals(GL11.GL_TRUE, driverAttrib(position, GL20.GL_VERTEX_ATTRIB_ARRAY_ENABLED), "conventional array stays enabled at draw level");
            assertTrue(GLStateManager.glIsEnabled(GL11.GL_VERTEX_ARRAY));
            assertEquals(0, GLStateManager.glGetInteger(GL15.GL_ARRAY_BUFFER_BINDING), "repoint must restore the array buffer binding");
        } finally {
            GLStateManager.glDisableVertexAttribArray(position);
            GLStateManager.glDisableClientState(GL11.GL_VERTEX_ARRAY);
            GLStateManager.glBindBuffer(GL15.GL_ARRAY_BUFFER, 0);
        }
    }

    @Test
    void conventionalPointerSetUnderEnabledGenericAppliesOnceGenericIsDisabled() {
        final int position = Usage.POSITION.getAttributeLocation();
        try {
            GLStateManager.glBindBuffer(GL15.GL_ARRAY_BUFFER, vbo);

            GLStateManager.glVertexAttribPointer(position, 2, GL11.GL_FLOAT, false, 8, 0L);
            GLStateManager.glEnableVertexAttribArray(position);
            GLStateManager.glVertexPointer(3, GL11.GL_FLOAT, 12, 0L);
            GLStateManager.glEnableClientState(GL11.GL_VERTEX_ARRAY);
            assertEquals(2, VAOManager.get(position).size);
            assertEquals(2, driverAttrib(position, GL20.GL_VERTEX_ATTRIB_ARRAY_SIZE));

            GLStateManager.glDisableVertexAttribArray(position);
            assertEquals(3, VAOManager.get(position).size);
            assertEquals(3, driverAttrib(position, GL20.GL_VERTEX_ATTRIB_ARRAY_SIZE));
            assertEquals(GL11.GL_TRUE, driverAttrib(position, GL20.GL_VERTEX_ATTRIB_ARRAY_ENABLED));

            GLStateManager.glDisableClientState(GL11.GL_VERTEX_ARRAY);
            assertEquals(GL11.GL_FALSE, driverAttrib(position, GL20.GL_VERTEX_ATTRIB_ARRAY_ENABLED));
            assertFalse(positionArrayEnabled());
        } finally {
            GLStateManager.glDisableVertexAttribArray(position);
            GLStateManager.glDisableClientState(GL11.GL_VERTEX_ARRAY);
            GLStateManager.glBindBuffer(GL15.GL_ARRAY_BUFFER, 0);
        }
    }

    @Test
    void drawVertexFlagsDoNotAnswerClientArrayEnables() {
        try {
            GLStateManager.glClientActiveTexture(GL13.GL_TEXTURE0);
            VAOManager.setCurrentVertexFlags(VertexFlags.COLOR_BIT | VertexFlags.NORMAL_BIT | VertexFlags.TEXTURE_BIT);
            assertFalse(GLStateManager.glIsEnabled(GL11.GL_COLOR_ARRAY));
            assertFalse(GLStateManager.glIsEnabled(GL11.GL_NORMAL_ARRAY));
            assertFalse(GLStateManager.glIsEnabled(GL11.GL_TEXTURE_COORD_ARRAY));

            GLStateManager.glEnableClientState(GL11.GL_COLOR_ARRAY);
            VAOManager.setCurrentVertexFlags(0);
            assertTrue(GLStateManager.glIsEnabled(GL11.GL_COLOR_ARRAY));
        } finally {
            GLStateManager.glDisableClientState(GL11.GL_COLOR_ARRAY);
            VAOManager.setCurrentVertexFlags(0);
        }
    }

    @Test
    void clientArrayPointerQueriesAreAnsweredFromCache() {
        final int vaoB = GLStateManager.glGenVertexArrays();
        final IntBuffer ints = BufferUtils.createIntBuffer(16);
        try {
            GLStateManager.glBindBuffer(GL15.GL_ARRAY_BUFFER, vbo);
            GLStateManager.glClientActiveTexture(GL13.GL_TEXTURE0);

            assertVertexArray("default vertex", 4, GL11.GL_FLOAT, 0, 0);
            assertClientArray("default normal", 0, 0, GL11.GL_NORMAL_ARRAY_TYPE, GL11.GL_FLOAT, GL11.GL_NORMAL_ARRAY_STRIDE, 0, GL15.GL_NORMAL_ARRAY_BUFFER_BINDING, 0);
            assertClientArray("default color", GL11.GL_COLOR_ARRAY_SIZE, 4, GL11.GL_COLOR_ARRAY_TYPE, GL11.GL_FLOAT, GL11.GL_COLOR_ARRAY_STRIDE, 0, GL15.GL_COLOR_ARRAY_BUFFER_BINDING, 0);
            assertClientArray("default secondary color", GL14.GL_SECONDARY_COLOR_ARRAY_SIZE, 3, GL14.GL_SECONDARY_COLOR_ARRAY_TYPE, GL11.GL_FLOAT,
                GL14.GL_SECONDARY_COLOR_ARRAY_STRIDE, 0, GL15.GL_SECONDARY_COLOR_ARRAY_BUFFER_BINDING, 0);
            assertClientArray("default fog coord", 0, 0, GL14.GL_FOG_COORDINATE_ARRAY_TYPE, GL11.GL_FLOAT, GL14.GL_FOG_COORDINATE_ARRAY_STRIDE, 0,
                GL15.GL_FOG_COORDINATE_ARRAY_BUFFER_BINDING, 0);
            assertClientArray("default index", 0, 0, GL11.GL_INDEX_ARRAY_TYPE, GL11.GL_FLOAT, GL11.GL_INDEX_ARRAY_STRIDE, 0, GL15.GL_INDEX_ARRAY_BUFFER_BINDING, 0);
            assertClientArray("default edge flag", 0, 0, 0, 0, GL11.GL_EDGE_FLAG_ARRAY_STRIDE, 0, GL15.GL_EDGE_FLAG_ARRAY_BUFFER_BINDING, 0);
            assertTexCoordArray("default texcoord", 4, GL11.GL_FLOAT, 0, 0);
            assertNull(GLStateManager.glGetPointer(GL11.GL_VERTEX_ARRAY_POINTER, 4));

            GLStateManager.glVertexPointer(3, GL11.GL_SHORT, 20, 8L);
            GLStateManager.glNormalPointer(GL11.GL_BYTE, 16, 4L);
            GLStateManager.glColorPointer(4, GL11.GL_UNSIGNED_BYTE, 16, 12L);
            GLStateManager.glSecondaryColorPointer(3, GL11.GL_FLOAT, 12, 0L);
            GLStateManager.glFogCoordPointer(GL11.GL_FLOAT, 4, 0L);
            GLStateManager.glEdgeFlagPointer(7, 0L);
            GLStateManager.glTexCoordPointer(2, GL11.GL_FLOAT, 8, 0L);

            assertVertexArray("vertex", 3, GL11.GL_SHORT, 20, vbo);
            assertClientArray("normal", 0, 0, GL11.GL_NORMAL_ARRAY_TYPE, GL11.GL_BYTE, GL11.GL_NORMAL_ARRAY_STRIDE, 16, GL15.GL_NORMAL_ARRAY_BUFFER_BINDING, vbo);
            assertClientArray("color", GL11.GL_COLOR_ARRAY_SIZE, 4, GL11.GL_COLOR_ARRAY_TYPE, GL11.GL_UNSIGNED_BYTE, GL11.GL_COLOR_ARRAY_STRIDE, 16,
                GL15.GL_COLOR_ARRAY_BUFFER_BINDING, vbo);
            assertClientArray("secondary color", GL14.GL_SECONDARY_COLOR_ARRAY_SIZE, 3, GL14.GL_SECONDARY_COLOR_ARRAY_TYPE, GL11.GL_FLOAT,
                GL14.GL_SECONDARY_COLOR_ARRAY_STRIDE, 12, GL15.GL_SECONDARY_COLOR_ARRAY_BUFFER_BINDING, vbo);
            assertClientArray("fog coord", 0, 0, GL14.GL_FOG_COORDINATE_ARRAY_TYPE, GL11.GL_FLOAT, GL14.GL_FOG_COORDINATE_ARRAY_STRIDE, 4,
                GL15.GL_FOG_COORDINATE_ARRAY_BUFFER_BINDING, vbo);
            assertClientArray("edge flag", 0, 0, 0, 0, GL11.GL_EDGE_FLAG_ARRAY_STRIDE, 7, GL15.GL_EDGE_FLAG_ARRAY_BUFFER_BINDING, vbo);
            assertTexCoordArray("texcoord unit 0", 2, GL11.GL_FLOAT, 8, vbo);

            assertEquals(3.0f, GLStateManager.glGetFloat(GL11.GL_VERTEX_ARRAY_SIZE));
            GLStateManager.glGetInteger(GL11.GL_VERTEX_ARRAY_STRIDE, ints);
            assertEquals(20, ints.get(0));
            assertEquals(8L, MemoryUtilities.memAddress(GLStateManager.glGetPointer(GL11.GL_VERTEX_ARRAY_POINTER, 4)));
            assertEquals(12L, MemoryUtilities.memAddress(GLStateManager.glGetPointer(GL11.GL_COLOR_ARRAY_POINTER, 4)));

            GLStateManager.glClientActiveTexture(GL13.GL_TEXTURE0 + GLStateManager.MAX_TEXTURE_COORDS - 1);
            assertTexCoordArray("unset texcoord unit", 4, GL11.GL_FLOAT, 0, 0);
            GLStateManager.glTexCoordPointer(3, GL11.GL_SHORT, 6, 0L);
            assertTexCoordArray("last texcoord unit", 3, GL11.GL_SHORT, 6, vbo);
            assertFalse(attribHasSize3(Usage.PRIMARY_UV));
            assertFalse(attribHasSize3(Usage.SECONDARY_UV));
            GLStateManager.glClientActiveTexture(GL13.GL_TEXTURE0);
            assertTexCoordArray("texcoord unit 0 again", 2, GL11.GL_FLOAT, 8, vbo);

            GLStateManager.glBindVertexArray(vaoB);
            assertVertexArray("other vao", 4, GL11.GL_FLOAT, 0, 0);
            final FloatBuffer client = BufferUtils.createFloatBuffer(12);
            GLStateManager.glVertexPointer(2, 0, client);
            assertVertexArray("client pointer", 2, GL11.GL_FLOAT, 0, 0);
            assertEquals(MemoryUtilities.memAddress(client), MemoryUtilities.memAddress(GLStateManager.glGetPointer(GL11.GL_VERTEX_ARRAY_POINTER, 4)));
            GLStateManager.glBindVertexArray(vao);
            assertVertexArray("first vao again", 3, GL11.GL_SHORT, 20, vbo);
        } finally {
            GLStateManager.glClientActiveTexture(GL13.GL_TEXTURE0);
            GLStateManager.glBindBuffer(GL15.GL_ARRAY_BUFFER, 0);
            GLStateManager.glDeleteVertexArrays(vaoB);
        }
    }

    @Test
    void interleavedArraysSetClientStateWithoutAnActiveFfpProgram() {
        try {
            GLStateManager.glBindBuffer(GL15.GL_ARRAY_BUFFER, vbo);
            GLStateManager.glClientActiveTexture(GL13.GL_TEXTURE0);
            assumeFalse(GLStateManager.ctx().ffp.isActive());

            GLStateManager.glInterleavedArrays(GL11.GL_C4UB_V3F, 0, 0L);
            assertTrue(GLStateManager.glIsEnabled(GL11.GL_VERTEX_ARRAY));
            assertTrue(GLStateManager.glIsEnabled(GL11.GL_COLOR_ARRAY));
            assertFalse(GLStateManager.glIsEnabled(GL11.GL_NORMAL_ARRAY));
            assertVertexArray("interleaved vertex", 3, GL11.GL_FLOAT, 16, vbo);
            assertEquals(GL11.GL_UNSIGNED_BYTE, GLStateManager.glGetInteger(GL11.GL_COLOR_ARRAY_TYPE));
        } finally {
            for (final int cap : CLIENT_ARRAY_CAPS) {
                GLStateManager.glDisableClientState(cap);
            }
            GLStateManager.glBindBuffer(GL15.GL_ARRAY_BUFFER, 0);
        }
    }

    @Test
    void clientVertexArrayBitRestoresPointersEnablesAndBindings() {
        final int vaoB = GLStateManager.glGenVertexArrays();
        final int vbo2 = GLStateManager.glGenBuffers();
        final int ebo = GLStateManager.glGenBuffers();
        final int position = Usage.POSITION.getAttributeLocation();
        final int color = Usage.COLOR.getAttributeLocation();
        final int normal = Usage.NORMAL.getAttributeLocation();
        try {
            GLStateManager.glBindBuffer(GL15.GL_ARRAY_BUFFER, vbo);
            GLStateManager.glBindBuffer(GL15.GL_ELEMENT_ARRAY_BUFFER, ebo);
            GLStateManager.glClientActiveTexture(GL13.GL_TEXTURE0);
            GLStateManager.glVertexPointer(3, GL11.GL_FLOAT, 12, 0L);
            GLStateManager.glColorPointer(4, GL11.GL_UNSIGNED_BYTE, 16, 12L);
            GLStateManager.glFogCoordPointer(GL11.GL_FLOAT, 4, 0L);
            GLStateManager.glEnableClientState(GL11.GL_VERTEX_ARRAY);
            GLStateManager.glEnableClientState(GL11.GL_COLOR_ARRAY);

            GLStateManager.glPushClientAttrib(GL11.GL_CLIENT_VERTEX_ARRAY_BIT);
            GLStateManager.glBindBuffer(GL15.GL_ARRAY_BUFFER, vbo2);
            GLStateManager.glVertexPointer(2, GL11.GL_SHORT, 4, 4L);
            GLStateManager.glFogCoordPointer(GL11.GL_FLOAT, 8, 0L);
            GLStateManager.glDisableClientState(GL11.GL_COLOR_ARRAY);
            GLStateManager.glEnableClientState(GL11.GL_NORMAL_ARRAY);
            GLStateManager.glVertexAttribPointer(5, 1, GL11.GL_FLOAT, false, 4, 0L);
            GLStateManager.glEnableVertexAttribArray(5);
            GLStateManager.glBindBuffer(GL15.GL_ELEMENT_ARRAY_BUFFER, 0);
            GLStateManager.glClientActiveTexture(GL13.GL_TEXTURE1);
            GLStateManager.glBindVertexArray(vaoB);
            GLStateManager.glPopClientAttrib();

            assertEquals(vao, GLStateManager.getBoundVAO());
            assertEquals(vbo, GLStateManager.glGetInteger(GL15.GL_ARRAY_BUFFER_BINDING));
            assertEquals(ebo, GLStateManager.glGetInteger(GL15.GL_ELEMENT_ARRAY_BUFFER_BINDING));
            assertEquals(ebo, GL11.glGetInteger(GL15.GL_ELEMENT_ARRAY_BUFFER_BINDING));
            assertEquals(GL13.GL_TEXTURE0, GLStateManager.glGetInteger(GL13.GL_CLIENT_ACTIVE_TEXTURE));
            assertVertexArray("restored vertex", 3, GL11.GL_FLOAT, 12, vbo);
            assertEquals(4, GLStateManager.glGetInteger(GL14.GL_FOG_COORDINATE_ARRAY_STRIDE));
            assertEquals(vbo, GLStateManager.glGetInteger(GL15.GL_FOG_COORDINATE_ARRAY_BUFFER_BINDING));
            assertTrue(GLStateManager.glIsEnabled(GL11.GL_COLOR_ARRAY));
            assertFalse(GLStateManager.glIsEnabled(GL11.GL_NORMAL_ARRAY));
            assertTrue(colorArrayEnabled());

            assertEquals(3, driverAttrib(position, GL20.GL_VERTEX_ATTRIB_ARRAY_SIZE));
            assertEquals(GL11.GL_FLOAT, driverAttrib(position, GL20.GL_VERTEX_ATTRIB_ARRAY_TYPE));
            assertEquals(12, driverAttrib(position, GL20.GL_VERTEX_ATTRIB_ARRAY_STRIDE));
            assertEquals(vbo, driverAttrib(position, GL15.GL_VERTEX_ATTRIB_ARRAY_BUFFER_BINDING));
            assertEquals(GL11.GL_TRUE, driverAttrib(position, GL20.GL_VERTEX_ATTRIB_ARRAY_ENABLED));
            assertEquals(GL11.GL_TRUE, driverAttrib(color, GL20.GL_VERTEX_ATTRIB_ARRAY_ENABLED));
            assertEquals(GL11.GL_FALSE, driverAttrib(normal, GL20.GL_VERTEX_ATTRIB_ARRAY_ENABLED));
            assertEquals(GL11.GL_FALSE, driverAttrib(5, GL20.GL_VERTEX_ATTRIB_ARRAY_ENABLED));

            GLStateManager.glBindVertexArray(vaoB);
            GLStateManager.glPushClientAttrib(GL11.GL_CLIENT_VERTEX_ARRAY_BIT);
            GLStateManager.glBindVertexArray(vao);
            GLStateManager.glDeleteVertexArrays(vaoB);
            GLStateManager.glPopClientAttrib();
            assertEquals(vao, GLStateManager.getBoundVAO(), "a deleted VAO must not be restored");
            assertVertexArray("after dead-vao pop", 3, GL11.GL_FLOAT, 12, vbo);
        } finally {
            drainClientAttribStack();
            GLStateManager.glBindVertexArray(vao);
            GLStateManager.glClientActiveTexture(GL13.GL_TEXTURE0);
            for (final int cap : CLIENT_ARRAY_CAPS) {
                GLStateManager.glDisableClientState(cap);
            }
            GLStateManager.glBindBuffer(GL15.GL_ARRAY_BUFFER, 0);
            GLStateManager.glDeleteVertexArrays(vaoB);
            GLStateManager.glDeleteBuffers(vbo2);
            GLStateManager.glDeleteBuffers(ebo);
        }
    }

    @Test
    void enabledGenericAttributeWinsWithoutAPointer() {
        final int position = Usage.POSITION.getAttributeLocation();
        try {
            GLStateManager.glBindBuffer(GL15.GL_ARRAY_BUFFER, vbo);
            GLStateManager.glVertexPointer(3, GL11.GL_FLOAT, 12, 0L);
            GLStateManager.glEnableClientState(GL11.GL_VERTEX_ARRAY);
            assertFalse(VAOManager.get(position).genericPointer);

            GLStateManager.glEnableVertexAttribArray(position);
            assertTrue(VAOManager.get(position).genericPointer, "generic 0 supersedes position on its enable bit alone");
            GLStateManager.glDisableVertexAttribArray(position);
            assertFalse(VAOManager.get(position).genericPointer);
            assertEquals(3, VAOManager.get(position).size);
        } finally {
            GLStateManager.glDisableVertexAttribArray(position);
            GLStateManager.glDisableClientState(GL11.GL_VERTEX_ARRAY);
            GLStateManager.glBindBuffer(GL15.GL_ARRAY_BUFFER, 0);
        }
    }

    @Test
    void deletedBufferIsDetachedFromTheBoundVertexArray() {
        GLStateManager.glBindBuffer(GL15.GL_ARRAY_BUFFER, vbo);
        GLStateManager.glVertexPointer(3, GL11.GL_FLOAT, 12, 4L);
        GLStateManager.glFogCoordPointer(GL11.GL_FLOAT, 4, 0L);
        assertEquals(vbo, GLStateManager.glGetInteger(GL15.GL_VERTEX_ARRAY_BUFFER_BINDING));

        GLStateManager.glDeleteBuffers(vbo);
        assertEquals(0, GLStateManager.glGetInteger(GL15.GL_VERTEX_ARRAY_BUFFER_BINDING));
        assertEquals(0, GLStateManager.glGetInteger(GL15.GL_FOG_COORDINATE_ARRAY_BUFFER_BINDING));
        assertEquals(12, GLStateManager.glGetInteger(GL11.GL_VERTEX_ARRAY_STRIDE), "offset and stride survive the detach");
        assertEquals(3, GLStateManager.glGetInteger(GL11.GL_VERTEX_ARRAY_SIZE));
    }

    @Test
    void shadowedPointerIsNotRepointedAtAReusedBufferName() {
        final int other = GLStateManager.glGenVertexArrays();
        final int conventional = GLStateManager.glGenBuffers();
        final int generic = GLStateManager.glGenBuffers();
        final int position = Usage.POSITION.getAttributeLocation();
        int reused = 0;
        try {
            GLStateManager.glBindBuffer(GL15.GL_ARRAY_BUFFER, conventional);
            GLStateManager.glVertexPointer(3, GL11.GL_FLOAT, 12, 0L);
            GLStateManager.glEnableClientState(GL11.GL_VERTEX_ARRAY);
            GLStateManager.glBindBuffer(GL15.GL_ARRAY_BUFFER, generic);
            GLStateManager.glVertexAttribPointer(position, 2, GL11.GL_FLOAT, false, 8, 0L);
            GLStateManager.glEnableVertexAttribArray(position);

            GLStateManager.glBindVertexArray(other);
            GLStateManager.glDeleteBuffers(conventional);
            reused = GLStateManager.glGenBuffers();
            GLStateManager.glBindBuffer(GL15.GL_ARRAY_BUFFER, reused);
            assumeTrue(reused == conventional, "driver did not reuse the buffer name");

            GLStateManager.glBindVertexArray(vao);
            GLStateManager.glDisableVertexAttribArray(position);
            assertEquals(generic, driverAttrib(position, GL15.GL_VERTEX_ATTRIB_ARRAY_BUFFER_BINDING), "a pointer set before the delete must not attach to the new buffer");
        } finally {
            GLStateManager.glBindVertexArray(vao);
            GLStateManager.glDisableVertexAttribArray(position);
            GLStateManager.glDisableClientState(GL11.GL_VERTEX_ARRAY);
            GLStateManager.glBindBuffer(GL15.GL_ARRAY_BUFFER, 0);
            GLStateManager.glDeleteVertexArrays(other);
            GLStateManager.glDeleteBuffers(generic);
            if (reused != 0) GLStateManager.glDeleteBuffers(reused);
        }
    }

    @Test
    void popKeepsCurrentArraysWhenTheSavedArrayBufferWasDeleted() {
        final int vbo2 = GLStateManager.glGenBuffers();
        try {
            GLStateManager.glBindBuffer(GL15.GL_ARRAY_BUFFER, vbo);
            GLStateManager.glClientActiveTexture(GL13.GL_TEXTURE0);
            GLStateManager.glVertexPointer(3, GL11.GL_FLOAT, 12, 0L);

            GLStateManager.glPushClientAttrib(GL11.GL_CLIENT_VERTEX_ARRAY_BIT);
            GLStateManager.glBindBuffer(GL15.GL_ARRAY_BUFFER, vbo2);
            GLStateManager.glVertexPointer(2, GL11.GL_SHORT, 4, 0L);
            GLStateManager.glClientActiveTexture(GL13.GL_TEXTURE1);
            GLStateManager.glDeleteBuffers(vbo);
            GLStateManager.glPopClientAttrib();

            assertEquals(2, GLStateManager.glGetInteger(GL11.GL_VERTEX_ARRAY_SIZE), "array state is not restored once its array buffer is gone");
            assertEquals(vbo2, GLStateManager.glGetInteger(GL15.GL_ARRAY_BUFFER_BINDING));
            assertEquals(vbo2, GL11.glGetInteger(GL15.GL_ARRAY_BUFFER_BINDING));
            assertEquals(GL13.GL_TEXTURE0, GLStateManager.glGetInteger(GL13.GL_CLIENT_ACTIVE_TEXTURE));
        } finally {
            drainClientAttribStack();
            GLStateManager.glClientActiveTexture(GL13.GL_TEXTURE0);
            GLStateManager.glBindBuffer(GL15.GL_ARRAY_BUFFER, 0);
            GLStateManager.glDeleteBuffers(vbo2);
        }
    }

    @Test
    void clientAttribStackIsNotCompiledIntoDisplayLists() {
        final int other = GLStateManager.glGenVertexArrays();
        final int list = GLStateManager.glGenLists(1);
        try {
            GLStateManager.glNewList(list, GL11.GL_COMPILE);
            GLStateManager.glPushClientAttrib(GL11.GL_CLIENT_VERTEX_ARRAY_BIT);
            GLStateManager.glPopClientAttrib();
            GLStateManager.glEndList();

            GLStateManager.glBindVertexArray(other);
            GLStateManager.glCallList(list);
            assertEquals(other, GLStateManager.getBoundVAO(), "replaying the list must not rebind the vertex array that was bound while compiling");
            assertEquals(other, GL11.glGetInteger(GL30.GL_VERTEX_ARRAY_BINDING), "driver vertex array unchanged by replay");
        } finally {
            drainClientAttribStack();
            GLStateManager.glDeleteLists(list, 1);
            GLStateManager.glDeleteVertexArrays(other);
        }
    }

    @Test
    void popRestoresDivisorAndStateWithoutAnAttributeLocation() {
        try {
            GLStateManager.glBindBuffer(GL15.GL_ARRAY_BUFFER, vbo);
            GLStateManager.glVertexAttribPointer(5, 1, GL11.GL_FLOAT, false, 4, 0L);

            GLStateManager.glPushClientAttrib(GL11.GL_CLIENT_VERTEX_ARRAY_BIT);
            GLStateManager.glPopClientAttrib();
            assertEquals(0, driverAttrib(5, GL33.GL_VERTEX_ATTRIB_ARRAY_DIVISOR));

            GLStateManager.glPushClientAttrib(GL11.GL_CLIENT_VERTEX_ARRAY_BIT);
            GLStateManager.glVertexAttribDivisor(5, 3);
            assertEquals(3, driverAttrib(5, GL33.GL_VERTEX_ATTRIB_ARRAY_DIVISOR));
            GLStateManager.glPopClientAttrib();
            assertEquals(0, driverAttrib(5, GL33.GL_VERTEX_ATTRIB_ARRAY_DIVISOR));

            GLStateManager.glPushClientAttrib(GL11.GL_CLIENT_VERTEX_ARRAY_BIT);
            GLStateManager.glEnableClientState(GL14.GL_FOG_COORDINATE_ARRAY);
            GLStateManager.glPopClientAttrib();
            assertFalse(GLStateManager.glIsEnabled(GL14.GL_FOG_COORDINATE_ARRAY));

            GLStateManager.glPushClientAttrib(GL11.GL_CLIENT_VERTEX_ARRAY_BIT);
            GLStateManager.glEnableVertexAttribArray(5);
            GLStateManager.glPopClientAttrib();
            assertEquals(GL11.GL_FALSE, driverAttrib(5, GL20.GL_VERTEX_ATTRIB_ARRAY_ENABLED));
        } finally {
            drainClientAttribStack();
            GLStateManager.glDisableVertexAttribArray(5);
            GLStateManager.glDisableClientState(GL14.GL_FOG_COORDINATE_ARRAY);
            GLStateManager.glBindBuffer(GL15.GL_ARRAY_BUFFER, 0);
        }
    }

    @Test
    void interleavedArraysFromAClientBufferRegisterClientPointers() {
        final FloatBuffer data = BufferUtils.createFloatBuffer(64);
        final int position = Usage.POSITION.getAttributeLocation();
        try {
            GLStateManager.glBindBuffer(GL15.GL_ARRAY_BUFFER, 0);
            data.position(2);

            GLStateManager.glInterleavedArrays(GL11.GL_C4UB_V3F, 0, data);
            final long base = MemoryUtilities.memAddress(data);
            assertNotNull(VAOManager.get(position).clientPointer);
            assertEquals(base + 4, MemoryUtilities.memAddress(VAOManager.get(position).clientPointer));
            assertEquals(base + 4, MemoryUtilities.memAddress(GLStateManager.glGetPointer(GL11.GL_VERTEX_ARRAY_POINTER, 4)));
            assertEquals(base, MemoryUtilities.memAddress(GLStateManager.glGetPointer(GL11.GL_COLOR_ARRAY_POINTER, 4)));
            assertEquals(0, GLStateManager.glGetInteger(GL15.GL_VERTEX_ARRAY_BUFFER_BINDING));
            assertEquals(16, GLStateManager.glGetInteger(GL11.GL_VERTEX_ARRAY_STRIDE));
            assertTrue(GLStateManager.ctx().vaos.hasAnyClientSideEnabledAttrib());
        } finally {
            for (final int cap : CLIENT_ARRAY_CAPS) {
                GLStateManager.glDisableClientState(cap);
            }
        }
    }

    @Test
    void enableAndDisableRouteClientArrayCapsToClientState() {
        for (final int cap : CLIENT_ARRAY_CAPS) {
            GLStateManager.glEnable(cap);
            assertTrue(GLStateManager.glIsEnabled(cap), hex(cap));
            GLStateManager.glDisable(cap);
            assertFalse(GLStateManager.glIsEnabled(cap), hex(cap));
        }
        GLStateManager.glEnable(GL11.GL_VERTEX_ARRAY);
        assertTrue(positionArrayEnabled());
        GLStateManager.glDisable(GL11.GL_VERTEX_ARRAY);
    }

    @Test
    void illegalPointerArgumentsAreIgnored() {
        try {
            GLStateManager.glBindBuffer(GL15.GL_ARRAY_BUFFER, vbo);
            GLStateManager.glVertexPointer(3, GL11.GL_FLOAT, 12, 0L);
            GLStateManager.glNormalPointer(GL11.GL_FLOAT, 12, 0L);

            GLStateManager.glVertexPointer(1, GL11.GL_FLOAT, 4, 0L);
            GLStateManager.glVertexPointer(3, GL11.GL_UNSIGNED_BYTE, 3, 0L);
            GLStateManager.glNormalPointer(GL11.GL_UNSIGNED_BYTE, 3, 0L);
            assertVertexArray("vertex after illegal calls", 3, GL11.GL_FLOAT, 12, vbo);
            assertEquals(GL11.GL_FLOAT, GLStateManager.glGetInteger(GL11.GL_NORMAL_ARRAY_TYPE));

            GLStateManager.glColorPointer(GL12.GL_BGRA, GL11.GL_UNSIGNED_BYTE, 4, 0L);
            assertEquals(GL12.GL_BGRA, GLStateManager.glGetInteger(GL11.GL_COLOR_ARRAY_SIZE));
            GLStateManager.glColorPointer(GL12.GL_BGRA, GL11.GL_FLOAT, 16, 0L);
            assertEquals(GL11.GL_UNSIGNED_BYTE, GLStateManager.glGetInteger(GL11.GL_COLOR_ARRAY_TYPE));

            GLStateManager.glVertexAttribPointer(VAOManager.MAX_ATTRIBS, 3, GL11.GL_FLOAT, false, 0, 0L);
            GLStateManager.glEnableVertexAttribArray(VAOManager.MAX_ATTRIBS);
            GLStateManager.glDisableVertexAttribArray(VAOManager.MAX_ATTRIBS);
            GLStateManager.glVertexAttribPointer(5, 5, GL11.GL_FLOAT, false, 0, 0L);
            assertNull(VAOManager.get(5));
        } finally {
            GLStateManager.glBindBuffer(GL15.GL_ARRAY_BUFFER, 0);
        }
    }

    @Test
    void clientArrayEnablesAreAnsweredFromCache() {
        final int vaoB = GLStateManager.glGenVertexArrays();
        try {
            GLStateManager.glClientActiveTexture(GL13.GL_TEXTURE0);
            for (final int cap : CLIENT_ARRAY_CAPS) {
                assertFalse(GLStateManager.glIsEnabled(cap), "default isEnabled " + hex(cap));
                assertEnableRoundTrip(cap, true);
            }

            GLStateManager.glEnableClientState(GL11.GL_TEXTURE_COORD_ARRAY);
            GLStateManager.glClientActiveTexture(GL13.GL_TEXTURE1);
            assertFalse(GLStateManager.glIsEnabled(GL11.GL_TEXTURE_COORD_ARRAY), "texcoord array enable is per client texture unit");
            GLStateManager.glClientActiveTexture(GL13.GL_TEXTURE0);
            assertTrue(GLStateManager.glIsEnabled(GL11.GL_TEXTURE_COORD_ARRAY));
            GLStateManager.glDisableClientState(GL11.GL_TEXTURE_COORD_ARRAY);

            GLStateManager.glEnableClientState(GL11.GL_INDEX_ARRAY);
            GLStateManager.glBindVertexArray(vaoB);
            assertFalse(GLStateManager.glIsEnabled(GL11.GL_INDEX_ARRAY), "array enables are per vertex array object");
            GLStateManager.glBindVertexArray(vao);
            assertTrue(GLStateManager.glIsEnabled(GL11.GL_INDEX_ARRAY));
            GLStateManager.glDisableClientState(GL11.GL_INDEX_ARRAY);

            GLStateManager.glBindVertexArray(vaoB);
            GLStateManager.glEnableVertexAttribArray(0);
            assertFalse(GLStateManager.glIsEnabled(GL11.GL_VERTEX_ARRAY), "an enabled generic attribute 0 is not GL_VERTEX_ARRAY");
            GLStateManager.glDisableVertexAttribArray(0);
            GLStateManager.glBindVertexArray(vao);
        } finally {
            GLStateManager.glBindVertexArray(vaoB);
            GLStateManager.glDisableVertexAttribArray(0);
            GLStateManager.glBindVertexArray(vao);
            GLStateManager.glClientActiveTexture(GL13.GL_TEXTURE0);
            for (final int cap : CLIENT_ARRAY_CAPS) {
                GLStateManager.glDisableClientState(cap);
            }
            GLStateManager.glDeleteVertexArrays(vaoB);
        }
    }
}
