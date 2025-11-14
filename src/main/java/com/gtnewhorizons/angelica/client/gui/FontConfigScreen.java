package com.gtnewhorizons.angelica.client.gui;

import com.gtnewhorizon.gtnhlib.config.ConfigException;
import com.gtnewhorizon.gtnhlib.config.ConfigurationManager;
import com.gtnewhorizons.angelica.client.font.FontStrategist;
import com.gtnewhorizons.angelica.config.FontConfig;
import net.coderbot.iris.gui.element.widget.IrisButton;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiSlot;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.resources.I18n;
import org.lwjgl.input.Keyboard;

import java.awt.Font;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

public class FontConfigScreen extends GuiScreen {

    private static final Font[] availableFonts = FontStrategist.getAvailableFonts();
    private final GuiScreen parent;
    private final String title;
    private final String searchPrompt;
    private String currentPrimaryFontName;
    private String currentFallbackFontName;
    private FontList fontList;
    private int selectedPrimaryFontListPos = -1;
    private int selectedFallbackFontListPos = -1;
    private ArrayList<Font> displayedFonts;
    private GuiTextField searchBox;

    public FontConfigScreen(GuiScreen parent) {
        this.title = I18n.format("options.angelica.fontconfig.title");
        this.searchPrompt = I18n.format("options.angelica.fontconfig.searchprompt");
        this.parent = parent;
        this.currentPrimaryFontName = FontConfig.customFontNamePrimary;
        this.currentFallbackFontName = FontConfig.customFontNameFallback;
        this.displayedFonts = new ArrayList<>(Arrays.asList(availableFonts));
        for (int i = 0; i < availableFonts.length; i++) {
            if (Objects.equals(this.currentPrimaryFontName, availableFonts[i].getFontName())) {
                selectedPrimaryFontListPos = i;
            }
            if (Objects.equals(this.currentFallbackFontName, availableFonts[i].getFontName())) {
                selectedFallbackFontListPos = i;
            }
        }
    }

    SliderClone.Option optFontQuality = new SliderClone.Option(6, 72, 6);
    SliderClone.Option optShadowOffset = new SliderClone.Option(0, 2, 0.05f);
    SliderClone.Option optGlyphAspect = new SliderClone.Option(-1, 1, 0.05f);
    SliderClone.Option optGlyphScale = new SliderClone.Option(0.1f, 3, 0.05f);
    SliderClone.Option optWhitespaceScale = new SliderClone.Option(0.1f, 3, 0.05f);
    SliderClone.Option optGlyphSpacing = new SliderClone.Option(-2f, 2f, 0.05f);
    SliderClone.Option optFontAAMode = new SliderClone.Option(0, 2, 1);
    SliderClone.Option optFontAAStrength = new SliderClone.Option(1, 24, 1);

    public void initGui() {
        Keyboard.enableRepeatEvents(true);
        searchBox = new GuiTextField(this.fontRendererObj, this.width / 2 - 120, 24, 240, 20);
        searchBox.setMaxStringLength(64);
        fontList = new FontList();
        initButtons();
    }

