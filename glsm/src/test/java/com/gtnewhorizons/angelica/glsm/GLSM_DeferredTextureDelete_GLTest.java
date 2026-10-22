package com.gtnewhorizons.angelica.glsm;

import com.gtnewhorizons.angelica.glsm.testutil.Reflect;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL14;
import org.lwjgl.opengl.GL30;
import org.lwjgl.opengl.GL33;

import java.nio.ByteBuffer;
import java.nio.FloatBuffer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

@GLCoreTest
public class GLSM_DeferredTextureDelete_GLTest {

    private static final int RELEASE_DELAY_FRAMES = Reflect.getStatic(GLStateManager.class, "RELEASE_DELAY_FRAMES");
    private static final int SIZE = 4;
    private static final int LEVELS = 3;

    private static int immutableTexture() {
        assumeTrue(GLStateManager.capabilities.OpenGL42, "no glTexStorage2D on this context");
        final int tex = GLStateManager.glGenTextures();
        GLStateManager.glBindTexture(GL11.GL_TEXTURE_2D, tex);
        GLStateManager.glTexStorage2D(GL11.GL_TEXTURE_2D, 1, GL30.GL_R8, 4, 4);
        GLStateManager.glBindTexture(GL11.GL_TEXTURE_2D, 0);
        return tex;
    }

    private static int mipmappedTexture() {
        final int tex = GLStateManager.glGenTextures();
        GLStateManager.glBindTexture(GL11.GL_TEXTURE_2D, tex);
        for (int level = 0; level < LEVELS; level++) {
            GLStateManager.glTexImage2D(GL11.GL_TEXTURE_2D, level, GL11.GL_RGBA8, SIZE >> level, SIZE >> level, 0, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, (ByteBuffer) null);
        }
        GLStateManager.glBindTexture(GL11.GL_TEXTURE_2D, 0);
        return tex;
    }

