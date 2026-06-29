package net.coderbot.iris.gui.element.shaderoptions;

import com.gtnewhorizons.angelica.glsm.GLStateManager;
import net.coderbot.iris.Iris;
import net.coderbot.iris.gui.GuiUtil;
import net.coderbot.iris.gui.NavigationController;
import net.coderbot.iris.gui.element.IrisElementRow;
import net.coderbot.iris.gui.element.ShaderPackOptionList;
import net.coderbot.iris.gui.screen.ShaderPackScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.resources.I18n;
import net.minecraft.util.EnumChatFormatting;
import org.jetbrains.annotations.Nullable;

public class HeaderEntry extends BaseEntry {

    private static String backButtonText()        { return EnumChatFormatting.ITALIC + "< " + I18n.format("options.iris.back"); }
    private static String resetButtonInactive()   { return EnumChatFormatting.GRAY   + I18n.format("options.iris.reset"); }
    private static String resetButtonActive()     { return EnumChatFormatting.YELLOW + I18n.format("options.iris.reset"); }
    private static String resetHoldShiftTooltip() { return EnumChatFormatting.BOLD   + I18n.format("options.iris.reset.tooltip.holdShift"); }
    private static String resetTooltip()          { return EnumChatFormatting.RED    + I18n.format("options.iris.reset.tooltip"); }
    private static String importTooltip()         { return I18n.format("options.iris.importSettings.tooltip"); }
    private static String exportTooltip()         { return I18n.format("options.iris.exportSettings.tooltip"); }
    private static String searchButtonText()      { return I18n.format("options.iris.search.button"); }
    private static String clearButtonText()       { return I18n.format("options.iris.clear.button"); }
    private static String searchTooltip()         { return I18n.format("options.iris.search.tooltip"); }
    private static String clearTooltip()          { return I18n.format("options.iris.clear.tooltip"); }

    private static final int MIN_SIDE_BUTTON_WIDTH = 42;
    private static final int BUTTON_HEIGHT = 16;
    private static final int SEARCH_BOX_GAP = 3;

    private final ShaderPackScreen screen;
    private final ShaderPackOptionList optionList;
    private final @Nullable IrisElementRow backButton;
    /** Width of the left-side button, used to clip the title text away from it. */
    private final int leftButtonWidth;
    private final IrisElementRow utilityButtons = new IrisElementRow();
    private final IrisElementRow.TextButtonElement resetButton;
    private final IrisElementRow.IconButtonElement importButton;
    private final IrisElementRow.IconButtonElement exportButton;
    private final String text;

    /** Non-null only on the main-screen header (search/clear toggle). */
    private final @Nullable IrisElementRow.TextButtonElement searchToggleButton;

    public HeaderEntry(ShaderPackScreen screen, NavigationController navigation, String text, boolean hasBackButton, ShaderPackOptionList optionList) {
        super(navigation);

        this.optionList = optionList;
        this.screen = screen;
        this.text = text;

        IrisElementRow.TextButtonElement toggleRef = null;

        if (hasBackButton) {
            // Sub-screen: navigating here while search is active → force-disable so the search box disappears
            if (optionList.isSearchModeActive()) {
                optionList.disableSearchMode();
            }
            String backText = backButtonText();
            int w = Math.max(MIN_SIDE_BUTTON_WIDTH, Minecraft.getMinecraft().fontRenderer.getStringWidth(backText) + 8);
            this.backButton = new IrisElementRow().add(new IrisElementRow.TextButtonElement(backText, this::backButtonClicked), w);
            this.leftButtonWidth = w;
        } else {
            // Main screen: left button is the search/clear toggle
            String buttonText = optionList.isSearchModeActive() ? clearButtonText() : searchButtonText();
            IrisElementRow.TextButtonElement searchBtn = new IrisElementRow.TextButtonElement(buttonText, this::searchButtonClicked);
            int w = Math.max(MIN_SIDE_BUTTON_WIDTH, Minecraft.getMinecraft().fontRenderer.getStringWidth(buttonText) + 8);
            this.backButton = new IrisElementRow().add(searchBtn, w);
            toggleRef = searchBtn;
            this.leftButtonWidth = w;
            optionList.setReservedLeftWidth(w + SEARCH_BOX_GAP);
        }

        this.searchToggleButton = toggleRef;

        this.resetButton = new IrisElementRow.TextButtonElement(resetButtonInactive(), this::resetButtonClicked);
        this.importButton = new IrisElementRow.IconButtonElement(GuiUtil.Icon.IMPORT, GuiUtil.Icon.IMPORT_COLORED, this::importSettingsButtonClicked);
        this.exportButton = new IrisElementRow.IconButtonElement(GuiUtil.Icon.EXPORT, GuiUtil.Icon.EXPORT_COLORED, this::exportSettingsButtonClicked);

        this.utilityButtons.add(this.importButton, 15).add(this.exportButton, 15)
            .add(this.resetButton, Math.max(MIN_SIDE_BUTTON_WIDTH, Minecraft.getMinecraft().fontRenderer.getStringWidth(resetButtonInactive()) + 8));
    }