    private void initButtons() {
        final int halfWidth = this.width / 2;

        this.buttonList.add(new IrisButton(halfWidth - 81 - 165, this.height - 20 - 7, 162, 20,
            FontConfig.enableCustomFont ? I18n.format("options.angelica.fontconfig.disable_custom_font") :
                I18n.format("options.angelica.fontconfig.enable_custom_font"), this::toggleCustomFont));
        this.buttonList.add(new IrisButton(halfWidth - 80, this.height - 20 - 7, 160, 20,
            I18n.format("options.angelica.fontconfig.reset_values"), this::resetValues));
        this.buttonList.add(new IrisButton(halfWidth - 81 + 165, this.height - 20 - 7, 162, 20,
            I18n.format("gui.done"), button -> this.onClose()));

        this.buttonList.add(
            new SliderClone.SliderCloneBuilder()
                .x(halfWidth - 180 - 6)
                .y(this.height - 41)
                .option(optFontQuality)
                .initialValue(FontConfig.customFontQuality)
                .setter(this::setFontQuality)
                .langKey("options.angelica.fontconfig.font_quality")
                .formatString("%2.0f")
                .build()
        );
        this.buttonList.add(
            new SliderClone.SliderCloneBuilder()
                .x(halfWidth - 180 - 6)
                .y(this.height - 65)
                .option(optShadowOffset)
                .initialValue(FontConfig.fontShadowOffset)
                .setter(value -> FontConfig.fontShadowOffset = value)
                .langKey("options.angelica.fontconfig.shadow_offset")
                .formatString("x%3.2f")
                .build()
        );
        this.buttonList.add(
            new SliderClone.SliderCloneBuilder()
                .x(halfWidth - 60 - 2)
                .y(this.height - 41)
                .option(optGlyphAspect)
                .initialValue(FontConfig.glyphAspect)
                .setter(value -> FontConfig.glyphAspect = value)
                .langKey("options.angelica.fontconfig.glyph_aspect")
                .build()
        );
        this.buttonList.add(
            new SliderClone.SliderCloneBuilder()
                .x(halfWidth - 60 - 2)
                .y(this.height - 65)
                .option(optGlyphScale)
                .initialValue(FontConfig.glyphScale)
                .setter(value -> FontConfig.glyphScale = value)
                .langKey("options.angelica.fontconfig.glyph_scale")
                .formatString("x%3.2f")
                .build()
        );
        this.buttonList.add(
            new SliderClone.SliderCloneBuilder()
                .x(halfWidth + 60 + 2)
                .y(this.height - 41)
                .option(optWhitespaceScale)
                .initialValue(FontConfig.whitespaceScale)
                .setter(value -> FontConfig.whitespaceScale = value)
                .langKey("options.angelica.fontconfig.whitespace_scale")
                .formatString("x%3.2f")
                .build()
        );
        this.buttonList.add(
            new SliderClone.SliderCloneBuilder()
                .x(halfWidth + 60 + 2)
                .y(this.height - 65)
                .option(optGlyphSpacing)
                .initialValue(FontConfig.glyphSpacing)
                .setter(value -> FontConfig.glyphSpacing = value)
                .langKey("options.angelica.fontconfig.glyph_spacing")
                .build()
        );
        this.buttonList.add(
            new SliderClone.SliderCloneBuilder()
                .x(halfWidth + 180 + 6)
                .y(this.height - 41)
                .option(optFontAAStrength)
                .initialValue(FontConfig.fontAAStrength)
                .setter(value -> FontConfig.fontAAStrength = value.intValue())
                .langKey("options.angelica.fontconfig.font_aa_strength")
                .formatString("%.0f")
                .build()
        );
        this.buttonList.add(
            new SliderClone.SliderCloneBuilder()
                .x(halfWidth + 180 + 6)
                .y(this.height - 65)
                .option(optFontAAMode)
                .initialValue(FontConfig.fontAAMode)
                .setter(value -> FontConfig.fontAAMode = value.intValue())
                .formatter(this::fontAAModeFormat)
                .langKey("options.angelica.fontconfig.aamode")
                .build()
        );
    }

    private void onClose() {
        applyChanges(true);
        this.mc.displayGuiScreen(parent);
    }

    private void applyChanges(boolean finalApply) {
        int pos;
        pos = selectedPrimaryFontListPos;
        if (pos >= 0 && pos < displayedFonts.size()) {
            FontConfig.customFontNamePrimary = displayedFonts.get(pos).getFontName();
        }
        pos = selectedFallbackFontListPos;
        if (pos >= 0 && pos < displayedFonts.size()) {
            FontConfig.customFontNameFallback = displayedFonts.get(pos).getFontName();
        }

        FontStrategist.reloadCustomFontProviders();

        if (finalApply) {
            ConfigurationManager.save(FontConfig.class);
        }
    }

