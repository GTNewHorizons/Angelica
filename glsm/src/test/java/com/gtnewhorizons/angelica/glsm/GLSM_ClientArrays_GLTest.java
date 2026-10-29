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
        savedVao = GLSMVertexInput.getBoundVAO();
        vao = GLSMVertexInput.glGenVertexArrays();
        vbo = GLSMBuffers.glGenBuffers();
        GLSMVertexInput.glBindVertexArray(vao);
    }

    @AfterEach
    void deleteVertexArray() {
        GLSMVertexInput.glBindVertexArray(savedVao);
        GLSMVertexInput.glDeleteVertexArrays(vao);
        GLSMBuffers.glDeleteBuffers(vbo);
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
        if (sizePname != 0) assertEquals(size, GLSMQueries.glGetInteger(sizePname), name + " size");
        if (typePname != 0) assertEquals(type, GLSMQueries.glGetInteger(typePname), name + " type");
        assertEquals(stride, GLSMQueries.glGetInteger(stridePname), name + " stride");
        assertEquals(binding, GLSMQueries.glGetInteger(bindingPname), name + " buffer binding");
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
            GLSMTextures.glClientActiveTexture(GL13.GL_TEXTURE0);
            final int flagsBefore = GLStateManager.ctx().vaos.getVertexFlags();
            assertFalse(positionArrayEnabled());
            assertFalse(colorArrayEnabled());

            GLSMAttribStack.glPushClientAttrib(GL11.GL_CLIENT_VERTEX_ARRAY_BIT);
            GLSMVertexInput.glEnableClientState(GL11.GL_VERTEX_ARRAY);
            GLSMVertexInput.glEnableClientState(GL11.GL_COLOR_ARRAY);
            GLSMTextures.glClientActiveTexture(GL13.GL_TEXTURE1);
            assertTrue(positionArrayEnabled());
            assertTrue(colorArrayEnabled());
            GLSMAttribStack.glPopClientAttrib();
            assertFalse(positionArrayEnabled(), "pop disables an array enabled inside the push");
            assertEquals(GL11.GL_FALSE, driverAttrib(position, GL20.GL_VERTEX_ATTRIB_ARRAY_ENABLED));
            assertFalse(colorArrayEnabled());
            assertEquals(flagsBefore, GLStateManager.ctx().vaos.getVertexFlags());
            assertEquals(0, GLSMTextures.getClientActiveTextureUnit());

            GLSMVertexInput.glEnableClientState(GL11.GL_VERTEX_ARRAY);
            GLSMAttribStack.glPushClientAttrib(GL11.GL_CLIENT_VERTEX_ARRAY_BIT);
            GLSMVertexInput.glDisableClientState(GL11.GL_VERTEX_ARRAY);
            GLSMAttribStack.glPopClientAttrib();
            assertTrue(positionArrayEnabled(), "pop re-enables an array disabled inside the push");
            assertEquals(GL11.GL_TRUE, driverAttrib(position, GL20.GL_VERTEX_ATTRIB_ARRAY_ENABLED));
            GLSMVertexInput.glDisableClientState(GL11.GL_VERTEX_ARRAY);

            GLSMAttribStack.glPushClientAttrib(GL11.GL_CLIENT_PIXEL_STORE_BIT);
            GLSMVertexInput.glEnableClientState(GL11.GL_VERTEX_ARRAY);
            GLSMVertexInput.glEnableClientState(GL11.GL_COLOR_ARRAY);
            GLSMTextures.glClientActiveTexture(GL13.GL_TEXTURE1);
            GLSMAttribStack.glPopClientAttrib();
            assertTrue(positionArrayEnabled(), "a pixel-store push must not restore array enables");
            assertTrue(colorArrayEnabled());
            assertEquals(1, GLSMTextures.getClientActiveTextureUnit());
        } finally {
            drainClientAttribStack();
            GLSMTextures.glClientActiveTexture(GL13.GL_TEXTURE0);
            GLSMVertexInput.glDisableClientState(GL11.GL_VERTEX_ARRAY);
            GLSMVertexInput.glDisableClientState(GL11.GL_COLOR_ARRAY);
        }
    }

    @Test
    void clientActiveTextureRoundTrips() {
        try {
            GLSMBuffers.glBindBuffer(GL15.GL_ARRAY_BUFFER, vbo);
            assertEquals(GLStateManager.MAX_TEXTURE_COORDS, GLSMQueries.glGetInteger(GL20.GL_MAX_TEXTURE_COORDS));

            GLSMTextures.glClientActiveTexture(GL13.GL_TEXTURE1);
            assertEquals(GL13.GL_TEXTURE1, GLSMQueries.glGetInteger(GL13.GL_CLIENT_ACTIVE_TEXTURE));
            GLSMTextures.glClientActiveTexture(0);
            assertEquals(GL13.GL_TEXTURE1, GLSMQueries.glGetInteger(GL13.GL_CLIENT_ACTIVE_TEXTURE));
            GLSMTextures.glClientActiveTexture(GL13.GL_TEXTURE0 + GLStateManager.MAX_TEXTURE_UNITS);
            assertEquals(GL13.GL_TEXTURE1, GLSMQueries.glGetInteger(GL13.GL_CLIENT_ACTIVE_TEXTURE));
            GLSMTextures.glClientActiveTexture(GL13.GL_TEXTURE0 + GLStateManager.MAX_TEXTURE_COORDS);
            assertEquals(GL13.GL_TEXTURE1, GLSMQueries.glGetInteger(GL13.GL_CLIENT_ACTIVE_TEXTURE), "units past GL_MAX_TEXTURE_COORDS are rejected");
            assertEquals(1, GLSMTextures.getClientActiveTextureUnit());

            GLSMVertexInput.glTexCoordPointer(2, GL11.GL_FLOAT, 0, 0L);
            final VAOManager.Attrib attrib = GLStateManager.ctx().vaos.attrib(Usage.SECONDARY_UV.getAttributeLocation());
            assertNotNull(attrib);
            assertEquals(2, attrib.size);

            assumeTrue(GLStateManager.MAX_TEXTURE_COORDS > 5);
            GLSMTextures.glClientActiveTexture(GL13.GL_TEXTURE5);
            assertEquals(GL13.GL_TEXTURE5, GLSMQueries.glGetInteger(GL13.GL_CLIENT_ACTIVE_TEXTURE));
            GLSMVertexInput.glTexCoordPointer(3, GL11.GL_FLOAT, 0, 0L);
            assertEquals(3, GLSMQueries.glGetInteger(GL11.GL_TEXTURE_COORD_ARRAY_SIZE));
            assertFalse(attribHasSize3(Usage.PRIMARY_UV));
            assertFalse(attribHasSize3(Usage.SECONDARY_UV));
        } finally {
            GLSMTextures.glClientActiveTexture(GL13.GL_TEXTURE0);
            GLSMBuffers.glBindBuffer(GL15.GL_ARRAY_BUFFER, 0);
        }
    }

    @Test
    void conventionalPointerSurvivesGenericOverride() {
        final int position = Usage.POSITION.getAttributeLocation();
        try {
            GLSMBuffers.glBindBuffer(GL15.GL_ARRAY_BUFFER, vbo);

            GLSMVertexInput.glVertexPointer(3, GL11.GL_FLOAT, 12, 0L);
            GLSMVertexInput.glEnableClientState(GL11.GL_VERTEX_ARRAY);
            GLSMVertexInput.glVertexAttribPointer(position, 2, GL11.GL_FLOAT, false, 8, 4L);
            assertEquals(3, VAOManager.get(position).size, "disabled generic pointer must not displace an enabled conventional array");
            assertEquals(3, driverAttrib(position, GL20.GL_VERTEX_ATTRIB_ARRAY_SIZE));

            GLSMVertexInput.glEnableVertexAttribArray(position);
            assertEquals(2, VAOManager.get(position).size, "enabled generic attribute wins");
            assertTrue(VAOManager.get(position).genericPointer);
            assertEquals(2, driverAttrib(position, GL20.GL_VERTEX_ATTRIB_ARRAY_SIZE));
            assertEquals(8, driverAttrib(position, GL20.GL_VERTEX_ATTRIB_ARRAY_STRIDE));

            GLSMBuffers.glBindBuffer(GL15.GL_ARRAY_BUFFER, 0);
            GLSMVertexInput.glDisableVertexAttribArray(position);
            assertEquals(3, VAOManager.get(position).size, "conventional pointer must come back when the generic attribute is disabled");
            assertEquals(12, VAOManager.get(position).stride);
            assertFalse(VAOManager.get(position).genericPointer);
            assertEquals(3, driverAttrib(position, GL20.GL_VERTEX_ATTRIB_ARRAY_SIZE));
            assertEquals(12, driverAttrib(position, GL20.GL_VERTEX_ATTRIB_ARRAY_STRIDE));
            assertEquals(vbo, driverAttrib(position, GL15.GL_VERTEX_ATTRIB_ARRAY_BUFFER_BINDING));
            assertEquals(GL11.GL_TRUE, driverAttrib(position, GL20.GL_VERTEX_ATTRIB_ARRAY_ENABLED), "conventional array stays enabled at draw level");
            assertTrue(GLSMEnable.glIsEnabled(GL11.GL_VERTEX_ARRAY));
            assertEquals(0, GLSMQueries.glGetInteger(GL15.GL_ARRAY_BUFFER_BINDING), "repoint must restore the array buffer binding");
        } finally {
            GLSMVertexInput.glDisableVertexAttribArray(position);
            GLSMVertexInput.glDisableClientState(GL11.GL_VERTEX_ARRAY);
            GLSMBuffers.glBindBuffer(GL15.GL_ARRAY_BUFFER, 0);
        }
    }

    @Test
    void conventionalPointerSetUnderEnabledGenericAppliesOnceGenericIsDisabled() {
        final int position = Usage.POSITION.getAttributeLocation();
        try {
            GLSMBuffers.glBindBuffer(GL15.GL_ARRAY_BUFFER, vbo);

            GLSMVertexInput.glVertexAttribPointer(position, 2, GL11.GL_FLOAT, false, 8, 0L);
            GLSMVertexInput.glEnableVertexAttribArray(position);
            GLSMVertexInput.glVertexPointer(3, GL11.GL_FLOAT, 12, 0L);
            GLSMVertexInput.glEnableClientState(GL11.GL_VERTEX_ARRAY);
            assertEquals(2, VAOManager.get(position).size);
            assertEquals(2, driverAttrib(position, GL20.GL_VERTEX_ATTRIB_ARRAY_SIZE));

            GLSMVertexInput.glDisableVertexAttribArray(position);
            assertEquals(3, VAOManager.get(position).size);
            assertEquals(3, driverAttrib(position, GL20.GL_VERTEX_ATTRIB_ARRAY_SIZE));
            assertEquals(GL11.GL_TRUE, driverAttrib(position, GL20.GL_VERTEX_ATTRIB_ARRAY_ENABLED));

            GLSMVertexInput.glDisableClientState(GL11.GL_VERTEX_ARRAY);
            assertEquals(GL11.GL_FALSE, driverAttrib(position, GL20.GL_VERTEX_ATTRIB_ARRAY_ENABLED));
            assertFalse(positionArrayEnabled());
        } finally {
            GLSMVertexInput.glDisableVertexAttribArray(position);
            GLSMVertexInput.glDisableClientState(GL11.GL_VERTEX_ARRAY);
            GLSMBuffers.glBindBuffer(GL15.GL_ARRAY_BUFFER, 0);
        }
    }

    @Test
    void drawVertexFlagsDoNotAnswerClientArrayEnables() {
        try {
            GLSMTextures.glClientActiveTexture(GL13.GL_TEXTURE0);
            VAOManager.setCurrentVertexFlags(VertexFlags.COLOR_BIT | VertexFlags.NORMAL_BIT | VertexFlags.TEXTURE_BIT);
            assertFalse(GLSMEnable.glIsEnabled(GL11.GL_COLOR_ARRAY));
            assertFalse(GLSMEnable.glIsEnabled(GL11.GL_NORMAL_ARRAY));
            assertFalse(GLSMEnable.glIsEnabled(GL11.GL_TEXTURE_COORD_ARRAY));

            GLSMVertexInput.glEnableClientState(GL11.GL_COLOR_ARRAY);
            VAOManager.setCurrentVertexFlags(0);
            assertTrue(GLSMEnable.glIsEnabled(GL11.GL_COLOR_ARRAY));
        } finally {
            GLSMVertexInput.glDisableClientState(GL11.GL_COLOR_ARRAY);
            VAOManager.setCurrentVertexFlags(0);
        }
    }

    @Test
    void clientArrayPointerQueriesAreAnsweredFromCache() {
        final int vaoB = GLSMVertexInput.glGenVertexArrays();
        final IntBuffer ints = BufferUtils.createIntBuffer(16);
        try {
            GLSMBuffers.glBindBuffer(GL15.GL_ARRAY_BUFFER, vbo);
            GLSMTextures.glClientActiveTexture(GL13.GL_TEXTURE0);

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
            assertNull(GLSMVertexInput.glGetPointer(GL11.GL_VERTEX_ARRAY_POINTER, 4));

            GLSMVertexInput.glVertexPointer(3, GL11.GL_SHORT, 20, 8L);
            GLSMVertexInput.glNormalPointer(GL11.GL_BYTE, 16, 4L);
            GLSMVertexInput.glColorPointer(4, GL11.GL_UNSIGNED_BYTE, 16, 12L);
            GLSMVertexInput.glSecondaryColorPointer(3, GL11.GL_FLOAT, 12, 0L);
            GLSMVertexInput.glFogCoordPointer(GL11.GL_FLOAT, 4, 0L);
            GLSMVertexInput.glEdgeFlagPointer(7, 0L);
            GLSMVertexInput.glTexCoordPointer(2, GL11.GL_FLOAT, 8, 0L);

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

            assertEquals(3.0f, GLSMQueries.glGetFloat(GL11.GL_VERTEX_ARRAY_SIZE));
            GLSMQueries.glGetInteger(GL11.GL_VERTEX_ARRAY_STRIDE, ints);
            assertEquals(20, ints.get(0));
            assertEquals(8L, MemoryUtilities.memAddress(GLSMVertexInput.glGetPointer(GL11.GL_VERTEX_ARRAY_POINTER, 4)));
            assertEquals(12L, MemoryUtilities.memAddress(GLSMVertexInput.glGetPointer(GL11.GL_COLOR_ARRAY_POINTER, 4)));

            GLSMTextures.glClientActiveTexture(GL13.GL_TEXTURE0 + GLStateManager.MAX_TEXTURE_COORDS - 1);
            assertTexCoordArray("unset texcoord unit", 4, GL11.GL_FLOAT, 0, 0);
            GLSMVertexInput.glTexCoordPointer(3, GL11.GL_SHORT, 6, 0L);
            assertTexCoordArray("last texcoord unit", 3, GL11.GL_SHORT, 6, vbo);
            assertFalse(attribHasSize3(Usage.PRIMARY_UV));
            assertFalse(attribHasSize3(Usage.SECONDARY_UV));
            GLSMTextures.glClientActiveTexture(GL13.GL_TEXTURE0);
            assertTexCoordArray("texcoord unit 0 again", 2, GL11.GL_FLOAT, 8, vbo);

            GLSMVertexInput.glBindVertexArray(vaoB);
            assertVertexArray("other vao", 4, GL11.GL_FLOAT, 0, 0);
            final FloatBuffer client = BufferUtils.createFloatBuffer(12);
            GLSMVertexInput.glVertexPointer(2, 0, client);
            assertVertexArray("client pointer", 2, GL11.GL_FLOAT, 0, 0);
            assertEquals(MemoryUtilities.memAddress(client), MemoryUtilities.memAddress(GLSMVertexInput.glGetPointer(GL11.GL_VERTEX_ARRAY_POINTER, 4)));
            GLSMVertexInput.glBindVertexArray(vao);
            assertVertexArray("first vao again", 3, GL11.GL_SHORT, 20, vbo);
        } finally {
            GLSMTextures.glClientActiveTexture(GL13.GL_TEXTURE0);
            GLSMBuffers.glBindBuffer(GL15.GL_ARRAY_BUFFER, 0);
            GLSMVertexInput.glDeleteVertexArrays(vaoB);
        }
    }

    @Test
    void interleavedArraysSetClientStateWithoutAnActiveFfpProgram() {
        try {
            GLSMBuffers.glBindBuffer(GL15.GL_ARRAY_BUFFER, vbo);
            GLSMTextures.glClientActiveTexture(GL13.GL_TEXTURE0);
            assumeFalse(GLStateManager.ctx().ffp.isActive());

            GLSMVertexInput.glInterleavedArrays(GL11.GL_C4UB_V3F, 0, 0L);
            assertTrue(GLSMEnable.glIsEnabled(GL11.GL_VERTEX_ARRAY));
            assertTrue(GLSMEnable.glIsEnabled(GL11.GL_COLOR_ARRAY));
            assertFalse(GLSMEnable.glIsEnabled(GL11.GL_NORMAL_ARRAY));
            assertVertexArray("interleaved vertex", 3, GL11.GL_FLOAT, 16, vbo);
            assertEquals(GL11.GL_UNSIGNED_BYTE, GLSMQueries.glGetInteger(GL11.GL_COLOR_ARRAY_TYPE));
        } finally {
            for (final int cap : CLIENT_ARRAY_CAPS) {
                GLSMVertexInput.glDisableClientState(cap);
            }
            GLSMBuffers.glBindBuffer(GL15.GL_ARRAY_BUFFER, 0);
        }
    }

    @Test
    void clientVertexArrayBitRestoresPointersEnablesAndBindings() {
        final int vaoB = GLSMVertexInput.glGenVertexArrays();
        final int vbo2 = GLSMBuffers.glGenBuffers();
        final int ebo = GLSMBuffers.glGenBuffers();
        final int position = Usage.POSITION.getAttributeLocation();
        final int color = Usage.COLOR.getAttributeLocation();
        final int normal = Usage.NORMAL.getAttributeLocation();
        try {
            GLSMBuffers.glBindBuffer(GL15.GL_ARRAY_BUFFER, vbo);
            GLSMBuffers.glBindBuffer(GL15.GL_ELEMENT_ARRAY_BUFFER, ebo);
            GLSMTextures.glClientActiveTexture(GL13.GL_TEXTURE0);
            GLSMVertexInput.glVertexPointer(3, GL11.GL_FLOAT, 12, 0L);
            GLSMVertexInput.glColorPointer(4, GL11.GL_UNSIGNED_BYTE, 16, 12L);
            GLSMVertexInput.glFogCoordPointer(GL11.GL_FLOAT, 4, 0L);
            GLSMVertexInput.glEnableClientState(GL11.GL_VERTEX_ARRAY);
            GLSMVertexInput.glEnableClientState(GL11.GL_COLOR_ARRAY);

            GLSMAttribStack.glPushClientAttrib(GL11.GL_CLIENT_VERTEX_ARRAY_BIT);
            GLSMBuffers.glBindBuffer(GL15.GL_ARRAY_BUFFER, vbo2);
            GLSMVertexInput.glVertexPointer(2, GL11.GL_SHORT, 4, 4L);
            GLSMVertexInput.glFogCoordPointer(GL11.GL_FLOAT, 8, 0L);
            GLSMVertexInput.glDisableClientState(GL11.GL_COLOR_ARRAY);
            GLSMVertexInput.glEnableClientState(GL11.GL_NORMAL_ARRAY);
            GLSMVertexInput.glVertexAttribPointer(5, 1, GL11.GL_FLOAT, false, 4, 0L);
            GLSMVertexInput.glEnableVertexAttribArray(5);
            GLSMBuffers.glBindBuffer(GL15.GL_ELEMENT_ARRAY_BUFFER, 0);
            GLSMTextures.glClientActiveTexture(GL13.GL_TEXTURE1);
            GLSMVertexInput.glBindVertexArray(vaoB);
            GLSMAttribStack.glPopClientAttrib();

            assertEquals(vao, GLSMVertexInput.getBoundVAO());
            assertEquals(vbo, GLSMQueries.glGetInteger(GL15.GL_ARRAY_BUFFER_BINDING));
            assertEquals(ebo, GLSMQueries.glGetInteger(GL15.GL_ELEMENT_ARRAY_BUFFER_BINDING));
            assertEquals(ebo, GL11.glGetInteger(GL15.GL_ELEMENT_ARRAY_BUFFER_BINDING));
            assertEquals(GL13.GL_TEXTURE0, GLSMQueries.glGetInteger(GL13.GL_CLIENT_ACTIVE_TEXTURE));
            assertVertexArray("restored vertex", 3, GL11.GL_FLOAT, 12, vbo);
            assertEquals(4, GLSMQueries.glGetInteger(GL14.GL_FOG_COORDINATE_ARRAY_STRIDE));
            assertEquals(vbo, GLSMQueries.glGetInteger(GL15.GL_FOG_COORDINATE_ARRAY_BUFFER_BINDING));
            assertTrue(GLSMEnable.glIsEnabled(GL11.GL_COLOR_ARRAY));
            assertFalse(GLSMEnable.glIsEnabled(GL11.GL_NORMAL_ARRAY));
            assertTrue(colorArrayEnabled());

            assertEquals(3, driverAttrib(position, GL20.GL_VERTEX_ATTRIB_ARRAY_SIZE));
            assertEquals(GL11.GL_FLOAT, driverAttrib(position, GL20.GL_VERTEX_ATTRIB_ARRAY_TYPE));
            assertEquals(12, driverAttrib(position, GL20.GL_VERTEX_ATTRIB_ARRAY_STRIDE));
            assertEquals(vbo, driverAttrib(position, GL15.GL_VERTEX_ATTRIB_ARRAY_BUFFER_BINDING));
            assertEquals(GL11.GL_TRUE, driverAttrib(position, GL20.GL_VERTEX_ATTRIB_ARRAY_ENABLED));
            assertEquals(GL11.GL_TRUE, driverAttrib(color, GL20.GL_VERTEX_ATTRIB_ARRAY_ENABLED));
            assertEquals(GL11.GL_FALSE, driverAttrib(normal, GL20.GL_VERTEX_ATTRIB_ARRAY_ENABLED));
            assertEquals(GL11.GL_FALSE, driverAttrib(5, GL20.GL_VERTEX_ATTRIB_ARRAY_ENABLED));

            GLSMVertexInput.glBindVertexArray(vaoB);
            GLSMAttribStack.glPushClientAttrib(GL11.GL_CLIENT_VERTEX_ARRAY_BIT);
            GLSMVertexInput.glBindVertexArray(vao);
            GLSMVertexInput.glDeleteVertexArrays(vaoB);
            GLSMAttribStack.glPopClientAttrib();
            assertEquals(vao, GLSMVertexInput.getBoundVAO(), "a deleted VAO must not be restored");
            assertVertexArray("after dead-vao pop", 3, GL11.GL_FLOAT, 12, vbo);
        } finally {
            drainClientAttribStack();
            GLSMVertexInput.glBindVertexArray(vao);
            GLSMTextures.glClientActiveTexture(GL13.GL_TEXTURE0);
            for (final int cap : CLIENT_ARRAY_CAPS) {
                GLSMVertexInput.glDisableClientState(cap);
            }
            GLSMBuffers.glBindBuffer(GL15.GL_ARRAY_BUFFER, 0);
            GLSMVertexInput.glDeleteVertexArrays(vaoB);
            GLSMBuffers.glDeleteBuffers(vbo2);
            GLSMBuffers.glDeleteBuffers(ebo);
        }
    }

    @Test
    void enabledGenericAttributeWinsWithoutAPointer() {
        final int position = Usage.POSITION.getAttributeLocation();
        try {
            GLSMBuffers.glBindBuffer(GL15.GL_ARRAY_BUFFER, vbo);
            GLSMVertexInput.glVertexPointer(3, GL11.GL_FLOAT, 12, 0L);
            GLSMVertexInput.glEnableClientState(GL11.GL_VERTEX_ARRAY);
            assertFalse(VAOManager.get(position).genericPointer);

            GLSMVertexInput.glEnableVertexAttribArray(position);
            assertTrue(VAOManager.get(position).genericPointer, "generic 0 supersedes position on its enable bit alone");
            GLSMVertexInput.glDisableVertexAttribArray(position);
            assertFalse(VAOManager.get(position).genericPointer);
            assertEquals(3, VAOManager.get(position).size);
        } finally {
            GLSMVertexInput.glDisableVertexAttribArray(position);
            GLSMVertexInput.glDisableClientState(GL11.GL_VERTEX_ARRAY);
            GLSMBuffers.glBindBuffer(GL15.GL_ARRAY_BUFFER, 0);
        }
    }

    @Test
    void deletedBufferIsDetachedFromTheBoundVertexArray() {
        GLSMBuffers.glBindBuffer(GL15.GL_ARRAY_BUFFER, vbo);
        GLSMVertexInput.glVertexPointer(3, GL11.GL_FLOAT, 12, 4L);
        GLSMVertexInput.glFogCoordPointer(GL11.GL_FLOAT, 4, 0L);
        assertEquals(vbo, GLSMQueries.glGetInteger(GL15.GL_VERTEX_ARRAY_BUFFER_BINDING));

        GLSMBuffers.glDeleteBuffers(vbo);
        assertEquals(0, GLSMQueries.glGetInteger(GL15.GL_VERTEX_ARRAY_BUFFER_BINDING));
        assertEquals(0, GLSMQueries.glGetInteger(GL15.GL_FOG_COORDINATE_ARRAY_BUFFER_BINDING));
        assertEquals(12, GLSMQueries.glGetInteger(GL11.GL_VERTEX_ARRAY_STRIDE), "offset and stride survive the detach");
        assertEquals(3, GLSMQueries.glGetInteger(GL11.GL_VERTEX_ARRAY_SIZE));
    }

    @Test
    void shadowedPointerIsNotRepointedAtAReusedBufferName() {
        final int other = GLSMVertexInput.glGenVertexArrays();
        final int conventional = GLSMBuffers.glGenBuffers();
        final int generic = GLSMBuffers.glGenBuffers();
        final int position = Usage.POSITION.getAttributeLocation();
        int reused = 0;
        try {
            GLSMBuffers.glBindBuffer(GL15.GL_ARRAY_BUFFER, conventional);
            GLSMVertexInput.glVertexPointer(3, GL11.GL_FLOAT, 12, 0L);
            GLSMVertexInput.glEnableClientState(GL11.GL_VERTEX_ARRAY);
            GLSMBuffers.glBindBuffer(GL15.GL_ARRAY_BUFFER, generic);
            GLSMVertexInput.glVertexAttribPointer(position, 2, GL11.GL_FLOAT, false, 8, 0L);
            GLSMVertexInput.glEnableVertexAttribArray(position);

            GLSMVertexInput.glBindVertexArray(other);
            GLSMBuffers.glDeleteBuffers(conventional);
            reused = GLSMBuffers.glGenBuffers();
            GLSMBuffers.glBindBuffer(GL15.GL_ARRAY_BUFFER, reused);
            assumeTrue(reused == conventional, "driver did not reuse the buffer name");

            GLSMVertexInput.glBindVertexArray(vao);
            GLSMVertexInput.glDisableVertexAttribArray(position);
            assertEquals(generic, driverAttrib(position, GL15.GL_VERTEX_ATTRIB_ARRAY_BUFFER_BINDING), "a pointer set before the delete must not attach to the new buffer");
        } finally {
            GLSMVertexInput.glBindVertexArray(vao);
            GLSMVertexInput.glDisableVertexAttribArray(position);
            GLSMVertexInput.glDisableClientState(GL11.GL_VERTEX_ARRAY);
            GLSMBuffers.glBindBuffer(GL15.GL_ARRAY_BUFFER, 0);
            GLSMVertexInput.glDeleteVertexArrays(other);
            GLSMBuffers.glDeleteBuffers(generic);
            if (reused != 0) GLSMBuffers.glDeleteBuffers(reused);
        }
    }

    @Test
    void popKeepsCurrentStateWhenTheSavedBuffersWereDeleted() {
        final int vbo2 = GLSMBuffers.glGenBuffers();
        final int ebo = GLSMBuffers.glGenBuffers();
        final int ebo2 = GLSMBuffers.glGenBuffers();
        try {
            GLSMBuffers.glBindBuffer(GL15.GL_ARRAY_BUFFER, vbo);
            GLSMBuffers.glBindBuffer(GL15.GL_ELEMENT_ARRAY_BUFFER, ebo);
            GLSMTextures.glClientActiveTexture(GL13.GL_TEXTURE0);
            GLSMVertexInput.glVertexPointer(3, GL11.GL_FLOAT, 12, 0L);

            GLSMAttribStack.glPushClientAttrib(GL11.GL_CLIENT_VERTEX_ARRAY_BIT);
            GLSMBuffers.glBindBuffer(GL15.GL_ARRAY_BUFFER, vbo2);
            GLSMBuffers.glBindBuffer(GL15.GL_ELEMENT_ARRAY_BUFFER, ebo2);
            GLSMVertexInput.glVertexPointer(2, GL11.GL_SHORT, 4, 0L);
            GLSMTextures.glClientActiveTexture(GL13.GL_TEXTURE1);
            GLSMBuffers.glDeleteBuffers(vbo);
            GLSMBuffers.glDeleteBuffers(ebo);
            GLSMAttribStack.glPopClientAttrib();

            assertEquals(2, GLSMQueries.glGetInteger(GL11.GL_VERTEX_ARRAY_SIZE), "array state is not restored once its array buffer is gone");
            assertEquals(vbo2, GLSMQueries.glGetInteger(GL15.GL_ARRAY_BUFFER_BINDING));
            assertEquals(vbo2, GL11.glGetInteger(GL15.GL_ARRAY_BUFFER_BINDING));
            assertEquals(ebo2, GLSMQueries.glGetInteger(GL15.GL_ELEMENT_ARRAY_BUFFER_BINDING), "a deleted element buffer is not rebound by the pop");
            assertEquals(ebo2, GL11.glGetInteger(GL15.GL_ELEMENT_ARRAY_BUFFER_BINDING));
            assertEquals(GL13.GL_TEXTURE0, GLSMQueries.glGetInteger(GL13.GL_CLIENT_ACTIVE_TEXTURE));
        } finally {
            drainClientAttribStack();
            GLSMTextures.glClientActiveTexture(GL13.GL_TEXTURE0);
            GLSMBuffers.glBindBuffer(GL15.GL_ARRAY_BUFFER, 0);
            GLSMBuffers.glBindBuffer(GL15.GL_ELEMENT_ARRAY_BUFFER, 0);
            GLSMBuffers.glDeleteBuffers(vbo2);
            GLSMBuffers.glDeleteBuffers(ebo2);
            GLSMBuffers.glDeleteBuffers(ebo);
        }
    }

    @Test
    void clientAttribStackIsNotCompiledIntoDisplayLists() {
        final int other = GLSMVertexInput.glGenVertexArrays();
        final int list = GLSMDraws.glGenLists(1);
        try {
            GLSMDraws.glNewList(list, GL11.GL_COMPILE);
            GLSMAttribStack.glPushClientAttrib(GL11.GL_CLIENT_VERTEX_ARRAY_BIT);
            GLSMAttribStack.glPopClientAttrib();
            GLSMDraws.glEndList();

            GLSMVertexInput.glBindVertexArray(other);
            GLSMDraws.glCallList(list);
            assertEquals(other, GLSMVertexInput.getBoundVAO(), "replaying the list must not rebind the vertex array that was bound while compiling");
            assertEquals(other, GL11.glGetInteger(GL30.GL_VERTEX_ARRAY_BINDING), "driver vertex array unchanged by replay");
        } finally {
            drainClientAttribStack();
            GLSMDraws.glDeleteLists(list, 1);
            GLSMVertexInput.glDeleteVertexArrays(other);
        }
    }

    @Test
    void popRestoresDivisorAndStateWithoutAnAttributeLocation() {
        try {
            GLSMBuffers.glBindBuffer(GL15.GL_ARRAY_BUFFER, vbo);
            GLSMVertexInput.glVertexAttribPointer(5, 1, GL11.GL_FLOAT, false, 4, 0L);

            GLSMAttribStack.glPushClientAttrib(GL11.GL_CLIENT_VERTEX_ARRAY_BIT);
            GLSMAttribStack.glPopClientAttrib();
            assertEquals(0, driverAttrib(5, GL33.GL_VERTEX_ATTRIB_ARRAY_DIVISOR));

            GLSMAttribStack.glPushClientAttrib(GL11.GL_CLIENT_VERTEX_ARRAY_BIT);
            GLSMVertexInput.glVertexAttribDivisor(5, 3);
            assertEquals(3, driverAttrib(5, GL33.GL_VERTEX_ATTRIB_ARRAY_DIVISOR));
            GLSMAttribStack.glPopClientAttrib();
            assertEquals(0, driverAttrib(5, GL33.GL_VERTEX_ATTRIB_ARRAY_DIVISOR));

            GLSMAttribStack.glPushClientAttrib(GL11.GL_CLIENT_VERTEX_ARRAY_BIT);
            GLSMVertexInput.glEnableClientState(GL14.GL_FOG_COORDINATE_ARRAY);
            GLSMAttribStack.glPopClientAttrib();
            assertFalse(GLSMEnable.glIsEnabled(GL14.GL_FOG_COORDINATE_ARRAY));

            GLSMAttribStack.glPushClientAttrib(GL11.GL_CLIENT_VERTEX_ARRAY_BIT);
            GLSMVertexInput.glEnableVertexAttribArray(5);
            GLSMAttribStack.glPopClientAttrib();
            assertEquals(GL11.GL_FALSE, driverAttrib(5, GL20.GL_VERTEX_ATTRIB_ARRAY_ENABLED));
        } finally {
            drainClientAttribStack();
            GLSMVertexInput.glDisableVertexAttribArray(5);
            GLSMVertexInput.glDisableClientState(GL14.GL_FOG_COORDINATE_ARRAY);
            GLSMBuffers.glBindBuffer(GL15.GL_ARRAY_BUFFER, 0);
        }
    }

    @Test
    void interleavedArraysFromAClientBufferRegisterClientPointers() {
        final FloatBuffer data = BufferUtils.createFloatBuffer(64);
        final int position = Usage.POSITION.getAttributeLocation();
        try {
            GLSMBuffers.glBindBuffer(GL15.GL_ARRAY_BUFFER, 0);
            data.position(2);

            GLSMVertexInput.glInterleavedArrays(GL11.GL_C4UB_V3F, 0, data);
            final long base = MemoryUtilities.memAddress(data);
            assertNotNull(VAOManager.get(position).clientPointer);
            assertEquals(base + 4, MemoryUtilities.memAddress(VAOManager.get(position).clientPointer));
            assertEquals(base + 4, MemoryUtilities.memAddress(GLSMVertexInput.glGetPointer(GL11.GL_VERTEX_ARRAY_POINTER, 4)));
            assertEquals(base, MemoryUtilities.memAddress(GLSMVertexInput.glGetPointer(GL11.GL_COLOR_ARRAY_POINTER, 4)));
            assertEquals(0, GLSMQueries.glGetInteger(GL15.GL_VERTEX_ARRAY_BUFFER_BINDING));
            assertEquals(16, GLSMQueries.glGetInteger(GL11.GL_VERTEX_ARRAY_STRIDE));
            assertTrue(GLStateManager.ctx().vaos.hasAnyClientSideEnabledAttrib());
        } finally {
            for (final int cap : CLIENT_ARRAY_CAPS) {
                GLSMVertexInput.glDisableClientState(cap);
            }
        }
    }

    @Test
    void enableAndDisableRouteClientArrayCapsToClientState() {
        for (final int cap : CLIENT_ARRAY_CAPS) {
            GLSMEnable.glEnable(cap);
            assertTrue(GLSMEnable.glIsEnabled(cap), hex(cap));
            GLSMEnable.glDisable(cap);
            assertFalse(GLSMEnable.glIsEnabled(cap), hex(cap));
        }
        GLSMEnable.glEnable(GL11.GL_VERTEX_ARRAY);
        assertTrue(positionArrayEnabled());
        GLSMEnable.glDisable(GL11.GL_VERTEX_ARRAY);
    }

    @Test
    void illegalPointerArgumentsAreIgnored() {
        try {
            GLSMBuffers.glBindBuffer(GL15.GL_ARRAY_BUFFER, vbo);
            GLSMVertexInput.glVertexPointer(3, GL11.GL_FLOAT, 12, 0L);
            GLSMVertexInput.glNormalPointer(GL11.GL_FLOAT, 12, 0L);

            GLSMVertexInput.glVertexPointer(1, GL11.GL_FLOAT, 4, 0L);
            GLSMVertexInput.glVertexPointer(3, GL11.GL_UNSIGNED_BYTE, 3, 0L);
            GLSMVertexInput.glNormalPointer(GL11.GL_UNSIGNED_BYTE, 3, 0L);
            assertVertexArray("vertex after illegal calls", 3, GL11.GL_FLOAT, 12, vbo);
            assertEquals(GL11.GL_FLOAT, GLSMQueries.glGetInteger(GL11.GL_NORMAL_ARRAY_TYPE));

            GLSMVertexInput.glColorPointer(GL12.GL_BGRA, GL11.GL_UNSIGNED_BYTE, 4, 0L);
            assertEquals(GL12.GL_BGRA, GLSMQueries.glGetInteger(GL11.GL_COLOR_ARRAY_SIZE));
            GLSMVertexInput.glColorPointer(GL12.GL_BGRA, GL11.GL_FLOAT, 16, 0L);
            assertEquals(GL11.GL_UNSIGNED_BYTE, GLSMQueries.glGetInteger(GL11.GL_COLOR_ARRAY_TYPE));

            GLSMVertexInput.glVertexAttribPointer(VAOManager.MAX_ATTRIBS, 3, GL11.GL_FLOAT, false, 0, 0L);
            GLSMVertexInput.glEnableVertexAttribArray(VAOManager.MAX_ATTRIBS);
            GLSMVertexInput.glDisableVertexAttribArray(VAOManager.MAX_ATTRIBS);
            GLSMVertexInput.glVertexAttribPointer(5, 5, GL11.GL_FLOAT, false, 0, 0L);
            assertNull(VAOManager.get(5));
        } finally {
            GLSMBuffers.glBindBuffer(GL15.GL_ARRAY_BUFFER, 0);
        }
    }

    @Test
    void clientArrayEnablesAreAnsweredFromCache() {
        final int vaoB = GLSMVertexInput.glGenVertexArrays();
        try {
            GLSMTextures.glClientActiveTexture(GL13.GL_TEXTURE0);
            for (final int cap : CLIENT_ARRAY_CAPS) {
                assertFalse(GLSMEnable.glIsEnabled(cap), "default isEnabled " + hex(cap));
                assertEnableRoundTrip(cap, true);
            }

            GLSMVertexInput.glEnableClientState(GL11.GL_TEXTURE_COORD_ARRAY);
            GLSMTextures.glClientActiveTexture(GL13.GL_TEXTURE1);
            assertFalse(GLSMEnable.glIsEnabled(GL11.GL_TEXTURE_COORD_ARRAY), "texcoord array enable is per client texture unit");
            GLSMTextures.glClientActiveTexture(GL13.GL_TEXTURE0);
            assertTrue(GLSMEnable.glIsEnabled(GL11.GL_TEXTURE_COORD_ARRAY));
            GLSMVertexInput.glDisableClientState(GL11.GL_TEXTURE_COORD_ARRAY);

            GLSMVertexInput.glEnableClientState(GL11.GL_INDEX_ARRAY);
            GLSMVertexInput.glBindVertexArray(vaoB);
            assertFalse(GLSMEnable.glIsEnabled(GL11.GL_INDEX_ARRAY), "array enables are per vertex array object");
            GLSMVertexInput.glBindVertexArray(vao);
            assertTrue(GLSMEnable.glIsEnabled(GL11.GL_INDEX_ARRAY));
            GLSMVertexInput.glDisableClientState(GL11.GL_INDEX_ARRAY);

            GLSMVertexInput.glBindVertexArray(vaoB);
            GLSMVertexInput.glEnableVertexAttribArray(0);
            assertFalse(GLSMEnable.glIsEnabled(GL11.GL_VERTEX_ARRAY), "an enabled generic attribute 0 is not GL_VERTEX_ARRAY");
            GLSMVertexInput.glDisableVertexAttribArray(0);
            GLSMVertexInput.glBindVertexArray(vao);
        } finally {
            GLSMVertexInput.glBindVertexArray(vaoB);
            GLSMVertexInput.glDisableVertexAttribArray(0);
            GLSMVertexInput.glBindVertexArray(vao);
            GLSMTextures.glClientActiveTexture(GL13.GL_TEXTURE0);
            for (final int cap : CLIENT_ARRAY_CAPS) {
                GLSMVertexInput.glDisableClientState(cap);
            }
            GLSMVertexInput.glDeleteVertexArrays(vaoB);
        }
    }
}
