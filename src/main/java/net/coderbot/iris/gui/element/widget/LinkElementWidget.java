package net.coderbot.iris.gui.element.widget;

import net.coderbot.iris.gui.GuiUtil;
import net.coderbot.iris.gui.NavigationController;
import net.coderbot.iris.gui.screen.ShaderPackScreen;
import net.coderbot.iris.shaderpack.option.menu.OptionMenuLinkElement;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.resources.I18n;

import java.util.Optional;

public class LinkElementWidget extends CommentedElementWidget<OptionMenuLinkElement> {
	private static final String ARROW = new String(">");

	private final String targetScreenId;
	private final String label;

	private NavigationController navigation;
	private String trimmedLabel = null;
	private boolean isLabelTrimmed = false;

	public LinkElementWidget(OptionMenuLinkElement element) {
		super(element);

		this.targetScreenId = element.targetScreenId;
		this.label = GuiUtil.translateOrDefault(new String(element.targetScreenId), "screen." + element.targetScreenId);
	}

	@Override
	public void init(ShaderPackScreen screen, NavigationController navigation) {
		this.navigation = navigation;
	}

	@Override
	public void drawScreen(int x, int y, int width, int height, int mouseX, int mouseY, float tickDelta, boolean hovered) {
		GuiUtil.bindIrisWidgetsTexture();
		GuiUtil.drawButton(x, y, width, height, hovered, false);

		final FontRenderer font = Minecraft.getMinecraft().fontRenderer;

        final int maxLabelWidth = width - 9;

		if (font.getStringWidth(this.label) > maxLabelWidth) {
			this.isLabelTrimmed = true;
		}

		if (this.trimmedLabel == null) {
			this.trimmedLabel = GuiUtil.shortenText(font, this.label, maxLabelWidth);
		}

		int labelWidth = font.getStringWidth(this.trimmedLabel);

		font.drawStringWithShadow(this.trimmedLabel, x + (int)(width * 0.5) - (int)(labelWidth * 0.5) - (int)(0.5 * Math.max(labelWidth - (width - 18), 0)), y + 7, 0xFFFFFF);
		font.drawString(ARROW, (x + width) - 9, y + 7, 0xFFFFFF);

		if (hovered && this.isLabelTrimmed) {
			// To prevent other elements from being drawn on top of the tooltip
			ShaderPackScreen.TOP_LAYER_RENDER_QUEUE.add(() -> GuiUtil.drawTextPanel(font, this.label, mouseX + 2, mouseY - 16));
		}
	}

	@Override
	public boolean mouseClicked(double mx, double my, int button) {
		if (button == 0) {
			this.navigation.open(targetScreenId);
			GuiUtil.playButtonClickSound();

			return true;
		}
		return super.mouseClicked(mx, my, button);
	}

	@Override
	public Optional<String> getCommentTitle() {
		return Optional.of(this.label);
	}

	@Override
	public Optional<String> getCommentBody() {
		final String translation = "screen." + this.targetScreenId + ".comment";
        final String translated = I18n.format(translation);
		return Optional.ofNullable(!translated.equals(translation) ? I18n.format(translation) : null);
	}
}
