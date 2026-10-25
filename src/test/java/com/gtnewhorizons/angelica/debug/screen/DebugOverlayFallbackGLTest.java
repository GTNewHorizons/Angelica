package com.gtnewhorizons.angelica.debug.screen;

import com.gtnewhorizons.angelica.client.font.BatchingFontRenderer;
import com.gtnewhorizons.angelica.client.font.FontTestFixture;
import com.gtnewhorizons.angelica.client.font.TextMesh;
import com.gtnewhorizons.angelica.config.AngelicaConfig;
import com.gtnewhorizons.angelica.glsm.GLCoreTest;
import com.gtnewhorizons.angelica.glsm.GLStateManager;
import com.gtnewhorizons.angelica.glsm.testutil.Reflect;
import com.gtnewhorizons.angelica.mixins.interfaces.IGameSettingsExt;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.settings.GameSettings;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;

import java.nio.ByteBuffer;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.withSettings;

@GLCoreTest
class DebugOverlayFallbackGLTest {

    private static FontTestFixture fixture;
    private static ThrowingBatcher batcher;
    private static DebugScreenEntryList savedEntries;
    private static boolean savedModernize;
    private static boolean savedFontRenderer;

    @TempDir
    static Path tempDir;

    static final class ThrowingBatcher extends BatchingFontRenderer {

        boolean armed;
        boolean threw;

        ThrowingBatcher(FontRenderer font) {
            super(font, FontTestFixture.charWidths(), new int[32], FontTestFixture.ASCII);
        }

        @Override
        public float drawString(float x, float y, int argb, boolean shadow, boolean unicode, CharSequence text, int start, int length) {
            if (armed) {
                threw = true;
                throw new IllegalStateException("draw");
            }
            return super.drawString(x, y, argb, shadow, unicode, text, start, length);
        }
    }

    @BeforeAll
    static void setUp() throws Exception {
        fixture = new FontTestFixture(tempDir);
        batcher = new ThrowingBatcher(fixture.font);
        fixture.font.batcher = batcher;
        fixture.batcher();
        fixture.minecraft.gameSettings = mock(GameSettings.class, withSettings().extraInterfaces(IGameSettingsExt.class));
        savedModernize = AngelicaConfig.modernizeF3Screen;
        savedFontRenderer = AngelicaConfig.enableFontRenderer;
        savedEntries = DebugScreen.ENTRIES;
        final DebugScreenEntryList entries = new DebugScreenEntryList(TestRegistry.main());
        entries.setOverlayVisible(true);
        DebugScreen.ENTRIES = entries;
    }

    @AfterAll
    static void tearDown() {
        AngelicaConfig.modernizeF3Screen = savedModernize;
        AngelicaConfig.enableFontRenderer = savedFontRenderer;
        DebugScreen.ENTRIES = savedEntries;
        fixture.close();
    }

    @BeforeEach
    void enableGrouped() {
        AngelicaConfig.modernizeF3Screen = true;
        AngelicaConfig.enableFontRenderer = true;
        Reflect.setStatic(DebugScreen.class, "groupedFailed", false);
        Reflect.setStatic(DebugScreen.class, "overlayVisible", true);
        DebugScreenOverlay.INSTANCE.afterDebugBlock(true, false);
    }

    @AfterEach
    void restore() {
        batcher.armed = false;
        batcher.resetAfterCrash();
        Reflect.setStatic(DebugScreen.class, "groupedFailed", false);
        Reflect.setStatic(DebugScreen.class, "overlayVisible", false);
        DebugScreenOverlay.INSTANCE.afterDebugBlock(false, false);
    }

    private static RenderGameOverlayEvent.Text event(String... left) {
        final ScaledResolution resolution = Reflect.allocate(ScaledResolution.class);
        Reflect.set(resolution, "scaleFactor", 1);
        final RenderGameOverlayEvent.Text event = Reflect.allocate(RenderGameOverlayEvent.Text.class);
        Reflect.set(event, "resolution", resolution);
        Reflect.set(event, "left", new ArrayList<>(List.of(left)));
        Reflect.set(event, "right", new ArrayList<String>());
        return event;
    }

    private static boolean anyLitAfterDirectDraw() {
        GLStateManager.glViewport(0, 0, 128, 32);
        GLStateManager.glMatrixMode(GL11.GL_PROJECTION);
        GLStateManager.glLoadIdentity();
        GLStateManager.glOrtho(0.0, 128, 32, 0.0, -1.0, 1.0);
        GLStateManager.glMatrixMode(GL11.GL_MODELVIEW);
        GLStateManager.glLoadIdentity();
        GLStateManager.glClearColor(0.0f, 0.0f, 0.0f, 1.0f);
        GLStateManager.glClear(GL11.GL_COLOR_BUFFER_BIT);
        batcher.drawString(2.0f, 2.0f, 0xFFFFFFFF, false, false, "balanced", 0, 8);
        final ByteBuffer pixels = BufferUtils.createByteBuffer(128 * 32 * 4);
        GL11.glReadPixels(0, 0, 128, 32, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, pixels);
        for (int i = 0; i < pixels.capacity(); i += 4) {
            if (pixels.get(i) != 0) return true;
        }
        return false;
    }

    @Test
    void fontWithoutTheBatcherFallsBackToTheClassicLayout() {
        assertTrue(DebugScreen.isGroupedActive());
        assertFalse(DebugScreenOverlay.INSTANCE.onTextEvent(event("mod line"), Reflect.allocate(FontRenderer.class), 320, 240, false));
        assertFalse(DebugScreen.isGroupedActive());
    }

    @Test
    void throwingDrawFallsBackToTheClassicLayoutWithTheBatchBalanced() {
        batcher.armed = true;
        DebugScreenOverlay.INSTANCE.afterDebugBlock(true, true);
        assertTrue(DebugScreenOverlay.INSTANCE.onTextEvent(event("mod line"), fixture.font, 320, 240, false));
        assertTrue(batcher.threw);
        assertFalse(DebugScreen.isGroupedActive());
        batcher.armed = false;
        batcher.beginCapture(new TextMesh());
        batcher.abortCapture();
        assertTrue(anyLitAfterDirectDraw());
    }
}