    private void resetValues(IrisButton button) {
        try {
            ConfigurationManager.getConfigElements(FontConfig.class).forEach(elem -> elem.set(elem.getDefault()));
        } catch (ConfigException e) {
            throw new RuntimeException(e);
        }
        selectedPrimaryFontListPos = -1;
        selectedFallbackFontListPos = -1;
        FontStrategist.customFontInUse = false;
        super.buttonList.clear();
        this.initButtons();
    }

    private void toggleCustomFont(IrisButton button) {
        FontConfig.enableCustomFont = !FontConfig.enableCustomFont;
        applyChanges(false);
        button.displayString = FontConfig.enableCustomFont ?
            I18n.format("options.angelica.fontconfig.disable_custom_font") :
            I18n.format("options.angelica.fontconfig.enable_custom_font");
    }

    private int qualityLast = FontConfig.customFontQuality;

    private void setFontQuality(float quality) {
        FontConfig.customFontQuality = (int) quality;
        if (qualityLast != (int) quality) {
            applyChanges(false);
        }
        qualityLast = (int) quality;
    }

    private String fontAAModeFormat(float AAmode) {
        return switch ((int) AAmode) {
            case 2 -> I18n.format("options.angelica.fontconfig.aamode.aa_16x");
            case 1 -> I18n.format("options.angelica.fontconfig.aamode.aa_4x");
            default -> I18n.format("options.angelica.fontconfig.aamode.aa_none");
        };
    }

    private float lastMouseX = 0;
    private float lastMouseY = 0;
    private long lastStillTime = 0;
    @Override
    public void drawScreen(int mouseX, int mouseY, float delta) {
        super.drawDefaultBackground();
        fontList.drawScreen(mouseX, mouseY, delta);
        searchBox.drawTextBox();
        // I bet you thought this was drawn inside the search box, not on top of it.
        if (!this.searchBox.isFocused() && this.searchBox.getText().isEmpty()) {
            this.drawCenteredString(this.fontRendererObj, this.searchPrompt,
                this.searchBox.xPosition + this.fontRendererObj.getStringWidth(this.searchPrompt) / 2 + 4,
                this.searchBox.yPosition + this.searchBox.height / 2 - 4, 0xFFFFFF);
        }
        drawCenteredString(this.fontRendererObj, this.title, (int) (this.width * 0.5), 8, 0xFFFFFF);
        drawCenteredString(this.fontRendererObj, I18n.format("options.angelica.fontconfig.currentfonts",
            FontConfig.customFontNamePrimary, FontConfig.customFontNameFallback), (int) (this.width * 0.5), this.height - 92, 0xFFFFFF);
        super.drawScreen(mouseX, mouseY, delta);

        for (GuiButton guiButton : buttonList) {
            if (!(guiButton instanceof SliderClone slider)) { continue; }
            final int top = slider.yPosition;
            final int bot = slider.yPosition + slider.height;
            final int left = slider.xPosition;
            final int right = slider.xPosition + slider.width;
            if (mouseY < top || mouseY >= bot || mouseX < left || mouseX >= right) { continue; }
            if (mouseX == lastMouseX && mouseY == lastMouseY) {
                if (lastStillTime == 0) {
                    lastStillTime = System.currentTimeMillis();
                }
                if (lastStillTime + 500L < System.currentTimeMillis()) {
                    displayTooltip(mouseX, mouseY, slider.tooltipKey);
                }
            } else {
                lastStillTime = 0;
            }
            break;
        }
        lastMouseX = mouseX;
        lastMouseY = mouseY;
    }

    private void displayTooltip(int x, int y, String langKey) {
        List<String> lines = this.fontRendererObj.listFormattedStringToWidth(I18n.format(langKey), this.width / 2);
        this.drawHoveringText(lines, x, y, this.fontRendererObj);
    }

