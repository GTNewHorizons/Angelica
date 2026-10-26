package com.gtnewhorizons.angelica.debug.screen;

import com.gtnewhorizons.angelica.api.debug.DebugScreenEntryStatus;
import com.gtnewhorizons.angelica.client.font.FontTestFixture;
import com.gtnewhorizons.angelica.config.AngelicaConfig;
import com.gtnewhorizons.angelica.glsm.GLCoreTest;
import com.gtnewhorizons.angelica.glsm.GLSMBlend;
import com.gtnewhorizons.angelica.glsm.GLSMDepthStencilAlpha;
import com.gtnewhorizons.angelica.glsm.testutil.Reflect;
import com.gtnewhorizons.angelica.mixins.interfaces.IGameSettingsExt;
import net.minecraft.client.audio.SoundHandler;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.client.settings.GameSettings;
import net.minecraft.util.StatCollector;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.lwjgl.opengl.GL11;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.withSettings;

@GLCoreTest
class DebugOptionsScreenGLTest {

    private static final int WIDTH = 320;
    private static final int HEIGHT = 240;
    private static final int ALWAYS_BUTTON = 2;

    private static FontTestFixture fixture;
    private static DebugScreenEntryList savedEntries;
    private static boolean savedModernize;
    private static boolean savedFontRenderer;
    private static Path profileFile;
    private static DebugScreenEntryList entries;

    @TempDir
    static Path tempDir;

    private DebugOptionsScreen screen;

