package com.gtnewhorizons.angelica.rendering.tesr;

import com.gtnewhorizons.angelica.config.AngelicaConfig;
import com.gtnewhorizons.angelica.glsm.ExitState;
import com.gtnewhorizons.angelica.glsm.GLCoreTest;
import com.gtnewhorizons.angelica.glsm.GLStateManager;
import com.gtnewhorizons.angelica.glsm.testutil.Reflect;
import com.gtnewhorizons.angelica.glsm.texture.TextureInfoCache;
import net.coderbot.batchedentityrendering.impl.AngelicaBufferSource;
import net.coderbot.iris.layer.PassOverride;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.ITextureObject;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.util.ResourceLocation;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.joml.Matrix4f;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static com.gtnewhorizons.angelica.rendering.tesr.RetainedTesrTestSupport.cubes;
import static com.gtnewhorizons.angelica.rendering.tesr.RetainedTesrTestSupport.onlyGroup;
import static com.gtnewhorizons.angelica.rendering.tesr.RetainedTesrTestSupport.template;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@GLCoreTest
class EntityTextureAtlasGLTest {

    private static final int WIDTH = 4;
    private static final int HEIGHT = 2;
    private static final int PAGE = EntityTextureAtlas.PAGE_SIZE;

    private final List<ITextureObject> pages = new ArrayList<>();
    private Minecraft previous;
    private int source, passColor, passFbo;

    @BeforeAll
    static void listenForDeletes() {
        TextureInfoCache.INSTANCE.addDeleteListener(EntityTextureAtlas.INSTANCE::onTextureDeleted);
    }