    private static int levelWidth(int tex, int level) {
        final int previous = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, tex);
        final int width = GL11.glGetTexLevelParameteri(GL11.GL_TEXTURE_2D, level, GL11.GL_TEXTURE_WIDTH);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, previous);
        return width;
    }

    private static int cachedBinding(int unit) {
        return GLStateManager.getTextures().getTextureUnitBindings(unit).getBinding();
    }

    private static void framesPass(int frames) {
        for (int i = 0; i < frames; i++) GLStateManager.onFrameEnd();
    }

    enum Attachment {
        NONE {
            @Override int attach(int tex) { return 0; }
        },
        FRAMEBUFFER {
            @Override int attach(int tex) {
                final int fbo = GLStateManager.glGenFramebuffers();
                GLStateManager.glBindFramebuffer(GL30.GL_FRAMEBUFFER, fbo);
                GLStateManager.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER, GL30.GL_COLOR_ATTACHMENT0, GL11.GL_TEXTURE_2D, tex, 0);
                GLStateManager.glBindFramebuffer(GL30.GL_FRAMEBUFFER, 0);
                return fbo;
            }
        },
        NAMED_EXT {
            @Override int attach(int tex) {
                final int fbo = GLStateManager.glGenFramebuffers();
                GLStateManager.glNamedFramebufferTexture2DEXT(fbo, GL30.GL_COLOR_ATTACHMENT0, GL11.GL_TEXTURE_2D, tex, 0);
                assertEquals(0, GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING), "named attach must restore the draw binding");
                return fbo;
            }
        };

        abstract int attach(int tex);
    }

    @ParameterizedTest
    @EnumSource(Attachment.class)
    void storageIsReleasedOnlyAfterTheDelayFramesUnattached(Attachment attachment) {
        framesPass(RELEASE_DELAY_FRAMES);
        final int before = GLStateManager.deferredTexturesAwaitingRelease();
        final int tex = mipmappedTexture();
        final int fbo = attachment.attach(tex);

        GLStateManager.glDeleteTextures(tex);
        assertEquals(before + 1, GLStateManager.deferredTexturesAwaitingRelease());
        framesPass(RELEASE_DELAY_FRAMES - 1);
        assertEquals(SIZE, levelWidth(tex, 0), "storage must outlive the delete by the delay frames");
        if (fbo != 0) {
            framesPass(RELEASE_DELAY_FRAMES);
            assertEquals(SIZE, levelWidth(tex, 0), "an attached texture keeps its storage");
            assertEquals(before + 1, GLStateManager.deferredTexturesAwaitingRelease());
            GLStateManager.glBindFramebuffer(GL30.GL_FRAMEBUFFER, fbo);
            GLStateManager.glDeleteTextures(GLStateManager.glGenTextures());
            assertEquals(tex, GL30.glGetFramebufferAttachmentParameteri(GL30.GL_FRAMEBUFFER, GL30.GL_COLOR_ATTACHMENT0, GL30.GL_FRAMEBUFFER_ATTACHMENT_OBJECT_NAME), "a flush must not delete an attached texture");
            GLStateManager.glBindFramebuffer(GL30.GL_FRAMEBUFFER, 0);
            GLStateManager.glDeleteFramebuffers(BufferUtils.createIntBuffer(1).put(fbo).flip());
            framesPass(RELEASE_DELAY_FRAMES - 1);
            assertEquals(SIZE, levelWidth(tex, 0), "storage must outlive the detach by the delay frames");
        }
        framesPass(1);

        for (int level = 0; level < LEVELS; level++) assertEquals(0, levelWidth(tex, level), "level " + level);
        assertEquals(before, GLStateManager.deferredTexturesAwaitingRelease(), "released storage leaves nothing awaiting release");
        assertEquals(GL11.GL_NO_ERROR, GL11.glGetError());
    }

    enum Rejection { ATTACH, BIND_TEXTURE_UNIT }

    @ParameterizedTest
    @EnumSource(Rejection.class)
    void deletedNamesAreRejected(Rejection rejection) {
        final int tex = mipmappedTexture();
        final int other = mipmappedTexture();
        GLStateManager.glDeleteTextures(tex);
        try {
            switch (rejection) {
                case ATTACH -> {
                    final int fbo = GLStateManager.glGenFramebuffers();
                    try {
                        GLStateManager.glBindFramebuffer(GL30.GL_FRAMEBUFFER, fbo);
                        GLStateManager.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER, GL30.GL_COLOR_ATTACHMENT0, GL11.GL_TEXTURE_2D, tex, 0);
                        assertEquals(GL11.GL_NONE, GL30.glGetFramebufferAttachmentParameteri(GL30.GL_FRAMEBUFFER, GL30.GL_COLOR_ATTACHMENT0, GL30.GL_FRAMEBUFFER_ATTACHMENT_OBJECT_TYPE));
                    } finally {
                        GLStateManager.glBindFramebuffer(GL30.GL_FRAMEBUFFER, 0);
                        GLStateManager.glDeleteFramebuffers(fbo);
                    }
                }
                case BIND_TEXTURE_UNIT -> {
                    GLStateManager.glBindTextureUnit(2, other);
                    GLStateManager.glBindTextureUnit(2, tex);
                    GLStateManager.glActiveTexture(GL13.GL_TEXTURE2);
                    assertEquals(other, GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D), "driver binding");
                    assertEquals(other, GLStateManager.glGetInteger(GL11.GL_TEXTURE_BINDING_2D), "cached binding");
                }
            }
            assertEquals(GL11.GL_NO_ERROR, GL11.glGetError());
        } finally {
            GLStateManager.glActiveTexture(GL13.GL_TEXTURE0);
            GLStateManager.glBindTextureUnit(2, 0);
            GLStateManager.glDeleteTextures(other);
        }
    }

    @Test
    void bindTexturesBindsEachTextureToItsTarget() {
        final int t2d = mipmappedTexture();
        final int t3d = GLStateManager.glGenTextures();
        GLStateManager.glBindTexture(GL12.GL_TEXTURE_3D, t3d);
        GLStateManager.glBindTexture(GL12.GL_TEXTURE_3D, 0);
        try {
            GLStateManager.glBindTextures(1, new int[] {t2d, t3d});

            assertEquals(GL13.GL_TEXTURE0, GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE), "active unit unchanged");
            GLStateManager.glActiveTexture(GL13.GL_TEXTURE1);
            assertEquals(t2d, GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D), "unit 1 driver binding");
            assertEquals(t2d, GLStateManager.glGetInteger(GL11.GL_TEXTURE_BINDING_2D), "unit 1 cached binding");
            GLStateManager.glActiveTexture(GL13.GL_TEXTURE2);
            assertEquals(t3d, GL11.glGetInteger(GL12.GL_TEXTURE_BINDING_3D), "unit 2 driver binding");
            assertEquals(t3d, cachedBinding(2), "unit 2 cached binding");
            GLStateManager.glActiveTexture(GL13.GL_TEXTURE0);

            final int unit0 = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
            GLStateManager.glBindTextures(GLStateManager.MAX_TEXTURE_UNITS - 1, new int[] {t3d, t2d});
            assertEquals(unit0, GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D), "an out-of-range multi-bind must not land on the active unit");

            GLStateManager.glDeleteTextures(t2d);
            GLStateManager.glActiveTexture(GL13.GL_TEXTURE1);
            assertEquals(0, GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D), "delete unbinds a multi-bound texture");
            GLStateManager.glActiveTexture(GL13.GL_TEXTURE0);
        } finally {
            GLStateManager.glActiveTexture(GL13.GL_TEXTURE0);
            GLStateManager.glBindTextures(1, new int[] {0, 0});
            GLStateManager.glDeleteTextures(t3d);
        }
        assertEquals(GL11.GL_NO_ERROR, GL11.glGetError());
    }

    @Test
    void dsaCreated3DTextureIsBoundAs3D() {
        final int tex = RenderSystem.createTexture(GL12.GL_TEXTURE_3D);
        final int other = GLStateManager.glGenTextures();
        GLStateManager.glBindTexture(GL12.GL_TEXTURE_3D, other);
        RenderSystem.texImage3D(tex, GL12.GL_TEXTURE_3D, 0, GL11.GL_RGBA8, SIZE, SIZE, SIZE, 0, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, (ByteBuffer) null);
        assertEquals(tex, GL11.glGetInteger(GL12.GL_TEXTURE_BINDING_3D), "texImage3D binds the texture as 3D");
        assertEquals(tex, cachedBinding(0), "cached 3D binding");
        GLStateManager.glBindTexture(GL12.GL_TEXTURE_3D, 0);
        GLStateManager.glDeleteTextures(tex);
        GLStateManager.glDeleteTextures(other);
        assertEquals(GL11.GL_NO_ERROR, GL11.glGetError());
    }

    @Test
    void createdNon2DTextureIsNotReleasedAs2D() {
        assumeTrue(GLStateManager.capabilities.OpenGL45, "no glCreateTextures on this context");
        final int tex = GLStateManager.glCreateTextures(GL12.GL_TEXTURE_3D);
        GLStateManager.glDeleteTextures(tex);
        framesPass(RELEASE_DELAY_FRAMES);
        assertEquals(GL11.GL_NO_ERROR, GL11.glGetError());
    }

    @Test
    void deletingDetachesOnlyFromBoundFramebuffers() {
        final int tex = mipmappedTexture();
        final int unbound = Attachment.FRAMEBUFFER.attach(tex);
        final int bound = Attachment.FRAMEBUFFER.attach(tex);
        try {
            GLStateManager.glBindFramebuffer(GL30.GL_FRAMEBUFFER, bound);

            GLStateManager.glDeleteTextures(tex);

            assertEquals(GL11.GL_NONE, GL30.glGetFramebufferAttachmentParameteri(GL30.GL_FRAMEBUFFER, GL30.GL_COLOR_ATTACHMENT0, GL30.GL_FRAMEBUFFER_ATTACHMENT_OBJECT_TYPE));
            GLStateManager.glBindFramebuffer(GL30.GL_FRAMEBUFFER, unbound);
            assertEquals(tex, GL30.glGetFramebufferAttachmentParameteri(GL30.GL_FRAMEBUFFER, GL30.GL_COLOR_ATTACHMENT0, GL30.GL_FRAMEBUFFER_ATTACHMENT_OBJECT_NAME));
        } finally {
            GLStateManager.glBindFramebuffer(GL30.GL_FRAMEBUFFER, 0);
            GLStateManager.glDeleteFramebuffers(bound);
            GLStateManager.glDeleteFramebuffers(unbound);
        }
    }

    enum Rescue { PENDING, RELEASED, ATTACHED }

    @ParameterizedTest
    @EnumSource(Rescue.class)
    void rescuedTextureIsEmptyWithDefaultParameters(Rescue rescue) {
        final int tex = mipmappedTexture();
        GLStateManager.glBindTexture(GL11.GL_TEXTURE_2D, tex);
        GLStateManager.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
        GLStateManager.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_NEAREST);
        GLStateManager.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE);
        GLStateManager.glTexParameteri(GL11.GL_TEXTURE_2D, GL12.GL_TEXTURE_BASE_LEVEL, 1);
        GLStateManager.glTexParameteri(GL11.GL_TEXTURE_2D, GL12.GL_TEXTURE_MAX_LEVEL, 2);
        GLStateManager.glTexParameteri(GL11.GL_TEXTURE_2D, GL33.GL_TEXTURE_SWIZZLE_R, GL11.GL_GREEN);
        GLStateManager.glTexParameterf(GL11.GL_TEXTURE_2D, GL14.GL_TEXTURE_LOD_BIAS, 1.5f);
        GLStateManager.glTexParameter(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_BORDER_COLOR, (FloatBuffer) BufferUtils.createFloatBuffer(4).put(new float[] {1, 1, 1, 1}).flip());
        GLStateManager.glBindTexture(GL11.GL_TEXTURE_2D, 0);
        final int fbo = rescue == Rescue.ATTACHED ? Attachment.FRAMEBUFFER.attach(tex) : 0;
        GLStateManager.glDeleteTextures(tex);
        if (rescue != Rescue.PENDING) framesPass(RELEASE_DELAY_FRAMES);

        GLStateManager.glBindTexture(GL11.GL_TEXTURE_2D, tex);
        try {
            assertEquals(rescue == Rescue.ATTACHED ? SIZE : 0, levelWidth(tex, 0), "only an attached texture keeps its storage");
            final int[][] defaults = {
                {GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_NEAREST_MIPMAP_LINEAR},
                {GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR},
                {GL11.GL_TEXTURE_WRAP_S, GL11.GL_REPEAT},
                {GL12.GL_TEXTURE_BASE_LEVEL, 0},
                {GL12.GL_TEXTURE_MAX_LEVEL, 1000},
                {GL33.GL_TEXTURE_SWIZZLE_R, GL11.GL_RED},
            };
            for (int[] pair : defaults) {
                assertEquals(pair[1], GL11.glGetTexParameteri(GL11.GL_TEXTURE_2D, pair[0]), "driver pname 0x" + Integer.toHexString(pair[0]));
                assertEquals(pair[1], GLStateManager.glGetTexParameteri(GL11.GL_TEXTURE_2D, pair[0]), "cached pname 0x" + Integer.toHexString(pair[0]));
            }
            assertEquals(0.0f, GL11.glGetTexParameterf(GL11.GL_TEXTURE_2D, GL14.GL_TEXTURE_LOD_BIAS));
            final FloatBuffer border = BufferUtils.createFloatBuffer(16);
            GL11.glGetTexParameter(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_BORDER_COLOR, border);
            assertEquals(0.0f, border.get(0) + border.get(1) + border.get(2) + border.get(3), "border color");
            assertEquals(GL11.GL_NO_ERROR, GL11.glGetError());
        } finally {
            GLStateManager.glBindTexture(GL11.GL_TEXTURE_2D, 0);
            GLStateManager.glDeleteTextures(tex);
            if (fbo != 0) GLStateManager.glDeleteFramebuffers(fbo);
        }
    }

    @Test
    void rescuingWithADifferentTargetLeavesTheBoundTextureAlone() {
        final int t3d = GLStateManager.glGenTextures();
        final int tex = mipmappedTexture();
        try {
            GLStateManager.glBindTexture(GL12.GL_TEXTURE_3D, t3d);
            GLStateManager.glTexParameteri(GL12.GL_TEXTURE_3D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
            GLStateManager.glDeleteTextures(tex);

            GLStateManager.glBindTexture(GL12.GL_TEXTURE_3D, tex);

            assertEquals(GL11.GL_NO_ERROR, GL11.glGetError(), "GLSM drops the bind");
            assertEquals(t3d, cachedBinding(0), "cached 3D binding");
            assertEquals(GL11.GL_LINEAR, GL11.glGetTexParameteri(GL12.GL_TEXTURE_3D, GL11.GL_TEXTURE_MIN_FILTER));
            framesPass(RELEASE_DELAY_FRAMES);
            assertEquals(t3d, GL11.glGetInteger(GL12.GL_TEXTURE_BINDING_3D), "the release must not unbind the 3D texture");
            assertEquals(0, levelWidth(tex, 0), "the mismatched bind must leave the texture deferred");
        } finally {
            GLStateManager.glBindTexture(GL12.GL_TEXTURE_3D, 0);
            GLStateManager.glDeleteTextures(t3d);
        }
    }

    @Test
    void deletingAStaleNameKeepsTheBoundTexture() {
        final int live = mipmappedTexture();
        final int stale = GLStateManager.glGenTextures();
        try {
            GLStateManager.glDeleteTextures(stale);
            GLStateManager.glDeleteTextures(GLStateManager.glGenTextures());
            GLStateManager.glBindTexture(GL11.GL_TEXTURE_2D, live);

            GLStateManager.glDeleteTextures(stale);
            framesPass(RELEASE_DELAY_FRAMES);

            assertEquals(SIZE, levelWidth(live, 0), "a stale delete must not empty the bound texture");
            assertEquals(GL11.GL_NO_ERROR, GL11.glGetError());
        } finally {
            GLStateManager.glBindTexture(GL11.GL_TEXTURE_2D, 0);
            GLStateManager.glDeleteTextures(live);
        }
    }

    @Test
    void deletingAnImmutableTextureRaisesNoError() {
        final int tex = immutableTexture();

        GLStateManager.glDeleteTextures(tex);

        assertEquals(GL11.GL_NO_ERROR, GL11.glGetError());
    }

    @Test
    void rescuedImmutableTextureResetsParametersAndDeletesAgain() {
        final int tex = immutableTexture();
        GLStateManager.glBindTexture(GL11.GL_TEXTURE_2D, tex);
        GLStateManager.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
        GLStateManager.glBindTexture(GL11.GL_TEXTURE_2D, 0);
        GLStateManager.glDeleteTextures(tex);
        GLStateManager.glBindTexture(GL11.GL_TEXTURE_2D, tex);

        assertEquals(GL11.GL_NEAREST_MIPMAP_LINEAR, GL11.glGetTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER));
        GLStateManager.glDeleteTextures(tex);

        assertEquals(GL11.GL_NO_ERROR, GL11.glGetError());
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void deletingA2DTextureWhileA3DTextureIsBound(boolean deletedIsHidden2D) {
        final int t2d = GLStateManager.glGenTextures();
        final int t3d = GLStateManager.glGenTextures();
        final int other = GLStateManager.glGenTextures();
        try {
            GLStateManager.glBindTexture(GL11.GL_TEXTURE_2D, t2d);
            GLStateManager.glBindTexture(GL12.GL_TEXTURE_3D, t3d);

            GLStateManager.glDeleteTextures(deletedIsHidden2D ? t2d : other);

            assertEquals(GL11.GL_NO_ERROR, GL11.glGetError());
            assertEquals(deletedIsHidden2D ? 0 : t2d, GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D), "only the deleted texture leaves the hidden 2D binding");
            assertEquals(t3d, GL11.glGetInteger(GL12.GL_TEXTURE_BINDING_3D));
        } finally {
            GLStateManager.glBindTexture(GL12.GL_TEXTURE_3D, 0);
            GLStateManager.glBindTexture(GL11.GL_TEXTURE_2D, 0);
            GLStateManager.glDeleteTextures(t3d);
            GLStateManager.glDeleteTextures(deletedIsHidden2D ? other : t2d);
        }
    }
}