    @BeforeAll
    static void setUp() throws Exception {
        fixture = new FontTestFixture(tempDir);
        fixture.batcher();
        fixture.font.setUnicodeFlag(false);
        Reflect.set(fixture.font, "charWidth", FontTestFixture.charWidths());
        Reflect.set(fixture.font, "glyphWidth", new byte[65536]);
        fixture.minecraft.fontRenderer = fixture.font;
        fixture.minecraft.gameSettings = mock(GameSettings.class, withSettings().extraInterfaces(IGameSettingsExt.class));
        fixture.minecraft.displayWidth = WIDTH * 2;
        fixture.minecraft.displayHeight = HEIGHT * 2;
        when(fixture.minecraft.getTextureManager()).thenReturn(mock(TextureManager.class));
        when(fixture.minecraft.getSoundHandler()).thenReturn(mock(SoundHandler.class));
        savedModernize = AngelicaConfig.modernizeF3Screen;
        savedFontRenderer = AngelicaConfig.enableFontRenderer;
        savedEntries = DebugScreen.ENTRIES;
        profileFile = tempDir.resolve("debug-profile.json");
        entries = new DebugScreenEntryList(TestRegistry.main());
        entries.setFile(profileFile.toFile());
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
    void openScreen() {
        AngelicaConfig.modernizeF3Screen = true;
        AngelicaConfig.enableFontRenderer = true;
        Reflect.setStatic(DebugScreen.class, "groupedFailed", false);
        screen = new DebugOptionsScreen();
        fixture.minecraft.currentScreen = screen;
        screen.setWorldAndResolution(fixture.minecraft, WIDTH, HEIGHT);
    }

    private List<?> rows() {
        return Reflect.get(screen, "rows");
    }

    private int shownRows() {
        return rows().size();
    }

    private GuiTextField searchField() {
        return Reflect.get(screen, "searchField");
    }

    private void type(String text) {
        for (final char c : text.toCharArray()) {
            screen.keyTyped(c, 0);
        }
    }

    @Test
    void statusButtonPersistsTheChoice() throws Exception {
        final int first = Reflect.get(screen, "scrollRow");
        final int visible = Reflect.get(screen, "visibleRows");
        DebugEntryDef target = null;
        Object button = null;
        for (int i = first; i < Math.min(rows().size(), first + visible) && button == null; i++) {
            final Object row = rows().get(i);
            final DebugEntryDef def = Reflect.get(row, "def");
            final Object[] buttons = Reflect.get(row, "buttons");
            if (def == null || buttons.length <= ALWAYS_BUTTON || entries.getStatus(def) == DebugScreenEntryStatus.ALWAYS_ON) continue;
            target = def;
            button = buttons[ALWAYS_BUTTON];
        }
        assertNotNull(button);
        final int x = Reflect.get(button, "x");
        final int y = Reflect.get(button, "y");
        final int w = Reflect.get(button, "w");
        final int h = Reflect.get(button, "h");

        screen.mouseClicked(x + w / 2, y + h / 2, 0);

        assertEquals(DebugScreenEntryStatus.ALWAYS_ON, entries.getStatus(target));
        final String saved = Files.readString(profileFile).replaceAll("\\s", "");
        assertTrue(saved.contains("\"" + target.id + "\":\"alwaysOn\""));
    }

    @Test
    void twoButtonRowShowingAlwaysOnSelectsOverlay() {
        AngelicaConfig.modernizeF3Screen = false;
        screen = new DebugOptionsScreen();
        fixture.minecraft.currentScreen = screen;
        screen.setWorldAndResolution(fixture.minecraft, WIDTH, HEIGHT);
        DebugEntryDef target = null;
        Object[] buttons = null;
        for (final Object row : rows()) {
            final DebugEntryDef def = Reflect.get(row, "def");
            final Object[] candidate = Reflect.get(row, "buttons");
            if (def != null && def.supportsAlways && candidate.length == 2) {
                target = def;
                buttons = candidate;
                break;
            }
        }
        assertNotNull(target);
        entries.overrideForSession(target, DebugScreenEntryStatus.ALWAYS_ON);
        try {
            screen.updateScreen();
            assertEquals(DebugScreenEntryStatus.ALWAYS_ON, entries.effectiveStatus(target));
            final boolean neverSelected = Reflect.get(buttons[0], "selected");
            final boolean overlaySelected = Reflect.get(buttons[1], "selected");
            assertTrue(overlaySelected);
            assertTrue(!neverSelected);
        } finally {
            entries.overrideForSession(target, null);
        }
    }

    @Test
    void searchFiltersRowsAndKeepsFocusAfterAClick() {
        final int all = shownRows();
        final Object last = rows().get(all - 1);
        final DebugEntryDef def = Reflect.get(last, "def");
        final String filter = def != null ? def.path : "size";

        type(filter);
        final int filtered = shownRows();
        assertTrue(filtered < all);
        assertEquals(filter, searchField().getText());

        screen.mouseClicked(1, 1, 0);
        type("x");

        assertEquals(filter + "x", searchField().getText());
    }

    @Test
    void panelsListShowsGroupIdsAndIsSearchable() {
        type("angelica:position");
        Object panel = null;
        int panels = 0;
        for (final Object row : rows()) {
            if (!"PANEL".equals(Reflect.get(row, "kind").toString())) continue;
            panels++;
            panel = row;
        }
        assertEquals(1, panels);
        assertEquals("angelica:position", Reflect.get(panel, "value"));
        assertEquals(StatCollector.translateToLocal("angelica.debug.group.position"), Reflect.get(panel, "plain"));
    }

    @Test
    void drawScreenLeavesNoGlError() {
        final int firstVisible = Reflect.get(screen, "scrollRow");
        String filter = null;
        for (int i = firstVisible; i < rows().size() && filter == null; i++) {
            final DebugEntryDef def = Reflect.get(rows().get(i), "def");
            if (def != null && Reflect.get(rows().get(i), "tooltip") != null) filter = def.path.substring(0, Math.min(3, def.path.length()));
        }
        assertNotNull(filter);
        type(filter);
        searchField().setSelectionPos(0);
        assertTrue(shownRows() > 0);
        final int first = Reflect.get(screen, "scrollRow");
        final int visible = Reflect.get(screen, "visibleRows");
        int hovered = -1;
        for (int i = first; i < Math.min(rows().size(), first + visible) && hovered < 0; i++) {
            if (Reflect.get(rows().get(i), "def") != null && Reflect.get(rows().get(i), "tooltip") != null) hovered = i;
        }
        assertTrue(hovered >= 0);
        final int viewTop = Reflect.get(screen, "viewTop");
        final int nameX = Reflect.get(screen, "nameX");
        final int rowH = Reflect.getStatic(DebugOptionsScreen.class, "ROW_H");
        final boolean blend = GLSMBlend.getBlendMode().isEnabled();
        final boolean depth = GLSMDepthStencilAlpha.getDepthTest().isEnabled();
        GLSMDepthStencilAlpha.enableDepthTest();
        try {
            GL11.glGetError();

            screen.drawScreen(nameX + 1, viewTop + (hovered - first) * rowH + rowH / 2, 0.0f);

            assertEquals(GL11.GL_NO_ERROR, GL11.glGetError());
            assertTrue(GLSMDepthStencilAlpha.getDepthTest().isEnabled());
            assertEquals(blend, GLSMBlend.getBlendMode().isEnabled());
            final Object batcher = Reflect.get(screen, "batcher");
            assertEquals(0, (int) Reflect.get(batcher, "batchDepth"));
            assertNotNull(Reflect.get(screen, "measuredTooltip"));
        } finally {
            if (!depth) GLSMDepthStencilAlpha.disableDepthTest();
        }
    }
}