    @BeforeEach
    void setUp() {
        previous = Minecraft.getMinecraft();
        final Minecraft minecraft = mock(Minecraft.class);
        final TextureManager textures = mock(TextureManager.class);
        when(textures.loadTexture(any(ResourceLocation.class), any(ITextureObject.class))).thenAnswer(call -> pages.add(call.getArgument(1)));
        when(minecraft.getTextureManager()).thenReturn(textures);
        Reflect.setStatic(Minecraft.class, "theMinecraft", minecraft);

        passColor = texture(GL11.GL_NEAREST, 0);
        passFbo = GLStateManager.glGenFramebuffers();
        GLStateManager.glBindFramebuffer(GL30.GL_FRAMEBUFFER, passFbo);
        GLStateManager.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER, GL30.GL_COLOR_ATTACHMENT0, GL11.GL_TEXTURE_2D, passColor, 0);
        source = texture(GL11.GL_NEAREST, 0);
    }

    @AfterEach
    void tearDown() {
        GLStateManager.cancelExitCapture();
        EntityTextureAtlas.INSTANCE.freePages();
        GLStateManager.glBindFramebuffer(GL30.GL_FRAMEBUFFER, 0);
        GLStateManager.glDeleteFramebuffers(passFbo);
        GLStateManager.glDeleteTextures(source);
        GLStateManager.glDeleteTextures(passColor);
        Reflect.setStatic(Minecraft.class, "theMinecraft", previous);
    }

    private static int argb(int x, int y, int seed) {
        return 0xFF000000 | (x * 50 + seed) << 16 | (y * 90 + seed) << 8 | 0x20;
    }

    @ParameterizedTest
    @CsvSource({"false, false", "true, false", "false, true", "true, true"})
    void entityReplayResolvesAtlasRegionsWithoutRemappingSharedGlintUploads(boolean shared, boolean glint) {
        final boolean atlasEnabled = AngelicaConfig.enableEntityTextureAtlas;
        final ModelPartBatcher batcher = ModelPartBatcher.INSTANCE;
        final RetainedTesrGroups previousGroups = Reflect.get(batcher, "activeGroups");
        final RetainedTesrGroups groups = new RetainedTesrGroups(new AngelicaBufferSource());
        final int otherTexture = texture(GL11.GL_NEAREST, 12);
        groups.beginPass(new Matrix4f(), 0, 0, 0, new InstancedTemplateRenderer(new InstanceRing()),
            new RetainedTesrTestSupport.CountingPipeline(true, true));
        try {
            AngelicaConfig.enableEntityTextureAtlas = true;
            Reflect.set(batcher, "activeGroups", groups);
            final EntityTextureAtlas.Slot shadowSlot = EntityTextureAtlas.INSTANCE.slotFor(source, false);
            assertNotNull(shadowSlot);
            EntityTextureAtlas.INSTANCE.onTextureDeleted(source);
            EntityTextureAtlas.INSTANCE.recycleReleasedRegions();
            assertSame(shadowSlot, EntityTextureAtlas.INSTANCE.slotFor(otherTexture, false));

            final EntityRenderCommands.Model entry = new EntityRenderCommands.Model();
            entry.instance.template = template();
            entry.cubes = cubes(1);
            entry.cubesInsideTexture = true;
            entry.scale = 1;
            entry.inputs.material = EntityMaterials.CUTOUT;
            entry.inputs.texture = new ResourceLocation("angelica", "test/replay");
            entry.inputs.textureId = source;
            entry.inputs.pass = PassOverride.NONE;
            entry.inputs.glintSlot = -1;
            if (glint) {
                entry.glint = new EntityRenderCommands.ModelGlint();
                entry.replayed = new RetainedTesrGroups.InstanceRange();
                entry.replayed.reuseUpload = true;
            }
            if (shared) {
                entry.source = new RetainedTesrGroups.InstanceColumns();
                entry.source.addCubes(entry.instance.template, entry.cubes, 1, new Matrix4f(), 0, -1, 0, 0, shadowSlot);
                entry.end = 1;
            }
            batcher.beginReplay(new Matrix4f());
            batcher.replay(entry);

            final RetainedTesrGroups.Group main = onlyGroup(groups);
            final EntityTextureAtlas.Slot mainSlot = main.cubeColumns.atlasSlot(0);
            if (glint) {
                assertNull(mainSlot, "glint reuses the armor upload, whose UVs must still address the original texture");
                assertEquals(entry.inputs.texture, main.layer.getTextureId());
            } else {
                assertNotNull(mainSlot);
                assertNotSame(shadowSlot, mainSlot);
                assertSame(EntityTextureAtlas.INSTANCE.slotFor(source, false), mainSlot);
            }
        } finally {
            Reflect.set(batcher, "activeGroups", previousGroups);
            AngelicaConfig.enableEntityTextureAtlas = atlasEnabled;
            groups.clear();
            GLStateManager.glDeleteTextures(otherTexture);
        }
    }

    private static int texture(int filter, int seed) {
        final int id = GLStateManager.glGenTextures();
        GLStateManager.glBindTexture(GL11.GL_TEXTURE_2D, id);
        GLStateManager.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, filter);
        GLStateManager.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, filter);
        GLStateManager.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA, WIDTH, HEIGHT, 0, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, texels(seed));
        GLStateManager.glBindTexture(GL11.GL_TEXTURE_2D, 0);
        return id;
    }

    private static ByteBuffer texels(int seed) {
        final ByteBuffer texels = BufferUtils.createByteBuffer(WIDTH * HEIGHT * 4);
        for (int y = 0; y < HEIGHT; y++) {
            for (int x = 0; x < WIDTH; x++) {
                final int c = argb(x, y, seed);
                texels.put((byte) (c >> 16)).put((byte) (c >> 8)).put((byte) c).put((byte) (c >>> 24));
            }
        }
        texels.flip();
        return texels;
    }

    private void assertSlotHolds(EntityTextureAtlas.Slot slot, int seed) {
        final ByteBuffer page = BufferUtils.createByteBuffer(PAGE * PAGE * 4);
        GLStateManager.glBindTexture(GL11.GL_TEXTURE_2D, pages.get(0).getGlTextureId());
        GLStateManager.glGetTexImage(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, page);
        GLStateManager.glBindTexture(GL11.GL_TEXTURE_2D, 0);
        for (int y = -1; y <= HEIGHT; y++) {
            for (int x = -1; x <= WIDTH; x++) {
                final int at = ((slot.y + 1 + y) * PAGE + slot.x + 1 + x) * 4;
                final int actual = (page.get(at + 3) & 0xFF) << 24 | (page.get(at) & 0xFF) << 16 | (page.get(at + 1) & 0xFF) << 8 | page.get(at + 2) & 0xFF;
                final int expected = argb(Math.clamp(x, 0, WIDTH - 1), Math.clamp(y, 0, HEIGHT - 1), seed);
                assertEquals(Integer.toHexString(expected), Integer.toHexString(actual), "page texel for source (" + x + ", " + y + ")");
            }
        }
    }

    @Test
    void copiesTheTextureWithEdgeGuttersAndRestoresTheFramebuffers() {
        final ExitState state = new ExitState();
        GLStateManager.beginExitCapture(state);
        final EntityTextureAtlas.Slot slot = EntityTextureAtlas.INSTANCE.slotFor(source, false);
        assertTrue(GLStateManager.finishExitCapture(state), "first-use atlas resources are internal batch operations");
        assertNotNull(slot);
        assertEquals(passFbo, GLStateManager.getDrawFramebuffer(), "draw framebuffer restored");
        assertEquals(passFbo, GLStateManager.getReadFramebuffer(), "read framebuffer restored");
        assertEquals((slot.x + 1) / (float) PAGE, slot.u);
        assertEquals(WIDTH / (float) PAGE, slot.w);
        assertSlotHolds(slot, 0);
    }

    @Test
    void reuploadingTheTextureRefreshesItsSlot() {
        final EntityTextureAtlas.Slot slot = EntityTextureAtlas.INSTANCE.slotFor(source, false);
        GLStateManager.glBindTexture(GL11.GL_TEXTURE_2D, source);
        GLStateManager.glTexSubImage2D(GL11.GL_TEXTURE_2D, 0, 0, 0, WIDTH, HEIGHT, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, texels(7));
        GLStateManager.glBindTexture(GL11.GL_TEXTURE_2D, 0);
        assertSame(slot, EntityTextureAtlas.INSTANCE.slotFor(source, false));
        assertSlotHolds(slot, 7);
    }

    @Test
    void filteredTexturesKeepTheirOwnDraw() {
        final int filtered = texture(GL11.GL_LINEAR, 0);
        try {
            assertNull(EntityTextureAtlas.INSTANCE.slotFor(filtered, false));
        } finally {
            GLStateManager.glDeleteTextures(filtered);
        }
    }

    @Test
    void switchingToLinearFilteringDropsTheSlot() {
        final EntityTextureAtlas.Slot slot = EntityTextureAtlas.INSTANCE.slotFor(source, false);
        assertNotNull(slot);
        GLStateManager.glBindTexture(GL11.GL_TEXTURE_2D, source);
        GLStateManager.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
        assertNull(EntityTextureAtlas.INSTANCE.slotFor(source, false), "integer filter change");
        GLStateManager.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_NEAREST);
        assertSame(slot, EntityTextureAtlas.INSTANCE.slotFor(source, false), "the kept slot comes back");
        GLStateManager.glTexParameterf(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
        GLStateManager.glBindTexture(GL11.GL_TEXTURE_2D, 0);
        assertNull(EntityTextureAtlas.INSTANCE.slotFor(source, false), "float filter change");
    }

    @Test
    void drawingIntoTheTextureDropsItsSlot() {
        assertNotNull(EntityTextureAtlas.INSTANCE.slotFor(source, false));
        final int target = GLStateManager.glGenFramebuffers();
        try {
            GLStateManager.glBindFramebuffer(GL30.GL_FRAMEBUFFER, target);
            GLStateManager.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER, GL30.GL_COLOR_ATTACHMENT0, GL11.GL_TEXTURE_2D, source, 0);
            GLStateManager.glClearColor(1.0f, 0.0f, 0.0f, 1.0f);
            GLStateManager.glClear(GL11.GL_COLOR_BUFFER_BIT);
        } finally {
            GLStateManager.glBindFramebuffer(GL30.GL_FRAMEBUFFER, passFbo);
            GLStateManager.glDeleteFramebuffers(target);
        }
        assertNull(EntityTextureAtlas.INSTANCE.slotFor(source, false));
    }

    @Test
    void clearingKeepsThePagesAndRefillsThem() {
        final EntityTextureAtlas.Slot before = EntityTextureAtlas.INSTANCE.slotFor(source, false);
        EntityTextureAtlas.INSTANCE.clear();
        final EntityTextureAtlas.Slot after = EntityTextureAtlas.INSTANCE.slotFor(source, false);
        assertNotNull(after);
        assertNotNull(before);
        assertSame(before.page, after.page, "a cleared atlas reuses its page instead of deleting and reallocating it");
        assertEquals(1, pages.size(), "the page is registered with the texture manager once");
        assertSlotHolds(after, 0);
    }

    @Test
    void aDeletedTexturesRegionIsReusedOnlyFromTheNextPass() {
        final EntityTextureAtlas.Slot first = EntityTextureAtlas.INSTANCE.slotFor(source, false);
        assertNotNull(first);
        GLStateManager.glDeleteTextures(source);
        final int samePass = texture(GL11.GL_NEAREST, 3);
        try {
            final EntityTextureAtlas.Slot taken = EntityTextureAtlas.INSTANCE.slotFor(samePass, false);
            assertNotNull(taken);
            assertFalse(taken.x == first.x && taken.y == first.y, "instances queued earlier in the pass still sample the old region");
            EntityTextureAtlas.INSTANCE.recycleReleasedRegions();
            source = texture(GL11.GL_NEAREST, 5);
            final EntityTextureAtlas.Slot reused = EntityTextureAtlas.INSTANCE.slotFor(source, false);
            assertNotNull(reused);
            assertEquals(first.x, reused.x);
            assertEquals(first.y, reused.y);
            assertSlotHolds(reused, 5);
        } finally {
            GLStateManager.glDeleteTextures(samePass);
        }
    }

    @Test
    void smallTexturesStillFitAfterALargeOneIsTurnedAway() {
        final int large = 512;
        final int[] filling = new int[4 * 9 + 1];
        final int small = texture(GL11.GL_NEAREST, 0);
        try {
            for (int i = 0; i < filling.length; i++) filling[i] = blankTexture(large);
            for (int i = 0; i < filling.length - 1; i++) assertNotNull(EntityTextureAtlas.INSTANCE.slotFor(filling[i], false), "large texture " + i);
            assertNull(EntityTextureAtlas.INSTANCE.slotFor(filling[filling.length - 1], false), "no page has room for another 512");
            assertNotNull(EntityTextureAtlas.INSTANCE.slotFor(small, false), "shelves still have room for a small texture");
        } finally {
            for (int id : filling) if (id != 0) GLStateManager.glDeleteTextures(id);
            GLStateManager.glDeleteTextures(small);
        }
    }

    private static int blankTexture(int size) {
        final int id = GLStateManager.glGenTextures();
        GLStateManager.glBindTexture(GL11.GL_TEXTURE_2D, id);
        GLStateManager.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_NEAREST);
        GLStateManager.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_NEAREST);
        GLStateManager.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA, size, size, 0, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, (ByteBuffer) null);
        GLStateManager.glBindTexture(GL11.GL_TEXTURE_2D, 0);
        return id;
    }
}