    @Override
    public void drawEntry(ShaderPackScreen screen, int index, int x, int y, int slotWidth, int slotHeight, Tessellator tessellator, int mouseX, int mouseY, boolean isMouseOver) {
        Gui.drawRect(x - 3, (y + slotHeight) - 3, x + slotWidth, (y + slotHeight) - 2, 0x66BEBEBE);
        // Gui.drawRect leaves GL color set to the rect's color; reset to white so button textures aren't tinted.
        GLStateManager.glColor4f(1.0f, 1.0f, 1.0f, 1.0f);
        final int tickDelta = 0;
        final FontRenderer font = Minecraft.getMinecraft().fontRenderer;

        GuiUtil.bindIrisWidgetsTexture();

        // Draw back button if present
        if (this.backButton != null) {
            backButton.drawScreen(x, y, BUTTON_HEIGHT, mouseX, mouseY, tickDelta, isMouseOver);
        }

        if (optionList.isSearchModeActive() && searchToggleButton != null) {
            if (searchToggleButton.isHovered()) {
                ShaderPackScreen.TOP_LAYER_RENDER_QUEUE.add(() -> GuiUtil.drawTextPanel(font, clearTooltip(), mouseX, mouseY - 16));
            }
            return;
        }

        GuiUtil.drawScrollingText(font, text, x + (int) (slotWidth * 0.5),
            x + leftButtonWidth + 5,
            ((x + slotWidth) - 10) - this.utilityButtons.getWidth(),
            y + 5, y + 15, 0xFFFFFF);

        final boolean shiftDown = GuiScreen.isShiftKeyDown();

        // Set the appearance of the reset button
        this.resetButton.disabled = !shiftDown;
        this.resetButton.text = shiftDown ? resetButtonActive() : resetButtonInactive();

        // Draw the utility buttons
        this.utilityButtons.renderRightAligned((x + slotWidth) - 3, y, BUTTON_HEIGHT, mouseX, mouseY, tickDelta, isMouseOver);

        // Draw the reset button's tooltip
        if (this.resetButton.isHovered()) {
            final String tooltip = shiftDown ? resetTooltip() : resetHoldShiftTooltip();
            queueBottomRightAnchoredTooltip(mouseX, mouseY, font, tooltip);
        }
        // Draw the import/export button tooltips
        if (this.importButton.isHovered()) {
            queueBottomRightAnchoredTooltip(mouseX, mouseY, font, importTooltip());
        }
        if (this.exportButton.isHovered()) {
            queueBottomRightAnchoredTooltip(mouseX, mouseY, font, exportTooltip());
        }
        // Search-button tooltip
        if (searchToggleButton != null && searchToggleButton.isHovered()) {
            ShaderPackScreen.TOP_LAYER_RENDER_QUEUE.add(() -> GuiUtil.drawTextPanel(font, searchTooltip(), mouseX, mouseY - 16));
        }
    }

    private void queueBottomRightAnchoredTooltip(int x, int y, FontRenderer font, String text) {
        ShaderPackScreen.TOP_LAYER_RENDER_QUEUE.add(() -> GuiUtil.drawTextPanel(font, text, x - (font.getStringWidth(text) + 10), y - 16));
    }

    @Override
    public boolean mouseClicked(int mouseX, int mouseY, int button) {
        if (optionList.isSearchModeActive()) {
            return backButton != null && backButton.mouseClicked(mouseX, mouseY, button);
        }
        final boolean backButtonResult = backButton != null && backButton.mouseClicked(mouseX, mouseY, button);
        final boolean utilButtonResult = utilityButtons.mouseClicked(mouseX, mouseY, button);

        return backButtonResult || utilButtonResult;
    }

    @Override
    public boolean mouseReleased(int mouseX, int mouseY, int button) {
        return false;
    }