    @Override
    protected void actionPerformed(GuiButton guiButton) {
        if (guiButton.enabled) {
            if (guiButton instanceof IrisButton irisButton) {
                irisButton.onPress();
            }
        }
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) {
        if (this.searchBox.isFocused()) {
            if (keyCode == Keyboard.KEY_ESCAPE) {
                this.searchBox.setFocused(false);
            }
            this.searchBox.textboxKeyTyped(typedChar, keyCode);
            this.displayedFonts = filterFonts(this.searchBox.getText().toLowerCase());
        }
        if (keyCode == Keyboard.KEY_ESCAPE) {
            this.onClose();
        }
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int mouseButton) {
        super.mouseClicked(mouseX, mouseY, mouseButton);
        this.searchBox.mouseClicked(mouseX, mouseY, mouseButton);
        this.fontList.mouseClicked(mouseX, mouseY, mouseButton);
    }

    @Override
    public void updateScreen() {
        this.searchBox.updateCursorCounter();
    }

    // this could be more efficient, but it's good enough as is
    private ArrayList<Font> filterFonts(String search) {
        ArrayList<Font> results;
        if (search == null || search.isEmpty()) {
            results = new ArrayList<>(Arrays.asList(availableFonts));
        } else {
            results = Arrays.stream(availableFonts).filter((font -> font.getFontName().toLowerCase().contains(search))).collect(Collectors.toCollection(ArrayList::new));
        }

        selectedPrimaryFontListPos = -1;
        selectedFallbackFontListPos = -1;
        for (int i = 0; i < results.size(); i++) {
            if (Objects.equals(currentPrimaryFontName, results.get(i).getFontName())) {
                selectedPrimaryFontListPos = i;
            }
            if (Objects.equals(currentFallbackFontName, results.get(i).getFontName())) {
                selectedFallbackFontListPos = i;
            }
        }
        return results;
    }

    class FontList extends GuiSlot {

        public FontList() {
            super(FontConfigScreen.this.mc, FontConfigScreen.this.width, FontConfigScreen.this.height, 52, FontConfigScreen.this.height - 100, 18);
        }

        private void mouseClicked(int mouseX, int mouseY, int mouseButton) {
            if (mouseY < this.top || mouseY > this.bottom) { return; }

            int listLeftBound = this.width / 2 - this.getListWidth() / 2;
            int listRightBound = this.width / 2 + this.getListWidth() / 2;
            if (mouseX < listLeftBound || mouseX > listRightBound) { return; }

            int mousePosInList = mouseY - this.top - this.headerPadding + (int)this.amountScrolled - 4;
            int slotIndex = mousePosInList / this.slotHeight;
            if (slotIndex < 0 || slotIndex >= this.getSize()) { return; }

            if (mouseButton == 0) {
                onElemClicked(slotIndex, false);
            } else if (mouseButton == 1) {
                onElemClicked(slotIndex, true);
            }
        }

        protected void onElemClicked(int index, boolean rightClick) {
            if (!rightClick) {
                selectedPrimaryFontListPos = index;
                currentPrimaryFontName = displayedFonts.get(index).getFontName();
            } else {
                selectedFallbackFontListPos = index;
                currentFallbackFontName = displayedFonts.get(index).getFontName();
            }
            applyChanges(false);
        }

        protected boolean isSelected(int index) {
            return (index == selectedPrimaryFontListPos || index == selectedFallbackFontListPos);
        }

        protected int getSize() {
            return displayedFonts.size();
        }

        @Override
        protected int getContentHeight() {
            return this.getSize() * 18;
        }

        @Override
        protected int getScrollBarX() {
            return this.width * 11 / 12 - 5;
        }

        @Override
        public int getListWidth() {
            return this.width * 2 / 3;
        }

        // see onElemClicked for the proper version that supports right-clicking
        protected void elementClicked(int index, boolean doubleClicked, int mouseX, int mouseY) {}

        protected void drawBackground() {
            drawDefaultBackground();
        }

        protected void drawSlot(int index, int x, int y, int p_148126_4_, Tessellator tessellator, int p_148126_6_, int p_148126_7_) {
            int color = 0xffffff;
            if (index == selectedPrimaryFontListPos) {
                color &= 0xffff55;
            }
            if (index == selectedFallbackFontListPos) {
                color &= 0x55ffff;
            }
            drawCenteredString(fontRendererObj, displayedFonts.get(index).getFontName(), this.width / 2, y + 1, color);
        }
    }
}
