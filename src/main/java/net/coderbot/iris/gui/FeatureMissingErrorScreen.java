package net.coderbot.iris.gui;


import net.minecraft.client.gui.GuiScreen;

public class FeatureMissingErrorScreen extends GuiScreen {
	private final GuiScreen parent;
	private MultiLineLabel message;
	private final FormattedText messageTemp;

	public FeatureMissingErrorScreen(GuiScreen parent, Component title, Component message) {
		super(title);
		this.parent = parent;
		this.messageTemp = message;
	}

	@Override
	protected void init() {
		super.init();
		this.message = MultiLineLabel.create(this.font, messageTemp, this.width - 50);
		this.addButton(new Button(this.width / 2 - 100, 140, 200, 20, CommonComponents.GUI_BACK, arg -> this.minecraft.setScreen(parent)));
	}

	@Override
	public void render(PoseStack poseStack, int mouseX, int mouseY, float delta) {
		this.renderBackground(poseStack);
		ErrorScreen.drawCenteredString(poseStack, this.font, this.title, this.width / 2, 90, 0xFFFFFF);
		message.renderCentered(poseStack, this.width / 2, 110, 9, 0xFFFFFF);
		super.render(poseStack, mouseX, mouseY, delta);
	}
}