    private boolean backButtonClicked(IrisElementRow.TextButtonElement button) {
        this.navigation.back();
        GuiUtil.playButtonClickSound();

        return true;
    }

    private boolean searchButtonClicked(IrisElementRow.TextButtonElement button) {
        GuiUtil.playButtonClickSound();
        if (optionList.isSearchModeActive()) {
            optionList.disableSearchModeAndRebuild();
        } else {
            optionList.enableSearchModeAndRebuild();
        }
        return true;
    }

    private boolean resetButtonClicked(IrisElementRow.TextButtonElement button) {
        if (GuiScreen.isShiftKeyDown()) {
            Iris.resetShaderPackOptionsOnNextReload();
            this.screen.applyChanges();
            GuiUtil.playButtonClickSound();

            return true;
        }

        return false;
    }

    private boolean importSettingsButtonClicked(IrisElementRow.IconButtonElement button) {
        //			GuiUtil.playButtonClickSound();
        //
        //			// Invalid state to be in
        //			if (!Iris.getCurrentPack().isPresent()) {
        //				return false;
        //			}
        //
        //			// Displaying a dialog when the game is full-screened can cause severe issues
        //			// https://github.com/IrisShaders/Iris/issues/1258
        //			if (Minecraft.getMinecraft().isFullScreen()) {
        //				this.screen.displayNotification(
        //					"" + EnumChatFormatting.RED + EnumChatFormatting.BOLD + I18n.format("options.iris.mustDisableFullscreen");
        //				return false;
        //			}
        //
        //			final ShaderPackScreen originalScreen = this.screen; // Also used to prevent invalid state
        //
        //			FileDialogUtil.fileSelectDialog(
        //					FileDialogUtil.DialogType.OPEN, "Import Shader Settings from File",
        //					Iris.getShaderpacksDirectory().resolve(Iris.getCurrentPackName() + ".txt"),
        //					 "Shader Pack Settings (.txt)", "*.txt")
        //			.whenComplete((path, err) -> {
        //				if (err != null) {
        //					Iris.logger.error("Error selecting shader settings from file", err);
        //
        //					return;
        //				}
        //
        //				if (Minecraft.getInstance().screen == originalScreen) {
        //					path.ifPresent(originalScreen::importPackOptions);
        //				}
        //			});
        //
        return true;
    }

    private boolean exportSettingsButtonClicked(IrisElementRow.IconButtonElement button) {
        //			GuiUtil.playButtonClickSound();
        //
        //			// Invalid state to be in
        //			if (!Iris.getCurrentPack().isPresent()) {
        //				return false;
        //			}
        //
        //			// Displaying a dialog when the game is full-screened can cause severe issues
        //			// https://github.com/IrisShaders/Iris/issues/1258
        //			if (Minecraft.getInstance().getWindow().isFullscreen()) {
        //				this.screen.displayNotification(
        //					I18n.format("options.iris.mustDisableFullscreen")
        //						.withStyle(ChatFormatting.RED).withStyle(ChatFormatting.BOLD));
        //				return false;
        //			}
        //
        //			FileDialogUtil.fileSelectDialog(
        //					FileDialogUtil.DialogType.SAVE, "Export Shader Settings to File",
        //					Iris.getShaderpacksDirectory().resolve(Iris.getCurrentPackName() + ".txt"),
        //					"Shader Pack Settings (.txt)", "*.txt")
        //			.whenComplete((path, err) -> {
        //				if (err != null) {
        //					Iris.logger.error("Error selecting file to export shader settings", err);
        //
        //					return;
        //				}
        //
        //				path.ifPresent(p -> {
        //					Properties toSave = new Properties();
        //
        //					// Dirty way of getting the currently applied settings as a Properties, directly
        //					// opens and copies out of the saved settings file if it is present
        //					Path sourceTxtPath = Iris.getShaderpacksDirectory().resolve(Iris.getCurrentPackName() + ".txt");
        //					if (Files.exists(sourceTxtPath)) {
        //						try (InputStream in = Files.newInputStream(sourceTxtPath)) {
        //							toSave.load(in);
        //						} catch (IOException ignored) {}
        //					}
        //
        //					// Save properties to user determined file
        //					try (OutputStream out = Files.newOutputStream(p)) {
        //						toSave.store(out, null);
        //					} catch (IOException e) {
        //						Iris.logger.error("Error saving properties to \"" + p + "\"", e);
        //					}
        //				});
        //			});

        return true;
    }
}
