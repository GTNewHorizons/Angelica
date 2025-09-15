package com.gtnewhorizons.angelica.client.gui;

import com.gtnewhorizon.gtnhlib.config.ConfigException;
import com.gtnewhorizon.gtnhlib.config.ConfigurationManager;
import com.gtnewhorizons.angelica.client.font.FontProviderCustom;
import com.gtnewhorizons.angelica.config.FontConfig;
import net.coderbot.iris.gui.element.widget.IrisButton;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiSlot;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.resources.I18n;

import java.awt.Font;
import java.awt.GraphicsEnvironment;
import java.util.Objects;

public class FontConfigScreen extends GuiScreen {

    private final GuiScreen parent;
    private final String title;
    private FontList fontList;
    private int selectedFontListPos = -1;
    private static Font[] availableFonts;

    public FontConfigScreen(GuiScreen parent) {
        this.title = I18n.format("options.angelica.fontconfig.title");
        this.parent = parent;
        availableFonts = GraphicsEnvironment.getLocalGraphicsEnvironment().getAllFonts();
        for (int i = 0; i < availableFonts.length; i++) {
            if (Objects.equals(FontConfig.customFontName, availableFonts[i].getFontName())) {
                selectedFontListPos = i;
                break;
            }
        }
    }
    SliderClone.Option optFontQuality = new SliderClone.Option(12, 72, 12);
    SliderClone.Option optShadowOffset = new SliderClone.Option(0, 2, 0.05f);
    SliderClone.Option optGlyphScaleY = new SliderClone.Option(0.1f, 3, 0.05f);
    SliderClone.Option optGlyphScaleX = new SliderClone.Option(0.1f, 3, 0.05f);
    SliderClone.Option optWhitespaceScale = new SliderClone.Option(0.1f, 3, 0.05f);
    SliderClone.Option optGlyphSpacing = new SliderClone.Option(0.1f, 3, 0.05f);
    SliderClone.Option optFontAAMode = new SliderClone.Option(0, 2, 1);
    SliderClone.Option optFontAAStrength = new SliderClone.Option(1, 12, 1);

    public void initGui() {
        fontList = new FontList();
        initButtons();
    }

    private void initButtons() {
        this.buttonList.add(new IrisButton(this.width / 2 - 81 - 165, this.height - 20 - 7, 162, 20,
            FontConfig.enableCustomFont ? I18n.format("options.angelica.fontconfig.disable_custom_font") :
                I18n.format("options.angelica.fontconfig.enable_custom_font"), this::toggleCustomFont));
        this.buttonList.add(new IrisButton(this.width / 2 - 80, this.height - 20 - 7, 160, 20,
            I18n.format("options.angelica.fontconfig.reset_values"), this::resetValues));
        this.buttonList.add(new IrisButton(this.width / 2 - 81 + 165, this.height - 20 - 7, 162, 20,
            I18n.format("gui.done"), button -> this.onClose()));
        this.buttonList.add(new SliderClone( this.width / 2 - 60 - 186, this.height - 40 - 11, 120, 20,
            optFontQuality, FontConfig.customFontQuality, this::setFontQuality, value -> I18n.format("options.angelica.fontconfig.font_quality", String.format("%2.0f", value))));
        this.buttonList.add(new SliderClone( this.width / 2 - 60 - 186, this.height - 60 - 15, 120, 20,
            optShadowOffset, FontConfig.fontShadowOffset, value -> FontConfig.fontShadowOffset = value, value -> I18n.format("options.angelica.fontconfig.shadow_offset", String.format("%3.2f", value))));
        this.buttonList.add(new SliderClone( this.width / 2 - 60 - 62, this.height - 40 - 11, 120, 20,
            optGlyphScaleY, FontConfig.glyphScaleY, value -> FontConfig.glyphScaleY = value, value -> I18n.format("options.angelica.fontconfig.glyph_scale_y", String.format("%3.2f", value))));
        this.buttonList.add(new SliderClone( this.width / 2 - 60 - 62, this.height - 60 - 15, 120, 20,
            optGlyphScaleX, FontConfig.glyphScaleX, value -> FontConfig.glyphScaleX = value, value -> I18n.format("options.angelica.fontconfig.glyph_scale_x", String.format("%3.2f", value))));
        this.buttonList.add(new SliderClone( this.width / 2 - 60 + 62, this.height - 40 - 11, 120, 20,
            optWhitespaceScale, FontConfig.whitespaceScale, value -> FontConfig.whitespaceScale = value, value -> I18n.format("options.angelica.fontconfig.whitespace_scale", String.format("%3.2f", value))));
        this.buttonList.add(new SliderClone( this.width / 2 - 60 + 62, this.height - 60 - 15, 120, 20,
            optGlyphSpacing, FontConfig.glyphSpacing, value -> FontConfig.glyphSpacing = value, value -> I18n.format("options.angelica.fontconfig.glyph_spacing", String.format("%3.2f", value))));
        this.buttonList.add(new SliderClone( this.width / 2 - 60 + 186, this.height - 40 - 11, 120, 20,
            optFontAAStrength, FontConfig.fontAAStrength, value -> FontConfig.fontAAStrength = value.intValue(), value -> I18n.format("options.angelica.fontconfig.font_aa_strength", String.format("%.0f", value))));
        this.buttonList.add(new SliderClone( this.width / 2 - 60 + 186, this.height - 60 - 15, 120, 20,
            optFontAAMode, FontConfig.fontAAMode, value -> FontConfig.fontAAMode = value.intValue(), this::fontAAModeFormat));
    }

