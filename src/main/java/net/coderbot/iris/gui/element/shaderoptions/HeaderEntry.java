package net.coderbot.iris.gui.element.shaderoptions;

import net.coderbot.iris.Iris;
import net.coderbot.iris.gui.GuiUtil;
import net.coderbot.iris.gui.NavigationController;
import net.coderbot.iris.gui.element.IrisElementRow;
import net.coderbot.iris.gui.screen.ShaderPackScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.resources.I18n;
import net.minecraft.util.EnumChatFormatting;
import org.jetbrains.annotations.Nullable;

public class HeaderEntry extends BaseEntry {

    public static final String BACK_BUTTON_TEXT = EnumChatFormatting.ITALIC + "< " + I18n.format("options.iris.back");
    public static final String RESET_BUTTON_TEXT_INACTIVE = EnumChatFormatting.GRAY + I18n.format("options.iris.reset");
    public static final String RESET_BUTTON_TEXT_ACTIVE = EnumChatFormatting.YELLOW + I18n.format("options.iris.reset");

    public static final String RESET_HOLD_SHIFT_TOOLTIP = EnumChatFormatting.BOLD + I18n.format("options.iris.reset.tooltip.holdShift");
    public static final String RESET_TOOLTIP = EnumChatFormatting.RED + I18n.format("options.iris.reset.tooltip");
    public static final String IMPORT_TOOLTIP = I18n.format("options.iris.importSettings.tooltip");
    //				.withStyle(style -> style.withColor(TextColor.fromRgb(0x4da6ff)));
    public static final String EXPORT_TOOLTIP = I18n.format("options.iris.exportSettings.tooltip");
    //				.withStyle(style -> style.withColor(TextColor.fromRgb(0xfc7d3d)));

    private static final int MIN_SIDE_BUTTON_WIDTH = 42;
    private static final int BUTTON_HEIGHT = 16;

    private final ShaderPackScreen screen;
    private final @Nullable IrisElementRow backButton;
    private final IrisElementRow utilityButtons = new IrisElementRow();
    private final IrisElementRow.TextButtonElement resetButton;
    private final IrisElementRow.IconButtonElement importButton;
    private final IrisElementRow.IconButtonElement exportButton;
    private final String text;

    public HeaderEntry(ShaderPackScreen screen, NavigationController navigation, String text, boolean hasBackButton) {
        super(navigation);

        if (hasBackButton) {
            this.backButton = new IrisElementRow().add(new IrisElementRow.TextButtonElement(BACK_BUTTON_TEXT, this::backButtonClicked),
                Math.max(MIN_SIDE_BUTTON_WIDTH, Minecraft.getMinecraft().fontRenderer.getStringWidth(BACK_BUTTON_TEXT) + 8));
        } else {
            this.backButton = null;
        }

        this.resetButton = new IrisElementRow.TextButtonElement(RESET_BUTTON_TEXT_INACTIVE, this::resetButtonClicked);
        this.importButton = new IrisElementRow.IconButtonElement(GuiUtil.Icon.IMPORT, GuiUtil.Icon.IMPORT_COLORED, this::importSettingsButtonClicked);
        this.exportButton = new IrisElementRow.IconButtonElement(GuiUtil.Icon.EXPORT, GuiUtil.Icon.EXPORT_COLORED, this::exportSettingsButtonClicked);

        this.utilityButtons.add(this.importButton, 15).add(this.exportButton, 15)
            .add(this.resetButton, Math.max(MIN_SIDE_BUTTON_WIDTH, Minecraft.getMinecraft().fontRenderer.getStringWidth((RESET_BUTTON_TEXT_INACTIVE) + 8)));

        this.screen = screen;
        this.text = text;
    }

    public void drawEntry(ShaderPackScreen screen, int index, int x, int y, int slotWidth, int slotHeight, Tessellator tessellator, int mouseX, int mouseY, boolean isMouseOver) {
        // Draw dividing line
        //			fill(x - 3, (y + slotHeight) - 2, x + slotWidth, (y + slotHeight) - 1, 0x66BEBEBE);
        final int tickDelta = 0;
        final FontRenderer font = Minecraft.getMinecraft().fontRenderer;

        // Draw header text
        this.screen.drawCenteredString(font, text, x + (int) (slotWidth * 0.5), y + 5, 0xFFFFFF);

        GuiUtil.bindIrisWidgetsTexture();

        // Draw back button if present
        if (this.backButton != null) {
            backButton.drawScreen(x, y, BUTTON_HEIGHT, mouseX, mouseY, tickDelta, isMouseOver);
        }

        final boolean shiftDown = GuiScreen.isShiftKeyDown();

        // Set the appearance of the reset button
        this.resetButton.disabled = !shiftDown;
        this.resetButton.text = shiftDown ? RESET_BUTTON_TEXT_ACTIVE : RESET_BUTTON_TEXT_INACTIVE;

        // Draw the utility buttons
        this.utilityButtons.renderRightAligned((x + slotWidth) - 3, y, BUTTON_HEIGHT, mouseX, mouseY, tickDelta, isMouseOver);

        // Draw the reset button's tooltip
        if (this.resetButton.isHovered()) {
            final String tooltip = shiftDown ? RESET_TOOLTIP : RESET_HOLD_SHIFT_TOOLTIP;
            queueBottomRightAnchoredTooltip(mouseX, mouseY, font, tooltip);
        }
        // Draw the import/export button tooltips
        if (this.importButton.isHovered()) {
            queueBottomRightAnchoredTooltip(mouseX, mouseY, font, IMPORT_TOOLTIP);
        }
        if (this.exportButton.isHovered()) {
            queueBottomRightAnchoredTooltip(mouseX, mouseY, font, EXPORT_TOOLTIP);
        }
    }

    private void queueBottomRightAnchoredTooltip(int x, int y, FontRenderer font, String text) {
        ShaderPackScreen.TOP_LAYER_RENDER_QUEUE.add(() -> GuiUtil.drawTextPanel(font, text, x - (font.getStringWidth(text) + 10), y - 16));
    }

    public boolean mouseClicked(int mouseX, int mouseY, int button) {
        final boolean backButtonResult = backButton != null && backButton.mouseClicked(mouseX, mouseY, button);
        final boolean utilButtonResult = utilityButtons.mouseClicked(mouseX, mouseY, button);

        return backButtonResult || utilButtonResult;
    }

    private boolean backButtonClicked(IrisElementRow.TextButtonElement button) {
        this.navigation.back();
        GuiUtil.playButtonClickSound();

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