    private void onClose() {
        this.mc.displayGuiScreen(parent);
        applyChanges(true);
    }

    private void applyChanges(boolean finalApply) {
        if (selectedFontListPos < 0) { return; }
        if (FontConfig.enableCustomFont) {
            FontProviderCustom.get().reloadFont(selectedFontListPos, finalApply);
            FontConfig.customFontName = availableFonts[selectedFontListPos].getFontName();
        }
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
        super.buttonList.clear();
        this.initButtons();
    }

    private void toggleCustomFont(IrisButton button) {
        FontConfig.enableCustomFont = !FontConfig.enableCustomFont;
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
            case 2 -> I18n.format("options.angelica.fontconfig.aa_16x");
            case 1 -> I18n.format("options.angelica.fontconfig.aa_4x");
            default -> I18n.format("options.angelica.fontconfig.aa_none");
        };
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float delta) {
        super.drawDefaultBackground();
        fontList.drawScreen(mouseX, mouseY, delta);
        drawCenteredString(this.fontRendererObj, this.title, (int) (this.width * 0.5), 8, 0xFFFFFF);
        super.drawScreen(mouseX, mouseY, delta);
    }

    @Override
    protected void actionPerformed(GuiButton guiButton) {
        if (guiButton.enabled) {
            if (guiButton instanceof IrisButton irisButton) {
                irisButton.onPress();
            }
        }
    }

    class FontList extends GuiSlot {

        public FontList() {
            super(FontConfigScreen.this.mc, FontConfigScreen.this.width, FontConfigScreen.this.height, 32, FontConfigScreen.this.height - 75 - 4, 18);
        }

        protected int getSize() {
            return availableFonts.length;
        }

        /**
         * The element in the slot that was clicked, boolean for whether it was double clicked or not
         */
        protected void elementClicked(int p_148144_1_, boolean p_148144_2_, int p_148144_3_, int p_148144_4_) {
            selectedFontListPos = p_148144_1_;
            applyChanges(false);
        }

        /**
         * Returns true if the element passed in is currently selected
         */
        protected boolean isSelected(int p_148131_1_) {
            return (p_148131_1_ == selectedFontListPos);
        }

        /**
         * Return the height of the content being scrolled
         */
        protected int getContentHeight() {
            return this.getSize() * 18;
        }

        protected void drawBackground() {
            drawDefaultBackground();
        }

        protected void drawSlot(int p_148126_1_, int p_148126_2_, int p_148126_3_, int p_148126_4_, Tessellator p_148126_5_, int p_148126_6_, int p_148126_7_) {
            drawCenteredString(fontRendererObj, availableFonts[p_148126_1_].getFontName(), this.width / 2, p_148126_3_ + 1, 16777215);
        }
    }
}
